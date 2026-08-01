# Quality gates and dependency trust

Every pull request must pass the repository-owned Gradle Wrapper on Linux. Pushes
to `main` additionally run the same gate on Windows. The checks use only
GitHub-hosted runners and do not require secrets or paid services. The gate covers
all current modules, including the deterministic domain, protocol, map validator,
identity code, and fixed-tick dedicated-server runtime.

## Local commands

Run the complete gate:

```bash
./gradlew check
```

Apply formatting:

```bash
./gradlew spotlessApply
```

Inspect individual reports under each module:

- `build/reports/tests/` — JUnit;
- `build/reports/jacoco/test/html/` and `jacocoTestReport.xml` — instruction and
  branch coverage;
- `build/reports/spotbugs/` — SpotBugs XML and HTML.

## Enforced checks

- Java 21 compilation with `-Xlint:all -Werror`;
- Spotless 8.9.0 with google-java-format 1.35.0 AOSP style;
- SpotBugs Gradle plugin 6.5.9 at maximum effort and low confidence threshold;
- JaCoCo 0.8.15 reports for every Java module;
- renderer-dependency boundary checks;
- production-source policy rejecting `System.out`, `System.err`,
  `printStackTrace`, and Java native object serialization;
- strict dependency locking for every resolvable configuration;
- SHA-256 dependency verification metadata maintained by Gradle;
- wrapper validation before any build step.

A suppression or exclusion must name the exact detector/rule, explain why the
finding is a false positive or accepted design, and include the smallest possible
scope. Disabling a tool globally to make CI green is not acceptable.

## Dependency maintenance

Dependency versions are pinned in `gradle/libs.versions.toml`. Lockfiles freeze
the full resolved graph, while `gradle/verification-metadata.xml` records accepted
artifact SHA-256 values. A changed dependency is reviewable only when its version,
lock state, and verification metadata change together.

Regenerate intentionally:

```bash
./gradlew --write-locks --write-verification-metadata sha256 resolveAndLockAll
./gradlew check
```

`resolveAndLockAll` is a maintenance-only task and deliberately does not support
the configuration cache. Review the diff before committing; never accept an
unexpected repository, component, artifact, or checksum merely because Gradle
generated it.

## Coverage policy

JaCoCo reports are evidence, not a target to game. The initial gate publishes
instruction and branch coverage without imposing one global percentage on empty
adapter modules. Domain changes still require direct unit tests and every
regression fix requires a test that would have failed before the fix. Module-level
thresholds will be introduced when each module has a meaningful executable base.
