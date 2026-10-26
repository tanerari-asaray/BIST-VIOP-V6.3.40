"""Verify actual Build 668 reports. Never report an unexecuted test as passed."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
import zipfile

root = Path(__file__).resolve().parents[1]
app = root / 'android-v6323' / 'app'
reports = list((app / 'build/test-results/testDiagnosticUnitTest').glob('TEST-*.xml'))
assert reports, 'No JUnit XML: native tests have not completed'
suites = [ET.parse(p).getroot() for p in reports]
counts = {k: sum(int(s.get(k, '0')) for s in suites) for k in ('tests', 'failures', 'errors', 'skipped')}
assert counts['tests'] >= 1000, counts
assert counts['failures'] == counts['errors'] == counts['skipped'] == 0, counts
assert any('ReadableStockAnalysisTest' in p.name for p in reports), 'New model tests absent'
assert any('Build668ScreenContractTest' in p.name for p in reports), 'New source contract tests absent'
compiled = app / 'build/tmp/kotlin-classes/diagnosticAndroidTest'
assert list(compiled.rglob('Build668ChartRuntimeTest.class')), 'Android instrumentation was not compiled'
lint = app / 'build/reports/lint-results-diagnostic.xml'
assert lint.is_file(), 'Lint was not executed'
assert not [i for i in ET.parse(lint).getroot().findall('issue') if i.get('severity') in ('Error', 'Fatal')]
apk = app / 'build/outputs/apk/diagnostic/app-diagnostic.apk'
assert apk.is_file() and apk.stat().st_size > 1_000_000
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    assert any(re.fullmatch(r'classes\d*\.dex', p) for p in z.namelist())
print('Build668 TESTS:', counts)
print('APK ZIP/DEX: PASS. Instrumentation only compiled, not executed. Physical install NOT VERIFIED.')
