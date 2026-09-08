#!/usr/bin/env bash
#
# SPDX-License-Identifier: Apache-2.0
# Copyright: Red Hat Inc. and Hibernate Authors
#
# Shared engine for the hibernate-accessor benchmark suites.
#
# This file is meant to be *sourced* by ../run-benchmarks.sh (the entrypoint) before it
# hands off to a per-suite driver (suite-basic.sh / suite-model.sh). It carries everything
# the two suites have in common: platform/CPU-core detection + taskset pinning, the
# async-profiler download/verify/flamegraph machinery (ported from hibernate-validator's
# run-benchmarks.sh, including its pinned checksums), the perf_event_paranoid handling, the
# JMH-JSON comparison table, and the jar build/lookup helpers.
#
# The entrypoint must export PERF_DIR and ROOT_DIR before sourcing this file. Each suite
# driver sets SUITE_MODULE / SUITE_JAR_DIR / SUITE_JAR_BASE before calling the jar helpers.

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Constants (async-profiler, pinned + checksum-verified)
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

readonly ASYNC_PROFILER_VERSION="4.5"
readonly ASYNC_PROFILER_DOWNLOAD_DIR="${PERF_DIR}/.async-profiler"
readonly ASYNC_PROFILER_SHA256_LINUX_X64="89546fbb9ee0fc5496c7edd4099b0709489bc78b0d8057ccbb4b801f6b032b62"
readonly ASYNC_PROFILER_SHA256_LINUX_ARM64="64c41d1465d60097439c50d7e924b4946f1f62b1cbd21ce5b034fad09c0d6979"
readonly ASYNC_PROFILER_SHA256_MACOS="46d04ef81f532a065a0b3877e488aa706afa14aa2ea14433b323db9e6fda76dc"

# Written to .results by the suites; kept here so both share the one directory.
readonly RESULTS_DIR="${PERF_DIR}/.results"

# Populated by detect_platform / setup_async_profiler / detect_cores.
ARCH=""
OS=""
RESOLVED_ASYNC_PROFILER_HOME=""
ORIGINAL_PERF_EVENT_PARANOID=""
TASKSET_CORES=""

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Logging helpers
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

log()  { printf '\033[0;32m[run-benchmarks]\033[0m %s\n' "$*"; }
warn() { printf '\033[0;33m[run-benchmarks]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[0;31m[run-benchmarks]\033[0m %s\n' "$*" >&2; exit "${2:-1}"; }

is_positive_int() { [[ "$1" =~ ^[1-9][0-9]*$ ]]; }

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Jar build / lookup helpers (shared by every suite)
#
# Each suite sets three variables that place its jars:
#   SUITE_MODULE   Gradle project path, e.g. :hibernate-accessor-benchmark-basic
#   SUITE_JAR_DIR  absolute build/libs dir holding the shadow jars
#   SUITE_JAR_BASE jar base name (the module name), e.g. hibernate-accessor-benchmark-basic
# The three classpath profiles (core / asm / bytebuddy) match the benchmark-jars convention.
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# core bundles reflection/method-handle/lambda (and the whole-model GENERATED_DOUBLE_SWITCH);
# asm and bytebuddy each stand alone. Maps a JMH strategy enum name to its jar profile.
profile_for_strategy() {
	case "$1" in
		REFLECTION|METHOD_HANDLE|LAMBDA|GENERATED_DOUBLE_SWITCH) echo "core" ;;
		ASM|ASM_PER_MEMBER)                                     echo "asm" ;;
		BYTE_BUDDY|BYTE_BUDDY_PER_MEMBER)                       echo "bytebuddy" ;;
		*) die "Unknown strategy enum: $1" ;;
	esac
}

# core -> benchmarkJarCore, asm -> benchmarkJarAsm, bytebuddy -> benchmarkJarBytebuddy
gradle_task_for_profile() {
	local p="$1"
	printf '%s:benchmarkJar%s' "${SUITE_MODULE}" "$(tr '[:lower:]' '[:upper:]' <<< "${p:0:1}")${p:1}"
}

jar_for_profile() {
	printf '%s/%s-%s.jar' "${SUITE_JAR_DIR}" "${SUITE_JAR_BASE}" "$1"
}

# build_or_reuse_jar <profile> <skip_build> [label]
# Builds (or, with skip_build=true, reuses) the shadow jar for one classpath profile.
build_or_reuse_jar() {
	local profile="$1" skip="$2" label="${3:-$1}"
	local jar task
	jar="$(jar_for_profile "${profile}")"
	task="$(gradle_task_for_profile "${profile}")"
	if [[ "${skip}" == "true" ]]; then
		[[ -f "${jar}" ]] || die "Jar not found and --skip-build was given: ${jar}"
		log "Reusing existing jar: ${jar}"
	else
		log "Building ${label} benchmark jar"
		( cd "${ROOT_DIR}" && ./gradlew "${task}" --console=plain )
		[[ -f "${jar}" ]] || die "Expected jar was not produced: ${jar}"
	fi
}

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Async-profiler (ported from hibernate-validator's run-benchmarks.sh)
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

detect_platform() {
	local machine
	machine="$(uname -m)"
	case "${machine}" in
		x86_64)  ARCH="x64" ;;
		aarch64|arm64) ARCH="arm64" ;;
		*) die "Unsupported architecture: ${machine}" 2 ;;
	esac

	local kernel
	kernel="$(uname -s)"
	case "${kernel}" in
		Linux)  OS="linux" ;;
		Darwin) OS="macos" ;;
		*) die "Unsupported OS: ${kernel}" 2 ;;
	esac
}

compute_sha256() {
	local file="$1"
	if command -v sha256sum >/dev/null 2>&1; then
		sha256sum "${file}" | cut -d' ' -f1
	elif command -v shasum >/dev/null 2>&1; then
		shasum -a 256 "${file}" | cut -d' ' -f1
	else
		die "Neither sha256sum nor shasum found. Cannot verify async-profiler archive." 4
	fi
}

get_expected_sha256() {
	case "${OS}-${ARCH}" in
		linux-x64)   echo "${ASYNC_PROFILER_SHA256_LINUX_X64}" ;;
		linux-arm64) echo "${ASYNC_PROFILER_SHA256_LINUX_ARM64}" ;;
		macos-*)     echo "${ASYNC_PROFILER_SHA256_MACOS}" ;;
		*) die "No SHA-256 hash for platform ${OS}-${ARCH}" 4 ;;
	esac
}

get_async_profiler_archive_name() {
	if [[ "${OS}" == "macos" ]]; then
		echo "async-profiler-${ASYNC_PROFILER_VERSION}-macos.zip"
	else
		echo "async-profiler-${ASYNC_PROFILER_VERSION}-${OS}-${ARCH}.tar.gz"
	fi
}

get_async_profiler_extract_dir() {
	if [[ "${OS}" == "macos" ]]; then
		echo "async-profiler-${ASYNC_PROFILER_VERSION}-macos"
	else
		echo "async-profiler-${ASYNC_PROFILER_VERSION}-${OS}-${ARCH}"
	fi
}

get_profiler_lib_name() {
	if [[ "${OS}" == "macos" ]]; then
		echo "libasyncProfiler.dylib"
	else
		echo "libasyncProfiler.so"
	fi
}

validate_async_profiler_home() {
	local home="$1"
	local lib_name
	lib_name="$(get_profiler_lib_name)"
	if [[ ! -f "${home}/lib/${lib_name}" ]]; then
		die "async-profiler library not found at ${home}/lib/${lib_name}" 4
	fi
}

setup_async_profiler() {
	if [[ -n "${ASYNC_PROFILER_PATH_ARG:-}" ]]; then
		RESOLVED_ASYNC_PROFILER_HOME="${ASYNC_PROFILER_PATH_ARG}"
		log "Using async-profiler from --async-profiler-path: ${RESOLVED_ASYNC_PROFILER_HOME}"
		validate_async_profiler_home "${RESOLVED_ASYNC_PROFILER_HOME}"
		return
	fi

	if [[ -n "${ASYNC_PROFILER_HOME:-}" ]]; then
		RESOLVED_ASYNC_PROFILER_HOME="${ASYNC_PROFILER_HOME}"
		log "Using async-profiler from ASYNC_PROFILER_HOME: ${RESOLVED_ASYNC_PROFILER_HOME}"
		validate_async_profiler_home "${RESOLVED_ASYNC_PROFILER_HOME}"
		return
	fi

	local archive_name extract_dir version_file
	archive_name="$(get_async_profiler_archive_name)"
	extract_dir="$(get_async_profiler_extract_dir)"
	version_file="${ASYNC_PROFILER_DOWNLOAD_DIR}/.version"

	RESOLVED_ASYNC_PROFILER_HOME="${ASYNC_PROFILER_DOWNLOAD_DIR}/${extract_dir}"

	if [[ -f "${version_file}" ]] && [[ "$(cat "${version_file}")" == "${ASYNC_PROFILER_VERSION}" ]]; then
		log "Using cached async-profiler ${ASYNC_PROFILER_VERSION} at ${RESOLVED_ASYNC_PROFILER_HOME}"
		validate_async_profiler_home "${RESOLVED_ASYNC_PROFILER_HOME}"
		return
	fi

	log "Downloading async-profiler ${ASYNC_PROFILER_VERSION} for ${OS}-${ARCH}..."
	mkdir -p "${ASYNC_PROFILER_DOWNLOAD_DIR}"

	local download_url="https://github.com/async-profiler/async-profiler/releases/download/v${ASYNC_PROFILER_VERSION}/${archive_name}"
	local tmp_file="${ASYNC_PROFILER_DOWNLOAD_DIR}/${archive_name}"

	curl -fSL --connect-timeout 30 --retry 3 --retry-delay 2 -o "${tmp_file}" "${download_url}" \
		|| die "Failed to download async-profiler from ${download_url}" 4

	local expected_sha actual_sha
	expected_sha="$(get_expected_sha256)"
	actual_sha="$(compute_sha256 "${tmp_file}")"

	if [[ "${actual_sha}" != "${expected_sha}" ]]; then
		rm -f "${tmp_file}"
		die "SHA-256 mismatch for ${archive_name}. Expected: ${expected_sha}, Got: ${actual_sha}" 4
	fi
	log "SHA-256 verified: ${actual_sha}"

	if [[ "${OS}" == "macos" ]]; then
		unzip -qo "${tmp_file}" -d "${ASYNC_PROFILER_DOWNLOAD_DIR}"
	else
		tar xzf "${tmp_file}" -C "${ASYNC_PROFILER_DOWNLOAD_DIR}"
	fi
	rm -f "${tmp_file}"

	echo "${ASYNC_PROFILER_VERSION}" > "${version_file}"
	log "async-profiler ${ASYNC_PROFILER_VERSION} installed at ${RESOLVED_ASYNC_PROFILER_HOME}"
	validate_async_profiler_home "${RESOLVED_ASYNC_PROFILER_HOME}"
}

restore_perf_event_paranoid() {
	if [[ -n "${ORIGINAL_PERF_EVENT_PARANOID}" ]]; then
		log "Restoring perf_event_paranoid to ${ORIGINAL_PERF_EVENT_PARANOID}..."
		sudo sysctl -q kernel.perf_event_paranoid="${ORIGINAL_PERF_EVENT_PARANOID}" 2>/dev/null \
			|| warn "Failed to restore perf_event_paranoid. Restore manually: sudo sysctl kernel.perf_event_paranoid=${ORIGINAL_PERF_EVENT_PARANOID}"
	fi
}

check_perf_event_paranoid() {
	[[ "${OS}" == "linux" ]] || return 0

	local paranoid_file="/proc/sys/kernel/perf_event_paranoid"
	[[ -f "${paranoid_file}" ]] || return 0

	local val
	val="$(cat "${paranoid_file}")"
	if (( val <= 1 )); then
		return 0
	fi

	warn "perf_event_paranoid is ${val} (needs <= 1 for hardware CPU profiling)."
	echo -n "[PROMPT] Temporarily set perf_event_paranoid=1 for this run? (requires sudo) [y/N] " >&2
	local answer
	read -r answer
	case "${answer}" in
		[yY]|[yY][eE][sS])
			ORIGINAL_PERF_EVENT_PARANOID="${val}"
			trap restore_perf_event_paranoid EXIT INT TERM
			sudo sysctl -q kernel.perf_event_paranoid=1 \
				|| die "Failed to set perf_event_paranoid. Try: sudo sysctl kernel.perf_event_paranoid=1" 4
			log "perf_event_paranoid set to 1 (will restore to ${val} on exit)."
			;;
		*)
			warn "Keeping perf_event_paranoid=${val}. async-profiler will fall back to itimer mode (less accurate)."
			;;
	esac
}

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# CPU core pinning (ported from hibernate-validator's run-benchmarks.sh)
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# Resolves TASKSET_CORES. An explicit --pin (PIN_CORES) wins; otherwise, on a hybrid Linux
# CPU the fastest (P-core) group is auto-detected via base_frequency and pinned to.
# Non-interactive: it logs what it decides rather than prompting.
detect_cores() {
	if [[ -n "${PIN_CORES:-}" ]]; then
		command -v taskset >/dev/null 2>&1 || die "--pin requested but 'taskset' is not available"
		TASKSET_CORES="${PIN_CORES}"
		log "Using user-specified cores: ${TASKSET_CORES}"
		return
	fi

	if [[ "$(uname -s)" != "Linux" ]]; then
		log "CPU core pinning is only supported on Linux. Skipping."
		return
	fi

	if ! command -v taskset >/dev/null 2>&1; then
		warn "'taskset' not found. Skipping CPU core pinning."
		return
	fi

	local freq_dir="/sys/devices/system/cpu"
	if [[ ! -f "${freq_dir}/cpu0/cpufreq/base_frequency" ]]; then
		log "No base_frequency info available. Using all cores (no pinning)."
		return
	fi

	local -A freq_map=()
	local max_freq=0
	local cpu_dir cpu_id freq_file freq
	for cpu_dir in "${freq_dir}"/cpu[0-9]*; do
		cpu_id="${cpu_dir##*cpu}"
		freq_file="${cpu_dir}/cpufreq/base_frequency"
		[[ -f "${freq_file}" ]] || continue
		freq="$(<"${freq_file}")"
		freq_map["${freq}"]+="${cpu_id} "
		if (( freq > max_freq )); then
			max_freq=${freq}
		fi
	done

	if (( ${#freq_map[@]} <= 1 )); then
		log "Uniform CPU frequencies detected. Using all cores (no pinning)."
		return
	fi

	local p_cores="${freq_map[${max_freq}]}"
	TASKSET_CORES="$(echo "${p_cores}" | xargs | tr ' ' ',')"
	TASKSET_CORES="${TASKSET_CORES%,}"
	log "Hybrid CPU detected. Pinning to P-cores (${max_freq} kHz): ${TASKSET_CORES}"
}

# Populates the named array with a "taskset -c <cores>" prefix (empty when no pinning),
# so callers can build their command as: local -a cmd=(); pin_prefix cmd; cmd+=( java ... ).
pin_prefix() {
	local -n _out="$1"
	_out=()
	[[ -n "${TASKSET_CORES}" ]] && _out=( taskset -c "${TASKSET_CORES}" )
}

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Comparison summary (JMH JSON -> one table)
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# Renders the per-jar JMH JSON files in a directory as one table. Benchmarks carry
# several param axes (access, valueKind, memberCount, modelId, readMode, ...) besides
# strategy, so those are shown in a PARAMS column and rows are grouped by benchmark/mode/params
# with the strategies ranked by score within each combo. Strategies live in different
# jars, so JMH cannot emit a single file; this stitches them back together.
# print_comparison_summary <dir> [glob]   (glob defaults to *.json)
print_comparison_summary() {
	local dir="$1"
	local glob="${2:-*.json}"
	command -v jq >/dev/null 2>&1 || { warn "jq not found; skipping summary. Raw JSON in ${dir}/"; return; }

	local files=()
	# shellcheck disable=SC2206
	files=( ${dir}/${glob} )
	[[ -e "${files[0]}" ]] || { warn "No JSON files matching ${glob} in ${dir}/; skipping summary."; return; }

	local table jq_err
	jq_err="$(mktemp)"
	# `add` flattens the per-jar result arrays into one; each JMH record may or may not carry
	# a `params` object (baselines with no @Param omit it entirely), so guard with `// {}`.
	if ! table="$(jq -rs '
		def round3: if type == "number" then (. * 1000 | round) / 1000 else . end;
		[ .[] | select(type == "array") ] | add // []
		| map({
			benchmark: (.benchmark | sub("^org\\.hibernate\\.accessor\\.performance\\.";"")),
			mode: .mode,
			params: ((.params // {}) | del(.strategy) | to_entries | map("\(.key)=\(.value)") | join(",")),
			strategy: (
				if ((.params // {}) | has("strategy")) then .params.strategy
				else
					( .benchmark | split(".") | last ) as $m
					| if   ($m | test("^raw";   "i")) then "raw (base)"
					  elif ($m | test("^iface"; "i")) then "iface (base)"
					  else "(base)" end
				end
			),
			score: (.primaryMetric.score | round3),
			error: (.primaryMetric.scoreError | round3),
			unit: .primaryMetric.scoreUnit
		})
		| sort_by(.benchmark, .mode, .params, .score)
		| (["BENCHMARK","MODE","PARAMS","STRATEGY","SCORE","ERROR","UNIT"]),
		  (.[] | [ .benchmark, .mode, .params, .strategy, (.score|tostring), (.error|tostring), .unit ])
		| @tsv
	' "${files[@]}" 2>"${jq_err}")"; then
		warn "Could not build summary from JSON results (see ${dir}/). jq said:"
		while IFS= read -r line; do warn "  ${line}"; done <"${jq_err}"
		rm -f "${jq_err}"
		return
	fi
	rm -f "${jq_err}"

	log "Comparison summary (avgt/ss: lower is better; thrpt: higher is better):"
	if command -v column >/dev/null 2>&1; then
		printf '%s\n' "${table}" | column -t -s "$(printf '\t')" >&2
	else
		printf '%s\n' "${table}" >&2
	fi
}
