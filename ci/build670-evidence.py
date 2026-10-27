"""Checks real runner output. A compiled APK or old embedded report is never test evidence."""
import hashlib,json,os,re,sys,xml.etree.ElementTree as ET
from pathlib import Path
mode=sys.argv[1]
if mode=='jvm':
    reports=list(Path('android-v6323/app/build/test-results/testDiagnosticUnitTest').glob('TEST-*.xml'))
    assert reports,'No JVM reports'
    suites=[ET.parse(p).getroot() for p in reports]
    count=sum(int(s.get('tests','0')) for s in suites)
    bad=sum(int(s.get('failures','0'))+int(s.get('errors','0'))+int(s.get('skipped','0')) for s in suites)
    print(json.dumps({'jvm_tests':count,'failures_errors_skipped':bad}))
    assert count>0 and bad==0
elif mode=='device':
    text=Path('build670-device/instrumentation.txt').read_text()
    matched=re.search(r'OK \((\d+) tests?\)',text)
    passed = bool(matched and int(matched[1]) >= 7 and not any(
        x in text for x in ['FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed', 'INSTRUMENTATION_ABORTED']))
    result = {'passed': int(matched[1]) if matched else 0,
              'status': 'PASS' if passed else 'FAIL', 'commit': os.getenv('GITHUB_SHA')}
    Path('build670-device/result.json').write_text(json.dumps(result))
    if not passed:
        raise SystemExit('Device tests failed; inspect instrumentation.txt, logcat.txt, window.txt and screens.tar')
elif mode=='package':
    root=Path('build670-tested'); files=list(root.iterdir())
    data={p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files if p.is_file()}
    data.update({'commit':os.getenv('GITHUB_SHA'),'repository':os.getenv('GITHUB_REPOSITORY'),'run_id':os.getenv('GITHUB_RUN_ID'),'signing':'Diagnostic debug signer. Not a production release; in-place update signer compatibility not established.'})
    (root/'PROVENANCE_REPORT.json').write_text(json.dumps(data,ensure_ascii=False,indent=2))
    (root/'BUILD_REPORT.md').write_text('# Tested diagnostic\n\nNative tests/lint and all four Android 15 matrix jobs passed as dependencies of this packaging job.\n\nNo licensed live-provider or physical-device certification is claimed.\n')
    for name in ['CHANGE_REPORT.md','PROVENANCE_REPORT.md']:
        (root/name).write_bytes(Path(name).read_bytes())
else: raise SystemExit('unknown mode')
