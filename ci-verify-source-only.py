"""Verify complete JVM reports and absence of newly built Android packages."""
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
evidence = root / 'source-checks'
evidence.mkdir(exist_ok=True)
packages = sorted(str(p.relative_to(root)) for suffix in ('*.apk', '*.aab') for p in root.rglob(suffix))
(evidence / 'apk-absence.json').write_text(json.dumps({
    'commit': os.environ.get('GITHUB_SHA'), 'apk_or_aab_count': len(packages), 'packages': packages
}, indent=2) + '\n')
assert not packages, f'Source-only validation produced Android packages: {packages}'

reports = sorted((root / 'android-v6323/app/build/test-results/testDiagnosticUnitTest').glob('TEST-*.xml'))
assert reports, 'JVM test reports missing'
counts = dict(tests=0, failures=0, errors=0, skipped=0)
for report in reports:
    suite = ET.parse(report).getroot()
    for key in counts:
        counts[key] += int(suite.attrib.get(key, 0))
assert counts['tests'] >= 983, f'Expected all 983 baseline and regression test declarations: {counts}'
required = ('BackendHttpDiagnosticTest', 'LeasedBistJobTest', 'BackendHttpTransportTest',
            'ProviderProbeGateTest', 'BackendUrlPolicyTest', 'ObservationSourcePolicyTest',
            'AutoScanCompletionTest', 'ReportRedactorTest', 'ViopDashboardPolicyTest',
            'DeepAuditShortExcursionTest', 'DeepAuditJsonRedactionTest', 'BackupExclusionPolicyTest', 'ViopQuoteDisplayPolicyTest', 'TrendUiPolicyTest', 'ViopV6SafetyGateTest')
for name in required:
    assert any(name in str(p) for p in reports), f'Regression test report missing: {name}'
assert counts['failures'] == counts['errors'] == counts['skipped'] == 0, counts
lint_path = root / 'android-v6323/app/build/reports/lint-results-diagnostic.xml'
assert lint_path.is_file(), 'Android Lint XML report missing'
lint_issues = ET.parse(lint_path).getroot().findall('issue')
lint = {
    'errors': sum(issue.get('severity') in ('Error', 'Fatal') for issue in lint_issues),
    'warnings': sum(issue.get('severity') == 'Warning' for issue in lint_issues),
}
assert lint['errors'] == 0, lint
instrumentation_classes = sorted((root / 'android-v6323/app/build/tmp/kotlin-classes/diagnosticAndroidTest').rglob('*.class'))
assert instrumentation_classes, 'Compiled Android test classes missing'
assert any(p.name == 'ConnectionLifecycleInstrumentedTest.class' for p in instrumentation_classes), 'New connection lifecycle device test did not compile'
summary = {
    'commit': os.environ.get('GITHUB_SHA'), 'jvm': counts,
    'lint': lint, 'compiled_android_test_classes': len(instrumentation_classes),
    'instrumentation': 'compiled only; NOT executed because APK creation is prohibited',
    'apk_produced': False
}
(evidence / 'test-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary))
