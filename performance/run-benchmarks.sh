#!/usr/bin/env bash
#
# SPDX-License-Identifier: Apache-2.0
# Copyright: Red Hat Inc. and Hibernate Authors
#
# Entrypoint for the hibernate-accessor JMH benchmark suites.
#
# The `performance/` area holds two very different benchmark suites that nonetheless share
# one system-config/profiling engine (CPU-core pinning, async-profiler, perf panic, the
# JMH-JSON comparison table). This entrypoint picks a suite with `--suite`, sources the shared
# engine (scripts/lib-bench-common.sh), then hands the remaining arguments to the matching
# per-suite driver (scripts/suite-<suite>.sh):
#
#   basic  (default)  fixed, hand-authored micro-benchmarks   -> :hibernate-accessor-benchmark-basic
#   model             build-time generated large-model suite   -> :hibernate-accessor-benchmark-model
#
# Each suite has its own axes and its own `--help`; run e.g. `./run-benchmarks.sh --suite model --help`.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# The entrypoint now lives at the root of performance/, so that dir IS PERF_DIR.
PERF_DIR="${SCRIPT_DIR}"
ROOT_DIR="$(cd "${PERF_DIR}/.." && pwd)"
export PERF_DIR ROOT_DIR

usage() {
	cat <<'EOF'
Usage: run-benchmarks.sh [--suite basic|model] [suite options] [benchmark-pattern ...]

Builds and runs one of the hibernate-accessor JMH benchmark suites, pinned to P-cores and,
optionally, profiled with async-profiler. All suites write into performance/.results/.

Suites:
  basic  (default)  Fixed, hand-authored micro-benchmarks (Read/Bulk/Cascade/Megamorphic, ...).
                    Strategies compared across the core/asm/bytebuddy jars.
  model             Generated large-model suite: throughput (GeneratedGraphBenchmark),
                    cold build cost (AccessorBuildBenchmark) and footprint (FootprintReport),
                    swept over the width x count x depth model matrix.

Options:
  --suite <name>    basic | model                                          (default: basic)
  -h, --help        this message; combine with --suite for the suite's own options

Per-suite help:
  ./run-benchmarks.sh --suite basic --help
  ./run-benchmarks.sh --suite model --help

Examples:
  ./run-benchmarks.sh --suite basic --all --quick ReadBenchmark
  ./run-benchmarks.sh --suite model --quick --models e8_f16_d2
EOF
}

# First pass: peel off --suite (and remember a bare --help), forward everything else verbatim.
SUITE=""
HELP_REQUESTED="false"
declare -a REST=()
while [[ $# -gt 0 ]]; do
	case "$1" in
		--suite)   SUITE="${2:-}"; shift 2 ;;
		--suite=*) SUITE="${1#*=}"; shift ;;
		-h|--help) HELP_REQUESTED="true"; REST+=("$1"); shift ;;
		*)         REST+=("$1"); shift ;;
	esac
done

# A bare `--help` with no suite prints this top-level usage; with a suite it falls through so
# the suite driver can print its own.
if [[ "${HELP_REQUESTED}" == "true" && -z "${SUITE}" ]]; then
	usage
	exit 0
fi

: "${SUITE:=basic}"

case "${SUITE}" in
	basic|model) ;;
	*) printf '\033[0;31m[run-benchmarks]\033[0m Unknown --suite: %s (basic | model)\n' "${SUITE}" >&2; exit 1 ;;
esac

# shellcheck source=scripts/lib-bench-common.sh
source "${SCRIPT_DIR}/scripts/lib-bench-common.sh"
# shellcheck source=/dev/null
source "${SCRIPT_DIR}/scripts/suite-${SUITE}.sh"

run_suite "${REST[@]}"
