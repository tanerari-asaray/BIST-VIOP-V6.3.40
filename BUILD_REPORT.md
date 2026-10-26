# BUILD 670 audit correction — validation status

Local gateway regression: 67 tests passed, 0 failed (Node 24; mocked provider contracts).
Local source XML/YAML, shell syntax and source hash checks: see CI verification.
Android JVM, Lint and device tests: PENDING real GitHub Actions execution. Local Gradle distribution/SDK unavailable.
No APK or production signing claim is made in this source commit.
Runtime evidence is written by the actual CI job to its artifacts, separately from this source record.

First Actions run 40369740027 reached Kotlin compilation and failed on three redundant-condition warnings under -Werror. Those conditions were simplified; strict warning policy was preserved. JVM/lint/device remain pending the corrective run.

Second Actions run 40370630923: production Kotlin/Java compilation passed. 1061 JVM tests executed; 1060 passed and SessionVwapTest.dailySeriesDoesNotPretendToBeSessionVwap failed because its synthetic close exceeded its high. The fixture was corrected to valid OHLC; strict production rejection was retained. Two additional execution/availability-boundary regressions were added. Subsequent results belong to the new CI run.
