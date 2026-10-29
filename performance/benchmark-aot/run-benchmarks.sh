#!/usr/bin/env bash
set -euo pipefail

benchmark_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${benchmark_dir}/../.." && pwd)"
backend=Asm
inlining=false
quick=false
skip_build=false
cores=""
while (($#)); do
    case "$1" in
        --backend)
            case "${2:-}" in asm) backend=Asm ;; bytebuddy) backend=ByteBuddy ;; *) echo 'Expected --backend asm|bytebuddy' >&2; exit 1 ;; esac
            shift 2 ;;
        --inlining) inlining=true; shift ;;
        --quick) quick=true; shift ;;
        --skip-build) skip_build=true; shift ;;
        --cores) cores="${2:?--cores requires a CPU list}"; shift 2 ;;
        --) shift; break ;;
        -h|--help)
            cat <<'HELP'
Usage: run-benchmarks.sh [--backend asm|bytebuddy] [--inlining] [--quick]
                         [--skip-build] [--cores CPU-LIST] -- [JMH options]
JMH options include:
  -p model=types,members,balanced,large,constructors
  -p entities=512 -p fields=64 -p constructors=8
  -p chunkPolicy=fixed,auto  Compare fixed sizes with the model-aware prototype
  -p targetMethodBytes=325   Estimated per-method byte budget for auto
  -p chunkSize=64,128,256,512
  -p typeRead=128 -p memberWrite=64 -p memberConstructor=32
  -p accessPattern=hot,sampled,spread
  -e '.*(write|construct)'    Run only read (exclude the other operations)
  -f 3 -wi 8 -i 8            Forks, warmup and measurement iterations
All eight AotChunkSizes components are independent -p parameters; zero on
integer components inherits chunkSize. String lookup defaults stay at 500.
--inlining enables HotSpot PrintInlining/LogCompilation in a separate diagnostic
run. --quick is a setup smoke test, not performance or inlining evidence.
HELP
            exit 0 ;;
        *) echo "Unknown runner option: $1 (put JMH options after --)" >&2; exit 1 ;;
    esac
done
if [[ -n "$cores" ]]; then
    command -v taskset >/dev/null || { echo '--cores requires taskset' >&2; exit 1; }
    taskset -c "$cores" true
fi
if [[ "$skip_build" == false ]]; then
    "$repo_dir/gradlew" -p "$repo_dir" :hibernate-accessor-benchmark-aot:shadowJar
fi
jar="$benchmark_dir/build/libs/aot-dispatch.jar"
[[ -f "$jar" ]] || { echo "Missing benchmark JAR: $jar" >&2; exit 1; }
mkdir -p "$repo_dir/performance/.results"
results="$(mktemp -d "$repo_dir/performance/.results/aot-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX")"
{
    date -u
    java -version 2>&1
    uname -a
    git -C "$repo_dir" rev-parse HEAD
    git -C "$repo_dir" status --short
    echo "backend=$backend cores=${cores:-unrestricted} inlining=$inlining quick=$quick"
} > "$results/environment.txt"
fork_args="-Xverify:all -Dhibernate.accessor.benchmark.output=\"$results/bytecode\""
options=()
jmh_options=("$@")
add_default() {
    local key="$1" value="$2" option
    for option in "${options[@]}" "${jmh_options[@]}"; do
        case "$option" in
            "-$key"|"--$key"|"-${key}="*|"--${key}="*) return ;;
        esac
    done
    options+=("-$key" "$value")
}
if [[ "$inlining" == true ]]; then
    fork_args+=" -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation -XX:+PrintInlining -XX:+LogCompilation -XX:LogFile=\"$results/hotspot-%p.xml\""
    add_default f 1
fi
if [[ "$quick" == true ]]; then
    add_default f 1
    add_default wi 1
    add_default i 1
    add_default w 100ms
    add_default r 100ms
fi
command=(java -jar "$jar" ".*${backend}DispatchBenchmark.*" -foe true
    -rf json -rff "$results/jmh.json" -jvmArgsAppend "$fork_args" "${options[@]}" "$@")
if [[ -n "$cores" ]]; then command=(taskset -c "$cores" "${command[@]}"); fi
printf '%q ' "${command[@]}" > "$results/command.txt"
printf '\n' >> "$results/command.txt"
echo "Results: $results"
"${command[@]}" 2>&1 | tee "$results/run.log"
