#!/usr/bin/env bash
#
# SPDX-License-Identifier: Apache-2.0
# Copyright: Red Hat Inc. and Hibernate Authors
#
# Basic suite driver: fixed, hand-authored micro-benchmarks (Read/Bulk/Cascade/Megamorphic/...).
#
# Sourced by ../run-benchmarks.sh after lib-bench-common.sh. Exposes run_suite(); relies on the
# shared engine for pinning, async-profiler, the comparison table and jar build/lookup.
#
# The benchmark jar is produced by Gradle (one shadow jar per classpath profile) and the specific
# strategy is selected at run time via the JMH `strategy` parameter.

# Where this suite's jars live (consumed by the shared jar helpers in lib-bench-common.sh).
SUITE_MODULE=":hibernate-accessor-benchmark-basic"
SUITE_JAR_BASE="hibernate-accessor-benchmark-basic"
SUITE_JAR_DIR="${PERF_DIR}/benchmark-basic/build/libs"

basic_usage() {
	cat <<'EOF'
Usage: run-benchmarks.sh --suite basic [options] [benchmark-pattern ...]

Builds the matching benchmark jar and runs the JMH micro-benchmarks for one strategy.

Options:
  -s, --strategy <name>     reflection | method-handle | lambda | asm | asm-per-member | bytebuddy | bytebuddy-per-member   (default: lambda)
      --all                 run every strategy (3 jars) into .results/compare-<ts>/ and
                            print a jq summary table; ignores -s / -rf / -rff
      --no-baselines        with --all, skip the dedicated baseline reference pass
  -m, --mode <mode>         JMH benchmark mode: avgt | thrpt | sample | ss | all    (default: avgt)
  -f, --forks <n>           number of forks                                         (default: 3)
  -wi, --warmup <n>         warmup iterations                                       (default: 5)
  -i, --iterations <n>      measurement iterations                                  (default: 5)
  -t, --threads <n>         measurement threads                                     (default: 1)
      --quick               fast, low-confidence settings (1 fork, 3 warmup, 5 iters)
  -rf, --result-format <f>  result format: json | csv | text | latex | scsv         (default: json)
  -rff, --result-file <p>   result file path            (default: .results/<profile>-<STRATEGY>-<ts>.<ext>)
      --pin <cores>         pin the forked JVM to CPU cores via taskset, e.g. "0-3"
                            (Linux; when omitted, P-cores are auto-detected on hybrid CPUs)
      --flamegraph          record async-profiler CPU profiles (auto-downloads + verifies v4.5)
      --async-format <f>    flamegraph | jfr | flamegraph,jfr                        (default: flamegraph,jfr)
      --async-profiler-path <dir>  use an existing async-profiler install instead of downloading
      --jvm-args <args>     extra JVM args forwarded to the forked JVM
      --skip-build          reuse the already-built jar
  -h, --help                show this help

Environment:
  ASYNC_PROFILER_HOME  path to an async-profiler install (fallback when --async-profiler-path is unset)

Benchmark patterns are JMH regexes, e.g. "ReadBenchmark", "Bulk.*bulkRead", ".*Baseline".

Examples:
  ./run-benchmarks.sh --suite basic --strategy asm --quick ReadBenchmark
  ./run-benchmarks.sh --suite basic -s lambda -m thrpt --pin 0-3 "Bulk.*"
  ./run-benchmarks.sh --suite basic --all --quick ReadBenchmark
  ./run-benchmarks.sh --suite basic -s reflection --flamegraph "ReadBenchmark"
EOF
}

run_suite() {
	# ~~~ Defaults ~~~
	local STRATEGY="lambda"
	local MODE="avgt"                # avgt | thrpt | sample | ss | all
	# Left empty during parsing so --quick defaults never clobber an explicit flag,
	# regardless of the order the two are given; concrete defaults are applied post-parse.
	local FORKS="" WARMUP_ITERATIONS="" MEASUREMENT_ITERATIONS="" THREADS=""
	local QUICK="false"
	local RESULT_FORMAT="json"       # json | csv | text | latex | scsv
	local RESULT_FILE=""
	PIN_CORES=""                     # e.g. "0-3"; empty disables taskset pinning (read by detect_cores)
	local FLAMEGRAPH="false"
	local ASYNC_FORMAT="flamegraph,jfr"
	ASYNC_PROFILER_PATH_ARG=""       # read by setup_async_profiler
	local SKIP_BUILD="false"
	local JVM_ARGS=""
	local RUN_ALL="false"
	local BASELINES="true"
	local -a BENCHMARK_PATTERNS=()

	# ~~~ Argument parsing ~~~
	while [[ $# -gt 0 ]]; do
		case "$1" in
			-s|--strategy)      STRATEGY="$2"; shift 2 ;;
			--all)              RUN_ALL="true"; shift ;;
			--no-baselines)     BASELINES="false"; shift ;;
			-m|--mode)          MODE="$2"; shift 2 ;;
			-f|--forks)         is_positive_int "${2:-}" || die "--forks requires a positive integer"; FORKS="$2"; shift 2 ;;
			-wi|--warmup)       is_positive_int "${2:-}" || die "--warmup requires a positive integer"; WARMUP_ITERATIONS="$2"; shift 2 ;;
			-i|--iterations)    is_positive_int "${2:-}" || die "--iterations requires a positive integer"; MEASUREMENT_ITERATIONS="$2"; shift 2 ;;
			-t|--threads)       is_positive_int "${2:-}" || die "--threads requires a positive integer"; THREADS="$2"; shift 2 ;;
			--quick)            QUICK="true"; shift ;;
			-rf|--result-format) RESULT_FORMAT="$2"; shift 2 ;;
			-rff|--result-file) RESULT_FILE="$2"; shift 2 ;;
			--pin)              PIN_CORES="$2"; shift 2 ;;
			--flamegraph)       FLAMEGRAPH="true"; shift ;;
			--async-format)     ASYNC_FORMAT="$2"; shift 2 ;;
			--async-profiler-path) ASYNC_PROFILER_PATH_ARG="$2"; shift 2 ;;
			--jvm-args)         JVM_ARGS="$2"; shift 2 ;;
			--skip-build)       SKIP_BUILD="true"; shift ;;
			-h|--help)          basic_usage; exit 0 ;;
			--)                 shift; while [[ $# -gt 0 ]]; do BENCHMARK_PATTERNS+=("$1"); shift; done ;;
			-*)                 die "Unknown option: $1 (see --suite basic --help)" ;;
			*)                  BENCHMARK_PATTERNS+=("$1"); shift ;;
		esac
	done

	# --quick only fills iteration counts that were not set explicitly, so an explicit
	# -f/-wi/-i wins no matter which side of --quick it appears on. Remaining unset
	# values fall back to the regular defaults.
	if [[ "${QUICK}" == "true" ]]; then
		[[ -z "${FORKS}" ]]                  && FORKS=1
		[[ -z "${WARMUP_ITERATIONS}" ]]      && WARMUP_ITERATIONS=3
		[[ -z "${MEASUREMENT_ITERATIONS}" ]] && MEASUREMENT_ITERATIONS=5
	fi
	: "${FORKS:=3}"
	: "${WARMUP_ITERATIONS:=5}"
	: "${MEASUREMENT_ITERATIONS:=5}"
	: "${THREADS:=1}"

	case "${ASYNC_FORMAT}" in
		flamegraph|jfr|flamegraph,jfr|jfr,flamegraph) ;;
		*) die "Unknown --async-format: ${ASYNC_FORMAT} (flamegraph | jfr | flamegraph,jfr)" ;;
	esac

	if [[ "${RUN_ALL}" == "true" ]]; then
		basic_run_all
		return
	fi

	basic_run_one
}

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# --all: run every strategy across its jar, then print a comparison table
#
# The 7 strategies span 3 jars (core bundles reflection/method-handle/lambda; asm and
# bytebuddy each stand alone), so this is the minimum 3 JMH processes, each writing json.
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
basic_run_all() {
	[[ "${FLAMEGRAPH}" == "true" ]] && die "--flamegraph cannot be combined with --all (profile one strategy at a time)"

	local COMPARE_DIR="${RESULTS_DIR}/compare-$(date +%Y%m%d-%H%M%S)"
	mkdir -p "${COMPARE_DIR}"

	detect_cores

	local -a ALL_PROFILES=( core asm bytebuddy )
	local -A ALL_STRATS=(
		[core]="REFLECTION,METHOD_HANDLE,LAMBDA"
		[asm]="ASM,ASM_PER_MEMBER"
		[bytebuddy]="BYTE_BUDDY,BYTE_BUDDY_PER_MEMBER"
	)

	local profile jar result_file
	for profile in "${ALL_PROFILES[@]}"; do
		build_or_reuse_jar "${profile}" "${SKIP_BUILD}" "${profile}"
		jar="$(jar_for_profile "${profile}")"

		result_file="${COMPARE_DIR}/${profile}.json"
		local -a cmd=()
		pin_prefix cmd
		cmd+=( java -jar "${jar}" )
		[[ -n "${JVM_ARGS}" ]] && cmd+=( -jvmArgsAppend "${JVM_ARGS}" )
		cmd+=( -bm "${MODE}" -f "${FORKS}" -wi "${WARMUP_ITERATIONS}" -i "${MEASUREMENT_ITERATIONS}" -t "${THREADS}" )
		cmd+=( -p "strategy=${ALL_STRATS[${profile}]}" )
		cmd+=( -rf json -rff "${result_file}" )
		# Baselines are strategy-independent and bundled in every jar; the dedicated pass
		# below runs them once, so keep them out of the per-strategy passes.
		cmd+=( -e ".*Baseline" )
		[[ "${#BENCHMARK_PATTERNS[@]}" -gt 0 ]] && cmd+=( "${BENCHMARK_PATTERNS[@]}" )

		log "Running ${profile} (${ALL_STRATS[${profile}]})"
		log "  ${cmd[*]}"
		echo
		"${cmd[@]}"
		echo
	done

	# Dedicated baseline pass: the reference points carry no strategy param, so run them
	# once from the core jar. Scope them to match the requested benchmarks by mapping each
	# pattern's "Benchmark" -> "Baseline" (ReadBenchmark -> ReadBaseline); stems like "Read"
	# or "Read.*" already match both classes, and no pattern falls back to the full set.
	if [[ "${BASELINES}" == "true" ]]; then
		local -a baseline_patterns=()
		if [[ "${#BENCHMARK_PATTERNS[@]}" -gt 0 ]]; then
			local p
			for p in "${BENCHMARK_PATTERNS[@]}"; do
				baseline_patterns+=( "${p//Benchmark/Baseline}" )
			done
		else
			baseline_patterns=( ".*Baseline" )
		fi

		local core_jar baseline_file
		core_jar="$(jar_for_profile core)"
		baseline_file="${COMPARE_DIR}/baseline.json"
		local -a bcmd=()
		pin_prefix bcmd
		bcmd+=( java -jar "${core_jar}" )
		[[ -n "${JVM_ARGS}" ]] && bcmd+=( -jvmArgsAppend "${JVM_ARGS}" )
		bcmd+=( -bm "${MODE}" -f "${FORKS}" -wi "${WARMUP_ITERATIONS}" -i "${MEASUREMENT_ITERATIONS}" -t "${THREADS}" )
		bcmd+=( -rf json -rff "${baseline_file}" "${baseline_patterns[@]}" )

		log "Running baselines (strategy-independent reference points)"
		log "  ${bcmd[*]}"
		echo
		# A derived pattern may match no baseline (e.g. a method-level pattern); don't let
		# that abort the run when the strategy results are already written.
		"${bcmd[@]}" || warn "Baseline pass matched nothing or failed; continuing with strategy results."
		echo
	fi

	log "All strategies complete. Result files in ${COMPARE_DIR}/"
	print_comparison_summary "${COMPARE_DIR}"
}

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Single strategy run (optionally with async-profiler flamegraphs)
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
basic_run_one() {
	# Strategy -> (jar profile, JMH parameter value)
	local PROFILE STRATEGY_PARAM
	case "$(echo "${STRATEGY}" | tr '[:upper:]' '[:lower:]')" in
		reflection)                 PROFILE="core";      STRATEGY_PARAM="REFLECTION" ;;
		method-handle|methodhandle) PROFILE="core";      STRATEGY_PARAM="METHOD_HANDLE" ;;
		lambda)                     PROFILE="core";      STRATEGY_PARAM="LAMBDA" ;;
		asm)                        PROFILE="asm";       STRATEGY_PARAM="ASM" ;;
		asm-per-member|asmpermember) PROFILE="asm";      STRATEGY_PARAM="ASM_PER_MEMBER" ;;
		bytebuddy|byte-buddy)       PROFILE="bytebuddy"; STRATEGY_PARAM="BYTE_BUDDY" ;;
		bytebuddy-per-member|byte-buddy-per-member) PROFILE="bytebuddy"; STRATEGY_PARAM="BYTE_BUDDY_PER_MEMBER" ;;
		*) die "Unknown strategy: ${STRATEGY} (reflection | method-handle | lambda | asm | asm-per-member | bytebuddy | bytebuddy-per-member)" ;;
	esac

	build_or_reuse_jar "${PROFILE}" "${SKIP_BUILD}" "${PROFILE} (strategy: ${STRATEGY_PARAM})"
	local JAR
	JAR="$(jar_for_profile "${PROFILE}")"

	# ~~~ Result file ~~~
	local -A RESULT_EXT=( [json]="json" [csv]="csv" [text]="txt" [latex]="tex" [scsv]="scsv" )
	local ext="${RESULT_EXT[${RESULT_FORMAT}]:-}"
	[[ -n "${ext}" ]] || die "Unknown result format: ${RESULT_FORMAT} (json | csv | text | latex | scsv)"

	mkdir -p "${RESULTS_DIR}"
	if [[ -z "${RESULT_FILE}" ]]; then
		RESULT_FILE="${RESULTS_DIR}/${PROFILE}-${STRATEGY_PARAM}-$(date +%Y%m%d-%H%M%S).${ext}"
	else
		# A user-supplied --result-file may point at a directory that does not exist yet.
		mkdir -p "$(dirname "${RESULT_FILE}")"
	fi

	# ~~~ Async-profiler setup (download + verify + platform/perf checks) ~~~
	local FLAME_DIR=""
	local -a JVM_ARGS_PARTS=()
	[[ -n "${JVM_ARGS}" ]] && JVM_ARGS_PARTS+=("${JVM_ARGS}")

	if [[ "${FLAMEGRAPH}" == "true" ]]; then
		detect_platform
		setup_async_profiler
		check_perf_event_paranoid

		FLAME_DIR="${RESULTS_DIR}/profile-${PROFILE}-${STRATEGY_PARAM}-$(date +%Y%m%d-%H%M%S)"
		mkdir -p "${FLAME_DIR}"

		local local_lib agent_path ap_out
		local_lib="$(get_profiler_lib_name)"
		agent_path="${RESOLVED_ASYNC_PROFILER_HOME}/lib/${local_lib}"
		# When JFR is requested (incl. the flamegraph,jfr default) record JFR and convert afterwards;
		# %p is replaced by async-profiler with each fork's PID so recordings are not overwritten.
		if [[ "${ASYNC_FORMAT}" == *"jfr"* ]]; then
			ap_out="${FLAME_DIR}/profile-%p.jfr"
		else
			ap_out="${FLAME_DIR}/profile-%p.html"
		fi
		JVM_ARGS_PARTS+=("-agentpath:${agent_path}=start,event=cpu,file=${ap_out},ann")
		log "Async-profiler recordings will be written to: ${FLAME_DIR}"
	fi

	# ~~~ Assemble and run the JMH command ~~~
	detect_cores

	local -a CMD=()
	pin_prefix CMD
	CMD+=( java -jar "${JAR}" )
	[[ "${#JVM_ARGS_PARTS[@]}" -gt 0 ]] && CMD+=( -jvmArgsAppend "${JVM_ARGS_PARTS[*]}" )
	CMD+=( -bm "${MODE}" )
	CMD+=( -f "${FORKS}" )
	CMD+=( -wi "${WARMUP_ITERATIONS}" )
	CMD+=( -i "${MEASUREMENT_ITERATIONS}" )
	CMD+=( -t "${THREADS}" )
	CMD+=( -p "strategy=${STRATEGY_PARAM}" )
	CMD+=( -rf "${RESULT_FORMAT}" -rff "${RESULT_FILE}" )

	# JMH benchmark patterns must come last; if none given, JMH runs everything in the jar.
	if [[ "${#BENCHMARK_PATTERNS[@]}" -gt 0 ]]; then
		CMD+=( "${BENCHMARK_PATTERNS[@]}" )
	fi

	log "Strategy : ${STRATEGY_PARAM} (${PROFILE} jar)"
	log "Mode     : ${MODE}, forks=${FORKS}, warmup=${WARMUP_ITERATIONS}, iterations=${MEASUREMENT_ITERATIONS}, threads=${THREADS}"
	log "Results  : ${RESULT_FILE}"
	log "Running  : ${CMD[*]}"
	echo

	"${CMD[@]}"

	echo

	# ~~~ Convert JFR recordings to flamegraph HTML ~~~
	if [[ "${FLAMEGRAPH}" == "true" ]]; then
		if [[ "${ASYNC_FORMAT}" == *"flamegraph"* ]] && [[ "${ASYNC_FORMAT}" == *"jfr"* ]]; then
			local jfrconv jfr_file html_file
			jfrconv="${RESOLVED_ASYNC_PROFILER_HOME}/bin/jfrconv"
			if [[ -x "${jfrconv}" ]]; then
				for jfr_file in "${FLAME_DIR}"/profile-*.jfr; do
					[[ -f "${jfr_file}" ]] || continue
					html_file="${jfr_file%.jfr}.html"
					log "Converting $(basename "${jfr_file}") to flamegraph..."
					"${jfrconv}" --cpu --lines "${jfr_file}" "${html_file}" \
						|| warn "Failed to convert ${jfr_file}. JFR recording is still available."
				done
			else
				warn "jfrconv not found at ${jfrconv}. Skipping flamegraph conversion."
			fi
		fi
		log "Async-profiler recordings in ${FLAME_DIR}/"
	fi

	log "Done. Results written to ${RESULT_FILE}"
}
