#!/usr/bin/env bash
set -euo pipefail
cd /workspace
mkdir -p source-checks
node --version
bun --version
java -version
python3 --version
python3 server/build_railway_bundle.py --check
node --check server/gateway_diagnostics.mjs
node --check server/railway_function_bundle.ts
node --test --test-reporter=tap server/tests/*.test.mjs | tee source-checks/gateway-tests.tap
bun test server/tests/websocket.bun.test.ts 2>&1 | tee source-checks/websocket-tests.log
cd android-v6323
chmod +x gradlew
if ./gradlew --no-daemon -I ../ci-source-only.init.gradle :app:assembleDiagnostic --dry-run > ../source-checks/apk-guard.log 2>&1; then
  echo 'APK task guard did not reject packaging'
  exit 1
fi
grep -q SOURCE_ONLY_APK_BLOCKED ../source-checks/apk-guard.log
set +e
./gradlew --no-daemon --stacktrace --continue --max-workers=2 \
  '-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m' \
  -I ../ci-source-only.init.gradle \
  :app:testDiagnosticUnitTest \
  :app:compileDiagnosticAndroidTestKotlin \
  :app:processDiagnosticResources \
  :app:lintDiagnostic | tee ../source-checks/gradle-validation.log
gradle_exit=${PIPESTATUS[0]}
set -e
python3 ../ci/deep-audit-jvm-report.py
if [ "$gradle_exit" -ne 0 ]; then
  exit "$gradle_exit"
fi
python3 ../ci-verify-source-only.py
echo 'SOURCE_ONLY_VALIDATION_PASSED'
