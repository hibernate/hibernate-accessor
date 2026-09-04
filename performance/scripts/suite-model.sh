#!/usr/bin/env bash
#
# SPDX-License-Identifier: Apache-2.0
# Copyright: Red Hat Inc. and Hibernate Authors
#
# Model suite driver: the build-time generated, parameterized entity model.
#
# Sourced by ../run-benchmarks.sh after lib-bench-common.sh. Exposes run_suite(); relies on the
# shared engine for pinning, async-profiler, the comparison table and jar build/lookup.
#
# The suite is three programs over ONE byte-identical generated model matrix (width x count x depth):
#   throughput  GeneratedGraphBenchmark  (avgt)  -- steady-state graph-walk cost per strategy
#   build       AccessorBuildBenchmark   (ss)    -- cold cost of building every reader once
#   footprint   FootprintReport (plain main)     -- classes loaded + metaspace, fresh JVM per cell
#
# The three share the generated model; each strategy lives in its classpath profile jar (core/
# asm/bytebuddy), so a full sweep is at most 3 JMH processes per program plus the footprint grid.

SUITE_MODULE=":hibernate-accessor-benchmark-model"
SUITE_JAR_BASE="hibernate-accessor-benchmark-model"
SUITE_JAR_DIR="${PERF_DIR}/benchmark-model/benchmarks/build/libs"

readonly FOOTPRINT_MAIN="org.hibernate.accessor.performance.FootprintReport"
readonly THROUGHPUT_CLASS="GeneratedGraphBenchmark"
readonly BUILD_CLASS="AccessorBuildBenchmark"

# FootprintReport is a plain main() with no @Param, so the suite drives its axes itself.
# These defaults mirror AccessorBuildBenchmark's modelId @Param (a small/medium/large sample).
readonly FOOTPRINT_DEFAULT_MODELS="e8_f16_d2,e64_f64_d2,e256_f16_d2"
readonly FOOTPRINT_DEFAULT_ACCESS="FIELD,METHOD"

model_usage() {
	cat <<'EOF'
Usage: run-benchmarks.sh --suite model [options]

Runs the generated-model suite (throughput + build + footprint) over the model matrix, pinned
to P-cores. By default it sweeps every strategy across the core/asm/bytebuddy jars and writes
into .results/model-<ts>/.

Options:
  -s, --strategy <name>     run ONE strategy instead of the full sweep:
                            reflection | method-handle | lambda | asm | asm-per-member |
                            bytebuddy | bytebuddy-per-member | generated-double-switch
                            (generated-double-switch is throughput-only)
      --programs <list>     csv subset of: throughput,build,footprint          (default: all three)
      --models <list>       csv modelId override, e.g. e8_f16_d2,e256_f64_d4   (default: benchmark @Param)
      --read-modes <list>   csv readMode override: ALL,HOT_SUBSET (throughput)  (default: benchmark @Param)
      --access <list>       csv access override: FIELD,METHOD                    (default: benchmark @Param)
  -f, --forks <n>           throughput forks                                    (default: 3)
  -wi, --warmup <n>         throughput warmup iterations                        (default: 5)
  -i, --iterations <n>      throughput measurement iterations                   (default: 5)
  -t, --threads <n>         throughput measurement threads                      (default: 1)
      --build-forks <n>     forks for the cold-build (ss) pass                  (default: 10)
      --quick               fast, low-confidence settings (throughput 1/3/5; build 2 forks)
      --pin <cores>         pin the forked JVM to CPU cores via taskset, e.g. "0-3"
                            (Linux; when omitted, P-cores are auto-detected on hybrid CPUs)
      --jvm-args <args>     extra JVM args forwarded to the forked JVM
      --skip-build          reuse the already-built jars
  -h, --help                show this help

Notes:
  - build (AccessorBuildBenchmark) is SingleShotTime with @Warmup(0)/@Measurement(1); the suite
    does not pass -wi/-i so those annotations hold, and controls repetition via --build-forks.
  - footprint (FootprintReport) has no JMH params; --models / --access drive it, defaulting to
    a small/medium/large model sample x FIELD,METHOD. generated-double-switch is skipped there
    and in the build pass (it is whole-model, not a per-member factory strategy).

Examples:
  ./run-benchmarks.sh --suite model --quick --models e8_f16_d2
  ./run-benchmarks.sh --suite model --programs throughput --read-modes HOT_SUBSET
  ./run-benchmarks.sh --suite model -s asm-per-member --models e256_f64_d4 --access FIELD
EOF
}

# Normalizes a CLI strategy name to its JMH enum value.
model_strategy_enum() {
	case "$(echo "$1" | tr '[:upper:]' '[:lower:]')" in
		reflection)                                 echo "REFLECTION" ;;
		method-handle|methodhandle)                 echo "METHOD_HANDLE" ;;
		lambda)                                     echo "LAMBDA" ;;
		asm)                                        echo "ASM" ;;
		asm-per-member|asmpermember)                echo "ASM_PER_MEMBER" ;;
		bytebuddy|byte-buddy)                       echo "BYTE_BUDDY" ;;
		bytebuddy-per-member|byte-buddy-per-member) echo "BYTE_BUDDY_PER_MEMBER" ;;
		generated-double-switch|double-switch|generated_double_switch) echo "GENERATED_DOUBLE_SWITCH" ;;
		*) die "Unknown strategy: $1 (see --suite model --help)" ;;
	esac
}

run_suite() {
	# ~~~ Defaults ~~~
	local STRATEGY=""                # empty -> full sweep
	local PROGRAMS="throughput,build,footprint"
	local MODELS="" READ_MODES="" ACCESS_KINDS=""
	local FORKS="" WARMUP_ITERATIONS="" MEASUREMENT_ITERATIONS="" THREADS=""
	local BUILD_FORKS=""
	local QUICK="false"
	PIN_CORES=""                     # read by detect_cores
	local JVM_ARGS=""
	local SKIP_BUILD="false"

	# ~~~ Argument parsing ~~~
	while [[ $# -gt 0 ]]; do
		case "$1" in
			-s|--strategy)   STRATEGY="$2"; shift 2 ;;
			--programs)      PROGRAMS="$2"; shift 2 ;;
			--models)        MODELS="$2"; shift 2 ;;
			--read-modes)    READ_MODES="$2"; shift 2 ;;
			--access)        ACCESS_KINDS="$2"; shift 2 ;;
			-f|--forks)      is_positive_int "${2:-}" || die "--forks requires a positive integer"; FORKS="$2"; shift 2 ;;
			-wi|--warmup)    is_positive_int "${2:-}" || die "--warmup requires a positive integer"; WARMUP_ITERATIONS="$2"; shift 2 ;;
			-i|--iterations) is_positive_int "${2:-}" || die "--iterations requires a positive integer"; MEASUREMENT_ITERATIONS="$2"; shift 2 ;;
			-t|--threads)    is_positive_int "${2:-}" || die "--threads requires a positive integer"; THREADS="$2"; shift 2 ;;
			--build-forks)   is_positive_int "${2:-}" || die "--build-forks requires a positive integer"; BUILD_FORKS="$2"; shift 2 ;;
			--quick)         QUICK="true"; shift ;;
			--pin)           PIN_CORES="$2"; shift 2 ;;
			--jvm-args)      JVM_ARGS="$2"; shift 2 ;;
			--skip-build)    SKIP_BUILD="true"; shift ;;
			-h|--help)       model_usage; exit 0 ;;
			-*)              die "Unknown option: $1 (see --suite model --help)" ;;
			*)               die "Unexpected argument: $1 (the model suite takes no benchmark patterns; use --programs)" ;;
		esac
	done

	# --quick fills only counts not set explicitly, so an explicit flag always wins.
	if [[ "${QUICK}" == "true" ]]; then
		[[ -z "${FORKS}" ]]                  && FORKS=1
		[[ -z "${WARMUP_ITERATIONS}" ]]      && WARMUP_ITERATIONS=3
		[[ -z "${MEASUREMENT_ITERATIONS}" ]] && MEASUREMENT_ITERATIONS=5
		[[ -z "${BUILD_FORKS}" ]]            && BUILD_FORKS=2
	fi
	: "${FORKS:=3}"
	: "${WARMUP_ITERATIONS:=5}"
	: "${MEASUREMENT_ITERATIONS:=5}"
	: "${THREADS:=1}"
	: "${BUILD_FORKS:=10}"

	# Which programs are enabled?
	local want_throughput="false" want_build="false" want_footprint="false"
	local prog
	IFS=',' read -ra _progs <<< "${PROGRAMS}"
	for prog in "${_progs[@]}"; do
		case "${prog}" in
			throughput) want_throughput="true" ;;
			build)      want_build="true" ;;
			footprint)  want_footprint="true" ;;
			"")         ;;
			*) die "Unknown program: ${prog} (throughput | build | footprint)" ;;
		esac
	done

	# ~~~ Resolve the strategy plan: full sweep vs one strategy ~~~
	# Per-profile strategy sets, keyed by jar profile. Throughput includes the whole-model
	# GENERATED_DOUBLE_SWITCH; build/footprint exclude it (it has no per-member factory).
	local -a RUN_PROFILES=()
	local -A THRU_STRATS=() BUILD_STRATS=()
	local -a FOOT_STRATS=()

	if [[ -n "${STRATEGY}" ]]; then
		local s profile
		s="$(model_strategy_enum "${STRATEGY}")"
		profile="$(profile_for_strategy "${s}")"
		RUN_PROFILES=( "${profile}" )
		THRU_STRATS["${profile}"]="${s}"
		if [[ "${s}" == "GENERATED_DOUBLE_SWITCH" ]]; then
			[[ "${want_build}" == "true" ]]     && { warn "generated-double-switch has no build pass; skipping build."; want_build="false"; }
			[[ "${want_footprint}" == "true" ]] && { warn "generated-double-switch has no footprint pass; skipping footprint."; want_footprint="false"; }
		else
			BUILD_STRATS["${profile}"]="${s}"
			FOOT_STRATS=( "${s}" )
		fi
	else
		RUN_PROFILES=( core asm bytebuddy )
		THRU_STRATS=(
			[core]="REFLECTION,METHOD_HANDLE,LAMBDA,GENERATED_DOUBLE_SWITCH"
			[asm]="ASM,ASM_PER_MEMBER"
			[bytebuddy]="BYTE_BUDDY,BYTE_BUDDY_PER_MEMBER"
		)
		BUILD_STRATS=(
			[core]="REFLECTION,METHOD_HANDLE,LAMBDA"
			[asm]="ASM,ASM_PER_MEMBER"
			[bytebuddy]="BYTE_BUDDY,BYTE_BUDDY_PER_MEMBER"
		)
		FOOT_STRATS=( REFLECTION METHOD_HANDLE LAMBDA ASM ASM_PER_MEMBER BYTE_BUDDY BYTE_BUDDY_PER_MEMBER )
	fi

	# ~~~ Build the jars we need (once), then set up the run ~~~
	local profile
	for profile in "${RUN_PROFILES[@]}"; do
		build_or_reuse_jar "${profile}" "${SKIP_BUILD}" "${profile}"
	done

	detect_cores

	local OUT="${RESULTS_DIR}/model-$(date +%Y%m%d-%H%M%S)"
	mkdir -p "${OUT}"
	log "Model suite output: ${OUT}"

	[[ "${want_throughput}" == "true" ]] && model_run_throughput
	[[ "${want_build}" == "true" ]]      && model_run_build
	[[ "${want_footprint}" == "true" ]]  && model_run_footprint

	log "Model suite complete. Results in ${OUT}/"
}

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# throughput -- GeneratedGraphBenchmark (avgt)
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
model_run_throughput() {
	log "=== throughput (GeneratedGraphBenchmark, avgt) ==="
	local profile jar
	for profile in "${RUN_PROFILES[@]}"; do
		[[ -n "${THRU_STRATS[${profile}]:-}" ]] || continue
		jar="$(jar_for_profile "${profile}")"

		local -a cmd=()
		pin_prefix cmd
		cmd+=( java -jar "${jar}" )
		[[ -n "${JVM_ARGS}" ]] && cmd+=( -jvmArgsAppend "${JVM_ARGS}" )
		cmd+=( -bm avgt -f "${FORKS}" -wi "${WARMUP_ITERATIONS}" -i "${MEASUREMENT_ITERATIONS}" -t "${THREADS}" )
		cmd+=( -p "strategy=${THRU_STRATS[${profile}]}" )
		[[ -n "${MODELS}" ]]     && cmd+=( -p "modelId=${MODELS}" )
		[[ -n "${ACCESS_KINDS}" ]] && cmd+=( -p "access=${ACCESS_KINDS}" )
		[[ -n "${READ_MODES}" ]] && cmd+=( -p "readMode=${READ_MODES}" )
		cmd+=( -rf json -rff "${OUT}/throughput-${profile}.json" )
		cmd+=( "${THROUGHPUT_CLASS}" )

		log "Running throughput ${profile} (${THRU_STRATS[${profile}]})"
		log "  ${cmd[*]}"
		echo
		"${cmd[@]}"
		echo
	done
	print_comparison_summary "${OUT}" "throughput-*.json"
}

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# build -- AccessorBuildBenchmark (ss). No -wi/-i: @Warmup(0)/@Measurement(1) hold.
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
model_run_build() {
	log "=== build (AccessorBuildBenchmark, ss, forks=${BUILD_FORKS}) ==="
	local profile jar
	for profile in "${RUN_PROFILES[@]}"; do
		[[ -n "${BUILD_STRATS[${profile}]:-}" ]] || continue
		jar="$(jar_for_profile "${profile}")"

		local -a cmd=()
		pin_prefix cmd
		cmd+=( java -jar "${jar}" )
		[[ -n "${JVM_ARGS}" ]] && cmd+=( -jvmArgsAppend "${JVM_ARGS}" )
		cmd+=( -bm ss -f "${BUILD_FORKS}" )
		cmd+=( -p "strategy=${BUILD_STRATS[${profile}]}" )
		[[ -n "${MODELS}" ]]       && cmd+=( -p "modelId=${MODELS}" )
		[[ -n "${ACCESS_KINDS}" ]] && cmd+=( -p "access=${ACCESS_KINDS}" )
		cmd+=( -rf json -rff "${OUT}/build-${profile}.json" )
		cmd+=( "${BUILD_CLASS}" )

		log "Running build ${profile} (${BUILD_STRATS[${profile}]})"
		log "  ${cmd[*]}"
		echo
		"${cmd[@]}"
		echo
	done
	print_comparison_summary "${OUT}" "build-*.json"
}

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# footprint -- FootprintReport, a fresh JVM per (strategy, model, access) cell.
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
model_run_footprint() {
	log "=== footprint (FootprintReport, fresh JVM per cell) ==="

	local models_csv="${MODELS:-${FOOTPRINT_DEFAULT_MODELS}}"
	local access_csv="${ACCESS_KINDS:-${FOOTPRINT_DEFAULT_ACCESS}}"
	local -a foot_models foot_access
	IFS=',' read -ra foot_models <<< "${models_csv}"
	IFS=',' read -ra foot_access <<< "${access_csv}"

	local csv="${OUT}/footprint.csv"
	# FootprintReport prints only the data row; the suite owns the header.
	echo "strategy,modelId,access,readers,classesLoaded,metaspaceBytes" > "${csv}"

	local s profile jar model access
	for s in "${FOOT_STRATS[@]}"; do
		profile="$(profile_for_strategy "${s}")"
		jar="$(jar_for_profile "${profile}")"
		for model in "${foot_models[@]}"; do
			for access in "${foot_access[@]}"; do
				local -a cmd=()
				pin_prefix cmd
				cmd+=( java -cp "${jar}" "${FOOTPRINT_MAIN}" "${s}" "${model}" "${access}" )
				log "footprint ${s} ${model} ${access}"
				# Fresh JVM per cell; append the single CSV row it prints.
				"${cmd[@]}" >> "${csv}" || warn "footprint cell failed: ${s} ${model} ${access}"
			done
		done
	done

	log "Footprint CSV: ${csv}"
	if command -v column >/dev/null 2>&1; then
		column -t -s , "${csv}" >&2
	else
		cat "${csv}" >&2
	fi
}
