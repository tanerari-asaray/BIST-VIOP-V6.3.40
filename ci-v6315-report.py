import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

root = Path.cwd()
delivery = root.parent / 'delivery'
base_sha = '6a1a756b1f16e85bb2ac2325c750775f83d4fe886d4293d3e0afff88a1e74251'
start = 'c776538c4a30bf358cfd88f56352efa9782bb271'
apk_records = []
for apk in root.glob('app/build/outputs/apk/diagnostic/*.apk'):
    dest = delivery / 'BIST-VIOP-V6.3.15-BUILD645-DIAGNOSTIC.apk'
    shutil.copy2(apk, dest)
    verify = subprocess.run([os.environ['ANDROID_HOME'] + '/build-tools/35.0.0/apksigner', 'verify', '--verbose', '--print-certs', str(dest)], capture_output=True, text=True)
    (delivery / 'APK-SIGNATURE.txt').write_text(verify.stdout + verify.stderr)
    if verify.returncode:
        raise RuntimeError('APK signature verification failed')
    apk_records.append({'name': dest.name, 'bytes': dest.stat().st_size, 'sha256': hashlib.sha256(dest.read_bytes()).hexdigest(), 'signing': 'Android debug certificate; diagnostic build; apksigner verification passed'})

source = delivery / 'BIST-VIOP-V6.3.15-BUILD645-BUILT-SOURCE.zip'
with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(root.rglob('*')):
        rel = path.relative_to(root)
        if path.is_file() and not any(part in {'build', '.gradle', '.kotlin'} for part in rel.parts) and path.name != 'local.properties':
            archive.write(path, rel)

record = {
    'SOURCE_BASE_SHA256': base_sha, 'GIT_COMMIT_SHA_START': start,
    'GIT_COMMIT_SHA_FINAL': os.environ['GITHUB_SHA'],
    'repository': os.environ['GITHUB_REPOSITORY'], 'branch': os.environ['GITHUB_REF_NAME'],
    'run_id': os.environ['GITHUB_RUN_ID'],
    'run_url': 'https://github.com/' + os.environ['GITHUB_REPOSITORY'] + '/actions/runs/' + os.environ['GITHUB_RUN_ID'],
    'assembleDiagnostic': os.environ['BUILD_RESULT'], 'testDiagnosticUnitTest': os.environ['TEST_RESULT'],
    'apk': apk_records, 'limitations': ['No device or emulator tests performed', 'No production release signing used', 'Live provider readiness and financial performance not validated']
}
(delivery / 'PROVENANCE_REPORT.md').write_text('# Build provenance\n\n```json\n' + json.dumps(record, indent=2) + '\n```\n')
(delivery / 'BUILD_REPORT.md').write_text('# Build results\n\nAssemble: ' + os.environ['BUILD_RESULT'] + '\n\nUnit tests: ' + os.environ['TEST_RESULT'] + '\n\nSee test report artifact and signature verification. Device tests were not run.\n')
(delivery / 'CHANGE_REPORT.md').write_text('# Changes\n\nUser-supplied V6.3.15 visual source imported without application feature changes into android-v6315/. Dedicated branch CI added for compilation, unit tests, APK signature verification, source archive and evidence.\n')
with (delivery / 'SHA256SUMS.txt').open('w') as out:
    out.write(base_sha + '  ORIGINAL-UPLOADED-SOURCE.zip\n')
    for file in sorted(delivery.iterdir()):
        if file.is_file() and file.name != 'SHA256SUMS.txt':
            out.write(hashlib.sha256(file.read_bytes()).hexdigest() + '  ' + file.name + '\n')
if os.environ['BUILD_RESULT'] != 'success' or not apk_records:
    raise RuntimeError('APK build did not succeed')
