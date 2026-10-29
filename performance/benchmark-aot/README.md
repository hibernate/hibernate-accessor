# AOT dispatch chunks and inlining

This benchmark measures cached `ValueReader.get`, `ValueWriter.set`, and
`Instantiator.create` calls. Generation, reflection/name lookup, accessor creation,
and correctness checks run during trial setup. Writes reuse a reference value;
constructor argument arrays are preallocated, but construction itself allocates.
The same generated workload is used by the AOT boundary tests for both backends.

Run from the repository root:

```bash
# Compare sizes with the entity/field counts held constant.
performance/benchmark-aot/run-benchmarks.sh -- -p model=members -p chunkSize=64,128,256,512
# Override the workload shape; zero selects the model's preset count.
performance/benchmark-aot/run-benchmarks.sh -- -p entities=512 -p fields=64 -p chunkSize=64,256
# Tune read dispatch independently at each level; measure only reads.
performance/benchmark-aot/run-benchmarks.sh -- -p model=large \
  -p typeRead=64,256 -p memberRead=64,256 -e '.*(write|construct)'
# Constructor dispatch across both hosts and overloads.
performance/benchmark-aot/run-benchmarks.sh --backend bytebuddy -- \
  -p model=constructors -p typeConstructor=16,64 -p memberConstructor=32,128
```

Use JMH's `-p` repeatedly for overrides. Independent lists produce a
Cartesian product. `chunkSize` defaults to 256 and supplies all six integer sizes;
nonzero operation-specific parameters override it:

| Level | Read | Write | Constructor | String lookup (setup only) |
| --- | --- | --- | --- | --- |
| Type dispatch | `typeRead` | `typeWrite` | `typeConstructor` | `typeLookup` |
| Member dispatch | `memberRead` | `memberWrite` | `memberConstructor` | `memberLookup` |

String sizes default to 500. `memberConstructor` preserves the generator's conversion
budget: divide by the largest constructor arity (minimum divisor 1), with at least one
case per helper. Thus changing the overload count can also change the effective chunk
size; hold it fixed during a chunk sweep. Large chunk sizes can exceed JVM bytecode limits;
generation fails during setup rather than silently changing the requested size.

| Model | Entities | Fields per entity | Constructors per entity |
| --- | ---: | ---: | ---: |
| `types` | 1024 | 4 | 1 |
| `members` | 4 | 512 | 1 |
| `balanced` (default) | 192 | 192 | 1 |
| `large` | 512 | 512 | 1 |
| `constructors` | 64 | 4 | 16 |

Override `entities`, `fields`, or `constructors` to set exact counts. Constructors range
from 1 to 64 overloads, with arities 0 through count minus one, taking reference arguments.
This is a reference-property workload; primitive conversion and realistic constructor
bodies may have different inlining behavior.

`accessPattern=hot` cycles over up to four accessors at the start of the model;
`sampled` cycles over sixteen evenly spread entity/member pairs; `spread` visits all
accessors. Sample positions are independent of chunk size to keep each comparison's
workload constant. State is thread-local, so writes do not race between benchmark threads.

## Experimental model-aware sizing

`-p chunkPolicy=auto` chooses integer chunk sizes from the known synthetic model.
`fixed` remains the default. Nonzero `typeRead`, `memberWrite`, etc. are exact overrides
under either policy; `chunkSize` supplies the fallback. String lookup is unchanged.

For `N` cases and size `k`, the heuristic estimates:

```
dispatcher bytes ≈ overhead + dispatcher-case cost × ceil(N / k)
helper bytes    ≈ overhead + sum(case costs in that helper)
```

It minimizes the larger method size, prefers fewer helpers on ties, and leaves switches
that already fit unsplit. For uniform cases the balance is near
`sqrt(N × dispatcher-case cost / helper-case cost)`. Read/write/type dispatch have different
cost estimates, and constructor cases grow with arity. If no two-level partition fits
`targetMethodBytes` (default 325), it retains the explicit fallback instead of claiming
an inlining guarantee. A configurable target is needed because JVMs have different limits.

```bash
performance/benchmark-aot/run-benchmarks.sh -- \
  -p model=balanced,large -p chunkPolicy=fixed,auto -p accessPattern=hot,spread
```

The prototype is deliberately in benchmark infrastructure, not the published generator.
Its byte-cost estimates cover this benchmark's reference fields and synthetic constructors;
they do not yet model arbitrary getters, primitive conversions, or user constructor bodies.
Recorded bytecode lengths are the actual output and can be compared with the estimates.
Meeting a size target does not guarantee inlining or better throughput: hotness, caller
budgets, and the extra dispatch levels still matter. Compare `fixed` and `auto` with the
same model/access pattern before considering a production policy.

## Inspecting inlining

```bash
performance/benchmark-aot/run-benchmarks.sh --inlining --cores 2-3 -- \
  -p model=members -p memberRead=16,64,256,512 -p accessPattern=hot \
  -e '.*(write|construct)' -wi 8 -i 3
```

Use CPU IDs available on your machine, or omit `--cores`. `--inlining` enables HotSpot
`PrintCompilation`, `PrintInlining`, and XML `LogCompilation`, with one fork by default.
Normal tiered compilation remains enabled. Run one operation and a few sizes at a time:
look in `run.log` for `dispatch.Reader::get`, `dispatch.Writer::set`,
`dispatch.Instantiator::create`, and host `$$__hibernateAccessor_read/write/create`
methods (including their `$N` helpers). Follow the indentation to see each call chain.
Reasons such as `inline (hot)`, `hot method too big`, and `callee is too large` describe
individual compilation decisions. The XML logs preserve compilation IDs and call-site
context for inspection with tools such as JITWatch.

A method may be rejected in an early compilation and inlined later; inspect the final hot
compilation and caller context, not just the first matching line. Absence of a line is not
proof of non-inlining. Inlining depends on JDK, call-site profiles, and the whole caller's
budget, so the runner records evidence rather than declaring a universal size threshold.

Each run writes a fresh `performance/.results/aot-*` directory containing:

- `jmh.json`, `run.log`, exact command, JDK/platform and Git state.
- `bytecode/*/configuration.txt` with effective counts and sizes.
- `bytecode/*/bytecode.csv` with exact method Code lengths and class SHA-256 hashes.
- `hotspot-*.xml` for diagnostic runs, one file per forked process.

Each trial gets its own bytecode directory (including separate operations/forks). Hashes
identify size settings that produce identical classes; code lengths let you connect
inlining decisions to actual emitted methods. No bytecode files are written in the timed
methods. Setup verifies every field read/write and constructor result before measurement.

Use normal runs without `--inlining` for timing comparisons. Keep model, access pattern,
JDK, CPU affinity and warmup/measurement settings fixed. `--quick` only checks setup and
runner wiring; it does not establish performance or steady-state inlining. `--skip-build`
reuses the existing JAR. Run `--help` for runner options; pass additional JMH options after
`--` (for example `-prof gc` when studying constructor allocation).

## Measurements on 2026-10-29

Keep the published defaults at **256 for integer dispatch and 500 for string lookup**.
There was no alternative that consistently improved the tested access patterns. The
model-aware policy remains an opt-in benchmark prototype, not a published default.

Measurements used Temurin 25.0.5 on an Intel i9-12900KS, pinned to separate P-cores 2 and 4,
with one benchmark thread. The initial screen covered 16, 32, 64, 128, 256 and 512. The
confirmation runs used two fresh forks, six one-second warmup iterations, five 500 ms
measurement iterations, and a fixed 2 GiB heap. Diagnostic runs used separate forks with
HotSpot logging and were excluded from timing comparisons. These are synthetic reference
fields/constructors on one JDK and machine, not a universal throughput ranking.

Representative confirmation results (ns/op; lower is better):

| Operation/model | Pattern | Fixed 256 | Model-aware | Result |
| --- | --- | ---: | ---: | --- |
| Read, 512 types × 512 fields | hot | 5.807 | 4.796 | automatic faster |
| Read, 512 types × 512 fields | spread | 110.638 | 81.437 | automatic faster |
| Read, 192 types × 192 fields | hot | 4.372 | 5.031 | automatic slower |
| Read, 192 types × 192 fields | spread | 25.582 | 31.173 | automatic slower |
| Read, 4 types × 512 fields | hot | 4.230 | 3.753 | automatic faster |
| Read, 4 types × 512 fields | spread | 14.739 | 17.603 | automatic slower |
| Write, 192 types × 192 fields | spread | 35.842 | 53.249 | automatic slower |
| Construct, 64 types × 16 overloads | spread | 17.682 | 14.406 | automatic faster |

Byte Buddy confirmed the large-model read direction: 102.250 → 81.261 ns/op for spread
access and 4.991 → 4.726 ns/op for hot access. Both backends emitted identical hashes for
all 517 generated classes in that model under each policy. Confidence intervals and
individual measurements are retained in the CSV below; small differences and the noisy
large write cases should not be treated as established improvements.

The policy selected type/member read sizes **20/21** for the large model, **12/13** for
the balanced model, and **4/21** for the member-heavy model. Smaller generated methods
help some call chains, but the two-level index arithmetic and extra calls matter too.
Overriding the balanced model's read sizes to powers of two (16/16) improved its automatic
results to 4.366 ns/op hot and 27.020 ns/op spread, still without a repeatable advantage
over fixed 256. Hot fork means differed substantially (4.865 and 3.866 ns/op), illustrating
why one short run or one inlining decision is insufficient.

Other important counterexamples:

- On 512-field hosts, member-write size 512 produced an 8,239-byte method. Hot writes took
  16.894 ns/op versus 4.845 at size 256, despite a small gain for spread access. Size 16
  lost on spread writes (20.110 versus 16.529 ns/op).
- Reducing only the type-read size from 256 to 16 did not give a repeatable improvement.
- A blanket constructor budget of 64 helped 16 overloads (17.467 → 7.814 ns/op spread)
  but hurt 32 overloads (10.572 → 21.687). Budget 128 did not resolve that tradeoff.
  The automatic policy instead selected budget 15 for 16 overloads and retained 256 for
  32, while choosing type-constructor size 7. This avoided the large regression in these
  fixtures, but is not yet evidence for arbitrary primitive or user-defined constructors.

### Inlining evidence

Final C2 compilation logs showed the following caller-specific decisions:

| Model/policy | Call | Bytecode bytes | Decision |
| --- | --- | ---: | --- |
| Balanced/fixed | benchmark → shared `Reader.get` | 2544 | `hot method too big` |
| Balanced/auto | benchmark → shared `Reader.get` | 207 | `inline (hot)` |
| Balanced/auto | shared reader → `get$0` | 203 | `inline (hot)` |
| Balanced/auto | `get$0` → host read dispatcher | 199 | `inline (hot)` |
| Balanced/auto | host dispatcher → read helper | 201 | `inline (hot)` |
| Large/fixed | shared reader → type helper | 3376 | `hot method too big` |
| Large/auto | benchmark → shared `Reader.get` | 307 | `inline (hot)` |
| Large/auto | shared reader → `get$0` | 307 | `already compiled into a medium method` |

Thus the math can keep individual methods below a bytecode target without guaranteeing
full-chain inlining, and full-chain inlining in a diagnostic fork does not establish a
normal-run throughput win. Compiler scheduling/profiles, compiled machine-code size,
call-site hotness, and caller budgets remain relevant. Diagnostic timing is not used above.

### Data and reproduction

- [Timing measurements](results/2026-10-29-timings.csv): 227 parameter cells across 299
  forks, including screening and diagnostic cells explicitly labelled as such. Includes
  source revisions, effective chunk sizes, fork means and every measurement sample.
- [Inlining decisions](results/2026-10-29-inlining.csv): latest installed C2 JMH loop
  compilation decisions, with caller, callee, bytecode size, compiler ID and retirement
  status. Host examples retain the first model type to keep the table manageable.
- Complete local logs, JMH JSON and bytecode reports remain under
  `performance/.results/<run>/`; each CSV row identifies its run directory.

The fixed-size work used commit `199080c`; the automatic-policy comparison used `3a17e05`.
Reproduce the policy comparison from the repository root:

```bash
performance/benchmark-aot/run-benchmarks.sh --cores 2,4 -- \
  -p model=balanced,large,members -p chunkPolicy=fixed,auto -p accessPattern=hot,spread \
  -e '.*(write|construct)' -f 2 -wi 6 -i 5 -w 1s -r 500ms -t 1 \
  -jvmArgs '-Xms2g -Xmx2g'
```

For a future production policy, consume resolved member metadata rather than assuming
uniform reference fields; account for primitive conversions and constructor argument
costs, consider cheap power-of-two divisors, and validate actual emitted method sizes.
Keep explicit sizes as exact overrides. Promote an automatic default only after checking
those cases and representative application access patterns, not merely satisfying a
single JVM's inline-size threshold.
