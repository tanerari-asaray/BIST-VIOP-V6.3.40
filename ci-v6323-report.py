import hashlib,json,os,subprocess,zipfile,datetime,xml.etree.ElementTree as ET
from pathlib import Path
root=Path.cwd();repo=root.parent;out=repo/'delivery';out.mkdir(exist_ok=True)
sha=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
assert sha==os.environ['GITHUB_SHA'],'Checked out commit differs from Actions SHA'
records=[]
checks_passed = all(os.environ.get(k)=='success' for k in ('TEST_RESULT','EMULATOR_RESULT','GATEWAY_RESULT','STREAM_RESULT','BUILD_RESULT'))
for apk in (root.glob('app/build/outputs/apk/diagnostic/*.apk') if checks_passed else []):
    dest=out/'BIST-VIOP-V6.3.35-BUILD665-INTEGRITY-DIAGNOSTIC.apk';dest.write_bytes(apk.read_bytes())
    sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
    cert=subprocess.run([str(sdk/'apksigner'),'verify','--verbose','--print-certs',str(dest)],text=True,capture_output=True)
    (out/'APK-SIGNATURE.txt').write_text(cert.stdout+cert.stderr);assert cert.returncode==0
    badging=subprocess.check_output([str(sdk/'aapt'),'dump','badging',str(dest)],text=True)
    (out/'APK-METADATA.txt').write_text(badging)
    assert "versionCode='665'" in badging and "versionName='6.3.35'" in badging
    assert "name='tr.borsatakip.v5.diagnostic.v665.integrity'" in badging
    with zipfile.ZipFile(dest) as z: assert z.read('assets/build-provenance.json').decode().strip()==json.dumps({'commit':sha,'run_id':os.environ['GITHUB_RUN_ID']},sort_keys=True)
    records.append({'name':dest.name,'bytes':dest.stat().st_size,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'certificate':'See APK-SIGNATURE.txt; debug signing, separate package'})
source=out/'BIST-VIOP-BUILD665-INTEGRITY-SOURCE.zip'
subprocess.run(['git','archive','--format=zip','-o',str(source),sha,'android-v6323','server','.github/workflows/build-v6323.yml','ci-v6323-report.py','ci-verify-screenshots.py','ci-verify-test-results.py','ci-run-emulator.sh','BUILD665-CHANGE-NOTES.md'],check=True,cwd=repo)
xmls=list(root.glob('app/build/test-results/**/*.xml'))+list(root.glob('app/build/outputs/androidTest-results/**/*.xml'))
tests=[]
for p in xmls:
    try:
        x=ET.parse(p).getroot();tests.append({'path':str(p.relative_to(root)),'tests':x.get('tests'),'failures':x.get('failures'),'errors':x.get('errors'),'skipped':x.get('skipped')})
    except ET.ParseError: tests.append({'path':str(p),'parse_error':True})
record={'GIT_COMMIT_SHA_START':'143cb2ee8f0768437b759a5fdf1f8be2797e9d7c','GIT_COMMIT_SHA_FINAL':sha,'SOURCE_BASE_SHA256':'bcb36946d3bf70c8ff6cea7e6966ed20a5d04e83d719c4c141c56bea571e45a7','source_identity_note':'User-supplied Build 663 audit baseline, with later Build 664 changes from parent 143cb2ee preserved; full integrity fixes described in BUILD665-CHANGE-NOTES.md','built_source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'repository':os.environ['GITHUB_REPOSITORY'],'branch':os.environ['GITHUB_REF_NAME'],'run_id':os.environ['GITHUB_RUN_ID'],'run_url':f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",'report_time_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'assemble':os.environ.get('BUILD_RESULT'),'unit_tests':os.environ.get('TEST_RESULT'),'emulator':os.environ.get('EMULATOR_RESULT'),'gateway_tests':os.environ.get('GATEWAY_RESULT'),'websocket_tests':os.environ.get('STREAM_RESULT'),'tests':tests,'apk':records,'limitations':['Licensed live BIST/VIOP service and physical-device integration were not verified; public KAP/Yahoo connectivity was not independently verified for this run','Emulator UI fixtures are explicitly test data, not real market data','No physical-device test','Diagnostic debug signature; separate installation, old app data is preserved']}
(out/'PROVENANCE.json').write_text(json.dumps(record,indent=2,ensure_ascii=False))
(out/'BUILD_REPORT.md').write_text('# Build 665 verification\n\n```json\n'+json.dumps(record,indent=2,ensure_ascii=False)+'\n```\n')
(out/'CHANGE_REPORT.md').write_text((repo/'BUILD665-CHANGE-NOTES.md').read_text())
(out/'PROVENANCE_REPORT.md').write_text('# Build provenance\n\n```json\n'+json.dumps(record,indent=2,ensure_ascii=False)+'\n```\n')
with (out/'SHA256SUMS.txt').open('w') as f:
    for p in sorted(out.iterdir()):
        if p.is_file() and p.name!='SHA256SUMS.txt':f.write(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n')
if checks_passed:
    assert tests and sum(int(t.get('tests') or 0) for t in tests) >= 931, 'Missing test suites'
    assert all(not t.get('parse_error') and all(int(t.get(k) or 0)==0 for k in ('failures','errors','skipped')) for t in tests), 'Failed or skipped test cases'
assert records and checks_passed,'No successful new APK build after all checks'

