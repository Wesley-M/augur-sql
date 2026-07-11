# Benchmarks

M6 adds a synthetic latency guard for the large-catalog completion path.

Run it locally:

```bash
./gradlew runBenchmarks
```

The benchmark builds a 5,000-table / 60,000-column catalog and measures:

- table prefix completion
- join-target table completion
- scoped qualified-column completion
- insert scaffold completion

It reports min, p50, p95, p99, and max latency per case. CI runs the benchmark
after `check`.

The default p99 guard is intentionally conservative for shared CI machines:
500 ms. Override it locally or in release validation:

```bash
./gradlew runBenchmarks -Daugur.benchmark.maxP99Millis=50
```

The benchmark is not a JMH replacement. It is a regression tripwire for the
library invariant that completion remains fast on large immutable snapshots.
