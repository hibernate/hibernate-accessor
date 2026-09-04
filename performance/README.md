# hibernate-accessor performance benchmarks

JMH benchmarks comparing the accessor strategies (reflection, method-handle, lambda, ASM,
ByteBuddy, and their per-member variants) against each other **and against hand-written
baselines**, so results show the *overhead* of a strategy over plain code, not just how the
strategies rank.

`performance/` is a plain umbrella directory (not a Gradle module). It holds two independent
benchmark **suites** plus one shared runner:

```
performance/
  run-benchmarks.sh          # entrypoint: --suite basic|model, sources the shared engine + suite driver
  scripts/
    lib-bench-common.sh      # shared engine: P-core pinning, async-profiler, perf panic, JMH-JSON table
    suite-basic.sh           # basic-suite driver
    suite-model.sh           # model-suite driver
  benchmark-basic/           # :hibernate-accessor-benchmark-basic  -- fixed, hand-authored models
  benchmark-model/
    generator/               # :hibernate-accessor-benchmark-model-generator  -- emits the model at build time
    benchmarks/              # :hibernate-accessor-benchmark-model  -- benchmarks over the generated model
  .results/                  # all suites write here (git-ignored)
  .async-profiler/           # pinned, checksum-verified async-profiler cache (git-ignored)
```

Both suites share the same system-config/profiling engine (CPU-core pinning, async-profiler
download/verify, `perf_event_paranoid` handling, the `jq` comparison table), so the runner is one
entrypoint with a `--suite` switch:

```bash
./performance/run-benchmarks.sh --suite basic --all --quick ReadBenchmark
./performance/run-benchmarks.sh --suite model --quick --models e8_f16_d2
```

`--suite` defaults to `basic`. Each suite has its own axes and its own `--help`:

```bash
./performance/run-benchmarks.sh --suite basic --help
./performance/run-benchmarks.sh --suite model --help
```

On Linux, `--pin` is optional for both suites: with no `--pin` the runner auto-detects P-cores on a
hybrid CPU (via `cpufreq/base_frequency`) and pins the forked JVM to them, falling back to no pinning
on a uniform CPU or when `taskset` is unavailable. Results are written under `performance/.results/`
(git-ignored).

---

## Basic suite (`--suite basic`)

Fixed, hand-authored models small enough to live in a file or two.

- `benchmark-basic/src/main/java/.../performance` -- the benchmarks
  - `ReadBenchmark` / `WriteBenchmark` -- single field/getter read & write, primitive vs reference
  - `InstantiateBenchmark` -- no-arg and all-args construction
  - `BulkBenchmark` -- multi-value read/write at arities 1 / 8 / 32
  - `MegamorphicBenchmark` -- one getter read across many entity types at a single (megamorphic) call site
  - `PolymorphicAccessBenchmark` -- the megamorphic call site split by field vs getter access; the case
    where a code-generating strategy (ASM/ByteBuddy) is expected to beat the JDK strategies
  - `CascadeBenchmark` -- a realistic validator/ORM traversal: walk a whole book-order object graph
    (order → customer → address, order → lines → book → author/publisher), reading every property
    through the accessors at two shared, megamorphic call sites
  - `*Baseline` -- hand-written reference points for each of the above (see below)
  - `entities/`, `baseline/` -- benchmark fixtures and the hand-written accessors

### Baselines

Every operation has two hand-written reference points, **not** parameterized by strategy:

- **raw** -- the property is read/written/constructed directly in the benchmark method; the absolute
  floor the JIT fully inlines. Primitives stay unboxed, so the boxing the strategies pay shows up as
  part of their overhead.
- **iface** -- the same operation behind a hand-written implementation of the accessor interface, so
  the call shape matches the strategies and the delta isolates each strategy's *internal* cost.

### Axes

`strategy` × `access` (FIELD/METHOD) × `valueKind` (PRIMITIVE/REFERENCE), plus `memberCount` for bulk
and `polymorphic` for the megamorphic scenario. Default mode is `AverageTime` (ns/op) because the
overhead-over-baseline deltas read linearly in the time domain; switch with `--mode`.

### Running

```bash
# Build + run a strategy (the runner picks the matching jar automatically)
./performance/run-benchmarks.sh --suite basic --strategy asm --quick ReadBenchmark

# Compare a strategy against its baselines in one run (pattern matches both)
./performance/run-benchmarks.sh --suite basic --strategy lambda "Read.*"

# Throughput instead of average time, pinned to cores, reusing the built jar
./performance/run-benchmarks.sh --suite basic -s lambda -m thrpt --pin 0-3 --skip-build "Bulk.*"
```

#### Comparing all strategies

`--all` runs every strategy in one command. Because the strategies span three jars, this is three
JMH processes (the core jar runs reflection/method-handle/lambda together); each writes its JSON
into a fresh `.results/compare-<timestamp>/` directory and a summary table is printed at the end,
grouped by benchmark and parameter combination (`access`, `valueKind`, `memberCount`, ...) with the
strategies ranked by score within each combo:

```bash
./performance/run-benchmarks.sh --suite basic --all --quick ReadBenchmark
```

The hand-written baselines are strategy-independent, so they are excluded from the per-strategy
passes and measured once in a dedicated pass (`baseline.json`); in the table their rows sort next to
the matching benchmark and their STRATEGY column reads `raw (base)` / `iface (base)`. Pass
`--no-baselines` to skip them. `--all` honours `--quick`, the iteration/fork/thread flags, benchmark
patterns and core pinning, and prints the table with `jq` (install it, or the raw JSON is still
written). Benchmark patterns scope the baseline pass too: each pattern's `Benchmark` is mapped to
`Baseline` (`ReadBenchmark` → `ReadBaseline`; stems like `Read` or `Read.*` already match both), so
`--all "ReadBenchmark"` measures only the Read baselines. With no pattern the full reference set runs.

#### Flamegraphs

`--flamegraph` records CPU profiles with [async-profiler](https://github.com/async-profiler/async-profiler).
The runner downloads a pinned version (v4.5) into `performance/.async-profiler/` and verifies it
against a built-in per-platform SHA-256 before use, so no manual setup is needed:

```bash
./performance/run-benchmarks.sh --suite basic --strategy asm --flamegraph "ReadBenchmark"
```

By default it records JFR and converts each fork's recording to a flamegraph HTML (`--async-format`
takes `flamegraph`, `jfr`, or `flamegraph,jfr`); output lands in a per-run `.results/profile-*/`
folder. To reuse an existing install instead of downloading, pass `--async-profiler-path <dir>` or
set `ASYNC_PROFILER_HOME`. On Linux, hardware CPU profiling needs `perf_event_paranoid <= 1`; if it
is higher the runner offers to lower it for the run (via `sudo`) and restores the original value on
exit.

`--suite basic --help` lists all options (forks, warmup/measurement iterations, threads, result
format/file, core pinning).

---

## Model suite (`--suite model`)

Rather than hand-authoring one shape, the model suite generates a whole entity model at build time --
swept across **width** (fields per entity) × **count** (number of entity types) × **depth**
(reference-chain length) -- so the strategies can be compared at scales that expose megamorphic and
footprint effects the basic suite cannot. The generator (`:hibernate-accessor-benchmark-model-generator`)
emits the entity `.class` files and `models/<modelId>.properties` descriptors; the benchmarks module
consumes them purely by reflection off the classpath. Model ids read as `e<count>_f<fields>_d<depth>`
(e.g. `e256_f16_d4` = 256 entity types, 16 fields each, depth 4). The set of models is declared in
`benchmark-model/generator/benchmark-models.txt`.

The suite is three programs over that one generated matrix:

- **throughput** -- `GeneratedGraphBenchmark` (`avgt`): steady-state cost of walking the object graph
  and reading every property through the accessors. Includes the whole-model `GENERATED_DOUBLE_SWITCH`
  strategy (a build-time double-`tableswitch` reader) alongside the per-member factories.
- **build** -- `AccessorBuildBenchmark` (`SingleShotTime`): the cold cost of building every reader once,
  per strategy. `@Warmup(0)`/`@Measurement(1)`; the runner does not pass `-wi/-i`, and controls
  repetition with `--build-forks` (default 10). No `GENERATED_DOUBLE_SWITCH` (it is whole-model, not a
  per-member factory).
- **footprint** -- `FootprintReport` (a plain `main`, one fresh JVM per cell): classes loaded and
  metaspace bytes consumed by building all readers, written as a CSV.

### Axes

`strategy` × `modelId` × `access` (FIELD/METHOD), plus `readMode` (ALL / HOT_SUBSET) for throughput.
With no override each program runs its own `@Param` defaults; narrow them with `--models`,
`--access` and (throughput only) `--read-modes`, each taking a comma-separated list.

### Running

By default the model suite runs all three programs and sweeps every strategy across the
core/asm/bytebuddy jars, writing into a single `.results/model-<timestamp>/` directory
(`throughput-<profile>.json`, `build-<profile>.json`, `footprint.csv`), then prints a comparison
table for throughput and build and the footprint CSV as a table.

```bash
# Quick smoke over one model, all three programs, every strategy
./performance/run-benchmarks.sh --suite model --quick --models e8_f16_d2

# Just throughput, narrowing the axes
./performance/run-benchmarks.sh --suite model --programs throughput --read-modes HOT_SUBSET

# One strategy, one model, field access only
./performance/run-benchmarks.sh --suite model -s asm-per-member --models e256_f64_d4 --access FIELD

# Just the cold build cost, more forks for confidence
./performance/run-benchmarks.sh --suite model --programs build --build-forks 20
```

`--suite model --help` lists all options. See
`benchmark-model/benchmarks/BENCHMARK_MODEL_PLAN.md` for the full design of the model matrix and what
each program is meant to expose.

---

## Jars

Each suite builds its own set of shadow jars, split along real classpath boundaries rather than
one-per-strategy (reflection, method-handle and lambda -- plus the model suite's
`GENERATED_DOUBLE_SWITCH` -- all live in the core jar). The ASM and ByteBuddy factories are resolved
reflectively, so the core jar compiles and runs with no ASM/ByteBuddy on the classpath; requesting
those strategies from the core jar fails fast.

Basic suite (`:hibernate-accessor-benchmark-basic`):

| Gradle task                                             | Jar                                       | Strategies |
|--------------------------------------------------------|-------------------------------------------|------------|
| `:hibernate-accessor-benchmark-basic:benchmarkJarCore`      | `hibernate-accessor-benchmark-basic-core.jar`      | reflection, method-handle, lambda |
| `:hibernate-accessor-benchmark-basic:benchmarkJarAsm`       | `hibernate-accessor-benchmark-basic-asm.jar`       | + ASM (+ per-member) |
| `:hibernate-accessor-benchmark-basic:benchmarkJarBytebuddy` | `hibernate-accessor-benchmark-basic-bytebuddy.jar` | + ByteBuddy (+ per-member) |
| `:hibernate-accessor-benchmark-basic:benchmarkJars`         | all of the above                          | |

Model suite (`:hibernate-accessor-benchmark-model`): the same three tasks/jars with
`hibernate-accessor-benchmark-model-<profile>.jar` names. Their `benchmarkJar*` tasks depend on the
generator's `generateBenchmarkModel`, so the generated model is bundled into every model jar.

The runner picks the matching jar automatically; the tasks are here for running a jar directly:

```bash
./gradlew :hibernate-accessor-benchmark-basic:benchmarkJarCore
java -jar performance/benchmark-basic/build/libs/hibernate-accessor-benchmark-basic-core.jar \
    -p strategy=LAMBDA ReadBenchmark

./gradlew :hibernate-accessor-benchmark-model:benchmarkJars
java -jar performance/benchmark-model/benchmarks/build/libs/hibernate-accessor-benchmark-model-core.jar \
    -p strategy=GENERATED_DOUBLE_SWITCH -p modelId=e8_f16_d2 GeneratedGraphBenchmark
```

## Useful examples

```bash
# Fast smoke check while iterating on a basic benchmark (low confidence, seconds not minutes)
./performance/run-benchmarks.sh --suite basic -s lambda --quick ReadBenchmark

# Full comparison of every strategy plus the baselines, printed as one table
./performance/run-benchmarks.sh --suite basic --all

# Higher-confidence basic run: more forks and iterations, CSV output for a spreadsheet
./performance/run-benchmarks.sh --suite basic -s lambda -f 10 -wi 10 -i 10 -rf csv "Bulk.*"

# Find where a strategy spends its time (CPU flamegraph)
./performance/run-benchmarks.sh --suite basic -s reflection --flamegraph "ReadBenchmark"

# Rebuild all three basic jars once, then compare repeatedly without rebuilding
./gradlew :hibernate-accessor-benchmark-basic:benchmarkJars
./performance/run-benchmarks.sh --suite basic --all --skip-build --quick

# Full model sweep over a small/medium/large model sample, reusing built jars
./performance/run-benchmarks.sh --suite model --skip-build --models e8_f16_d2,e64_f64_d2,e256_f16_d2
```
