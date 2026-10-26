"""Build 666 delivery from real native reports and a freshly assembled APK."""
import argparse, hashlib, json, os, re, shutil, subprocess, zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/'android-v6323'
EVIDENCE=ROOT/'apk-evidence'
OUT=ROOT/'apk-delivery'
BASE_COMMIT='df7d420e90b47989e016bc1b0929c69a0848c20b'
BASE_SHA='22b2c018fcb56a6c0ea52aad6acaa162a6daa4ad10b7a1131831e00407831066'
PACKAGE='tr.borsatakip.v5.diagnostic.v666.work'
PREFIX='BIST-VIOP-V6.3.36-BUILD666'

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def identity():
    commit=os.environ.get('BIST_BUILD_COMMIT') or os.environ.get('GITHUB_SHA','')
    assert re.fullmatch('[0-9a-f]{40}',commit),'A real source commit is required'
    return {'commit':commit,'version':'6.3.36','build':666,'package':PACKAGE,
            'run_id':os.environ.get('GITHUB_RUN_ID'),
            'executor':'github-actions' if os.environ.get('GITHUB_ACTIONS')=='true' else 'local',
            'source_base_sha256':BASE_SHA,
            'default_backend_url':os.environ.get('BORSA_BACKEND_URL','')}

def prepare():
    p=APP/'app/src/main/assets/build-provenance.json'
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(identity(),sort_keys=True)+'\n',encoding='utf-8')
    print('Build provenance prepared for '+identity()['commit'])

def test_results():
    EVIDENCE.mkdir(exist_ok=True)
    totals=dict(tests=0,failures=0,errors=0,skipped=0)
    reports=sorted((APP/'app/build/test-results/testDiagnosticUnitTest').glob('TEST-*.xml'))
    assert reports,'Fresh JVM reports are missing'
    for p in reports:
        suite=ET.parse(p).getroot()
        for k in totals:totals[k]+=int(suite.get(k,0))
    assert totals['tests']>=983,totals
    assert totals['failures']==totals['errors']==totals['skipped']==0,totals
    for required in ['BackendHttpTransportTest','TrendUiPolicyTest','ViopV6SafetyGateTest','ViopQuoteDisplayPolicyTest','ViopDashboardPolicyTest','LeasedBistJobTest']:
        assert any(required in p.name for p in reports),required
    lint_path=APP/'app/build/reports/lint-results-diagnostic.xml'
    issues=ET.parse(lint_path).getroot().findall('issue')
    lint={'errors':sum(i.get('severity') in ('Error','Fatal') for i in issues),
          'warnings':sum(i.get('severity')=='Warning' for i in issues)}
    assert lint['errors']==0,lint
    classes=list((APP/'app/build/tmp/kotlin-classes/diagnosticAndroidTest').rglob('*.class'))
    assert any(p.name=='ConnectionLifecycleInstrumentedTest.class' for p in classes),'Android test sources did not compile'
    gateway=(EVIDENCE/'gateway-tests.tap').read_text()
    assert re.search(r'# pass 64\b',gateway) and re.search(r'# fail 0\b',gateway)
    assert re.search(r'# skipped 0\b',gateway)
    websocket=(EVIDENCE/'websocket-tests.log').read_text()
    assert re.search(r'\b1 pass\b',websocket) and re.search(r'\b0 fail\b',websocket)
    result={'jvm':totals,'jvmSuites':len(reports),'lint':lint,
            'compiledAndroidTestClasses':len(classes),'androidTestsExecuted':False,
            'gateway':{'passed':64,'failed':0},'localWebSocket':{'passed':1,'failed':0},
            'physicalDevice':'NOT_TESTED','liveProvider':'NOT_VERIFIED'}
    (EVIDENCE/'test-summary.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))
    return result

def run_tool(args,output):
    result=subprocess.run([str(a) for a in args],text=True,capture_output=True)
    (OUT/output).write_text(result.stdout+result.stderr,encoding='utf-8')
    assert result.returncode==0,output
    return result.stdout

def package():
    result=test_results()
    OUT.mkdir(exist_ok=True)
    original=APP/'app/build/outputs/apk/diagnostic/app-diagnostic.apk'
    assert original.is_file() and original.stat().st_size>1_000_000,'New APK is missing'
    apk=OUT/(PREFIX+'-DIAGNOSTIC.apk')
    shutil.copyfile(original,apk)
    sdk=Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])/'build-tools/35.0.0'
    signature=run_tool([sdk/'apksigner','verify','--verbose','--print-certs',apk],'APK-SIGNATURE.txt')
    assert 'Android Debug' in signature,'Expected explicit diagnostic/debug signing'
    badging=run_tool([sdk/'aapt','dump','badging',apk],'APK-METADATA.txt')
    assert "name='"+PACKAGE+"'" in badging
    assert "versionCode='666'" in badging and "versionName='6.3.36'" in badging
    run_tool([sdk/'zipalign','-c','-P','16','-v','4',apk],'APK-ALIGNMENT.txt')
    with zipfile.ZipFile(apk) as z:
        assert z.testzip() is None,'APK CRC failed'
        assert json.loads(z.read('assets/build-provenance.json'))==identity(),'Embedded source identity mismatch'
        dex=[n for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n)]
        assert dex and all(z.read(n).startswith(b'dex\n0') for n in dex),'DEX data is missing'
        assert 'AndroidManifest.xml' in z.namelist() and 'resources.arsc' in z.namelist()
    paths=json.loads((ROOT/'ci/apk666-source-files.json').read_text())
    assert len(paths)==len(set(paths))
    source_manifest={}
    source=OUT/(PREFIX+'-KAYNAK.zip')
    with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for relative in sorted(paths):
            p=ROOT/relative
            assert p.is_file() and '..' not in Path(relative).parts
            assert p.suffix not in ['.apk','.aab','.keystore','.jks'],relative
            source_manifest[relative]=digest(p);z.write(p,relative)
        z.writestr('SOURCE-SHA256.json',json.dumps(source_manifest,indent=2)+'\n')
    with zipfile.ZipFile(source) as z:
        assert z.testzip() is None
        assert all(hashlib.sha256(z.read(n)).hexdigest()==h for n,h in source_manifest.items())
    evidence_zip=OUT/(PREFIX+'-TEST-KANITLARI.zip')
    with zipfile.ZipFile(evidence_zip,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in sorted(EVIDENCE.rglob('*')):
            if p.is_file():z.write(p,'RUN/'+p.relative_to(EVIDENCE).as_posix())
        for p in sorted((APP/'app/build/test-results/testDiagnosticUnitTest').glob('*.xml')):
            z.write(p,'JUNIT/'+p.name)
        for p in sorted((APP/'app/build/reports').glob('lint-results-diagnostic.*')):
            z.write(p,'LINT/'+p.name)
    records=[]
    for p in [apk,source,evidence_zip]:
        records.append({'name':p.name,'sizeBytes':p.stat().st_size,'sha256':digest(p)})
    provenance={
        'SOURCE_BASE_SHA256':BASE_SHA,'GIT_COMMIT_SHA_START':BASE_COMMIT,
        'GIT_COMMIT_SHA_FINAL':identity()['commit'],'repository':'ferittasbek-png/BIST-VIOP-V6.2.2',
        'branch':'build/apk666-verified','buildIdentity':identity(),'results':result,
        'artifacts':records,'apkVerification':{'signature':'PASS - debug/diagnostic','package':'PASS','alignment':'PASS','zipCrc':'PASS','dex':'PASS','embeddedSourceCommit':'PASS'},
        'sourceFileCount':len(source_manifest),'sourceFiles':source_manifest,
        'limitations':['Physical device and Android instrumentation execution not performed','Licensed live provider not verified','Lint warnings remain','Separate app identity preserves previous installed app data'],
        'githubActionsRunId':os.environ.get('GITHUB_RUN_ID'),
        'githubActionsConclusion':'This local delivery does not assert Actions success' if identity()['executor']=='local' else 'Artifact produced after all workflow checks'
    }
    (OUT/'PROVENANCE.json').write_text(json.dumps(provenance,indent=2,ensure_ascii=False)+'\n')
    for name in ['APK-BUILD-PROVENANCE.json']:
        shutil.copyfile(APP/'app/src/main/assets/build-provenance.json',OUT/name)
    change=(ROOT/'BUILD666-CHANGE-NOTES.md').read_text()
    (OUT/'CHANGE_REPORT.md').write_text(change,encoding='utf-8')
    j=result['jvm'];l=result['lint'];apk_hash=records[0]['sha256']
    report=f"""# Build 666 APK doğrulama raporu

1. Başlangıç kaynak: Build 665; SHA-256 {BASE_SHA}; başlangıç commit {BASE_COMMIT}.
2. Son kaynak commit: {identity()['commit']}; dal build/apk666-verified; sürüm 6.3.36 / 666.
3. GitHub'a kaydedilen değişiklikler: sürüm/paket kimliği, Wrapper checksum, APK CI ve doğrulama/raporlama araçları.
4. Kaynak değişiklikleri: CHANGE_REPORT.md içinde. Önceki işlevsel hata düzeltmeleri korunur.
5. Gerçek yeni JVM sonuçları: {j['tests']} test, {j['failures']} başarısız, {j['errors']} hata, {j['skipped']} atlanan. Lint: {l['errors']} hata/fatal, {l['warnings']} uyarı. Android test class: {result['compiledAndroidTestClasses']} derlendi; cihazda yürütülmedi. Gateway 64/64; gerçek yerel WebSocket 1/1.
6. APK: {apk.name}; {apk.stat().st_size} bayt; SHA-256 {apk_hash}. Manifest, DEX, ZIP CRC, 16 KB ZIP hizalama ve gömülü kaynak commit kontrolü geçti.
7. İmzalama: apksigner doğrulandı; Android Debug tanılama imzası. Üretim/release imzası değildir. Paket {PACKAGE}; önceki uygulamanın yanına kurulur.
8. Yürütme ortamı: {identity()['executor']}. GitHub Actions sonucu ayrı doğrulanır; yerel sonuçlar Actions başarısı olarak sunulmaz.
9. Teslim: APK, kaynak ZIP, test kanıtı ZIP, CHANGE_REPORT.md, BUILD_REPORT.md, PROVENANCE_REPORT.md, PROVENANCE.json ve SHA256SUMS.txt.
10. Açık kalanlar: fiziksel cihaz/emülatör instrumentation çalışması, lisanslı canlı sağlayıcı, Lint uyarıları. Canlı veri bağlı veya üretime hazır sonucu verilmez.
"""
    (OUT/'BUILD_REPORT.md').write_text(report,encoding='utf-8')
    (OUT/'PROVENANCE_REPORT.md').write_text('# Kaynak ve paket kimliği\n\n'+report,encoding='utf-8')
    with (OUT/'SHA256SUMS.txt').open('w') as f:
        for p in sorted(OUT.iterdir()):
            if p.is_file() and p.name!='SHA256SUMS.txt':f.write(digest(p)+'  '+p.name+'\n')
    print(json.dumps({'apk':records[0],'source':records[1],'tests':records[2],'results':result},ensure_ascii=False))

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('step',choices=['prepare','tests','package'])
    args=parser.parse_args()
    {'prepare':prepare,'tests':test_results,'package':package}[args.step]()

