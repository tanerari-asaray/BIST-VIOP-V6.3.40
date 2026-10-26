"""Bounded native test evidence, including failed runs. Does not package Android apps."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
counts = dict(tests=0, failures=0, errors=0, skipped=0)
failed = []
reports = sorted((root / "android-v6323/app/build/test-results/testDiagnosticUnitTest").glob("TEST-*.xml"))
for path in reports:
    suite = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(suite.attrib.get(key, 0))
    for case in suite.findall("testcase"):
        for node in list(case.findall("failure")) + list(case.findall("error")):
            failed.append(dict(className=case.get("classname"), testName=case.get("name"),
                               message=node.get("message", "")[:700]))
packages = sorted(str(p.relative_to(root)) for suffix in ("*.apk", "*.aab") for p in root.rglob(suffix))
result = dict(scope="NATIVE_JVM", reports=len(reports), counts=counts, failedTests=failed[:30],
              apkOrAabCount=len(packages), deviceExecution="NOT_PERFORMED")
print("DEEP_AUDIT_NATIVE_EVIDENCE=" + json.dumps(result, ensure_ascii=True))
target = root / "source-checks"
target.mkdir(exist_ok=True)
(target / "deep-audit-native.json").write_text(json.dumps(result, indent=2) + "\n")
assert not packages, "APK/AAB creation prohibited"
