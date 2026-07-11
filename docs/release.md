# Release Checklist

M6 prepares the standalone repo for a first release. Do not publish from
connector-bridge; publish from this repo after the standalone checks pass.

## Preflight

```bash
./gradlew clean check runQuickstart runBenchmarks javadoc assemble
```

For stricter local performance validation:

```bash
./gradlew runBenchmarks -Daugur.benchmark.maxP99Millis=50
```

## Artifact Check

`assemble` should produce:

- `build/libs/augur-sql-<version>.jar`
- `build/libs/augur-sql-<version>-sources.jar`
- `build/libs/augur-sql-<version>-javadoc.jar`

To inspect the Maven publication locally:

```bash
./gradlew publishToMavenLocal
```

## Versioning

Update `version` in `build.gradle` from `0.1.0-SNAPSHOT` to the release version,
then run the preflight again.

## Tagging

Only tag after the release artifact check passes:

```bash
git tag v0.1.0
git push origin main v0.1.0
```

Connector Bridge integration remains M7. A release tag should not include
bridge-specific adapters or UI dependencies.
