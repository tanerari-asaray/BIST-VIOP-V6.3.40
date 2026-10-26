"""Fail before the delivery build if test suites are missing, failed or skipped."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

root = Path.cwd()
groups = {
    "unit": ("app/build/test-results/**/*.xml", 879),
    "android": ("app/build/outputs/androidTest-results/**/*.xml", 52),
}
summary = {}
for label, (pattern, minimum) in groups.items():
    files = list(root.glob(pattern))
    assert files, f"Missing {label} XML reports"
    count = 0
    for path in files:
        suite = ET.parse(path).getroot()
        assert suite.tag == "testsuite", f"Unexpected test report format: {path}"
        assert suite.get("tests") is not None, f"Missing test count: {path}"
        count += int(suite.get("tests"))
        for attr in ("failures", "errors", "skipped"):
            assert int(suite.get(attr, "0")) == 0, f"{attr} in {path}"
        assert not suite.findall(".//failure") and not suite.findall(".//error") and not suite.findall(".//skipped"), f"Unsuccessful testcase: {path}"
    assert count >= minimum, f"Missing {label} cases: {count} < {minimum}"
    summary[label] = {"tests": count, "failures": 0, "errors": 0, "skipped": 0}
print(json.dumps(summary, sort_keys=True))
