import json, hashlib, zipfile, os, sys, re
from pathlib import Path
root = Path(__file__).parent
orig = Path('/mnt/data/files/BIST-VIOP-V6.3.37-BUILD667-KAYNAK.zip')
assert hashlib.sha256(orig.read_bytes()).hexdigest()=='d7a38630f17ae1d16316dbcfde89b7db35dab27839fdc8d217f987d0275c2567'
target = root/'android-v6323/app/src/main/java/tr/borsatakip/v5/ui/FullscreenChartActivity.kt'
s = target.read_text()
analyzer=(root/'android-v6323/app/src/main/java/tr/borsatakip/v5/analysis/ReadableStockAnalysis.kt').read_text()
timeframes=(root/'android-v6323/app/src/main/java/tr/borsatakip/v5/analysis/ChartTimeframe.kt').read_text()
gradle=(root/'android-v6323/app/build.gradle.kts').read_text()
checks={
  'baseline_sha256_verified':True,
  'tradingview_first':s.index('TRADINGVIEW GRAFİĞİNİ AÇ')<s.index('notice = label')<s.index('val tabs = row()'),
  'tradingview_uses_official_bist_symbol_page':'tradingview.com/symbols/BIST-' in s,
  'viop_link_falls_back_to_search':'tradingview.com/search/?query=' in s,
  'symbol_checked_before_url':'matches(Regex("[A-Z0-9._-]{1,40}"))' in s,
  'all_four_trends':all(t in s for t in ['ChartTimeframe.FIVE_MIN','ChartTimeframe.TEN_MIN','ChartTimeframe.ONE_HOUR','ChartTimeframe.ONE_DAY']),
  'ten_minute_resampled_not_invented':'TEN_MIN("10 DK", 5, 30L * 24 * 60 * 60 * 1000, aggregateMinutes = 10)' in timeframes,
  'trend_requests_real_data':'withTimeout(35_000) { fetch(tf) }' in s,
  'no_data_suppressed':'Veri yok · Yorum yapılmadı' in s,
  'last_unverified_bar_excluded':'val closedCandidates = clean.dropLast(1)' in analyzer and 'val bars = state.candles.dropLast(1)' in s,
  'readable_panel_on_chart':'chartPage.addView(readableBottom)' in s,
  'readable_panel_on_details':'renderEverydayExplanation(details, state.candles)' in s,
  'common_language_questions':all(k in s for k in ['Fiyat nasıl hareket ediyor?', 'Hangi seviyeler önemli?', 'Bu yorum ne zaman değişir?', 'Teknik kelimeler ne anlama geliyor?']),
  'data_source_disclosed':'Kaynak: $source' in s,
  'not_investment_advice':'Bu bilgiler yatırım tavsiyesi ya da işlem emri değildir.' in s,
  'viop_leverage_warning':'VİOP sözleşmelerinde kaldıraç' in s,
  'no_sample_kayse_price':'2,65 TL' not in s and '2,65 TL' not in analyzer,
  'version_668':'versionCode = 668' in gradle and 'versionName = "6.3.38"' in gradle,
  'new_unit_tests_exist': (root/'android-v6323/app/src/test/java/tr/borsatakip/v5/analysis/ReadableStockAnalysisTest.kt').is_file(),
  'source_contract_tests_exist':(root/'android-v6323/app/src/test/java/tr/borsatakip/v5/analysis/Build668ScreenContractTest.kt').is_file(),
  'instrumentation_test_source_exists':(root/'android-v6323/app/src/androidTest/java/tr/borsatakip/v5/ui/Build668ChartRuntimeTest.kt').is_file(),
  'ci_quality_gate_available':(root/'ci/build668-quality.py').is_file(),
}
assert all(checks.values()), {k:v for k,v in checks.items() if not v}
base = {p:hashlib.sha256(b).hexdigest() for p,b in ((x, zipfile.ZipFile(orig).read(x)) for x in zipfile.ZipFile(orig).namelist())}
new={p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file() and p.name != 'modify_screen.py' and p.name != 'verify_source668.py' and '__pycache__' not in p.parts}
changed=[k for k in base.keys()&new.keys() if base[k]!=new[k]]
added=[k for k in new if k not in base]
removed=[k for k in base if k not in new]
allow_changed={'android-v6323/app/src/main/java/tr/borsatakip/v5/ui/FullscreenChartActivity.kt','android-v6323/app/src/main/java/tr/borsatakip/v5/analysis/ChartTimeframe.kt','android-v6323/app/build.gradle.kts'}
assert set(changed)==allow_changed,(changed,allow_changed)
assert not removed,removed
expected_add={'android-v6323/app/src/main/java/tr/borsatakip/v5/analysis/ReadableStockAnalysis.kt','android-v6323/app/src/test/java/tr/borsatakip/v5/analysis/ReadableStockAnalysisTest.kt','android-v6323/app/src/test/java/tr/borsatakip/v5/analysis/Build668ScreenContractTest.kt','android-v6323/app/src/androidTest/java/tr/borsatakip/v5/ui/Build668ChartRuntimeTest.kt','.github/workflows/build-668-chart-analysis.yml','ci/build668-quality.py','BUILD668-CHANGE-NOTES.md'}
assert set(added)==expected_add,(added,expected_add)
report={'static_checks':{'passed':sum(checks.values()),'total':len(checks)},'baseline_files':len(base),'source_files':len(new),'changed_files':sorted(changed),'added_files':sorted(added),'removed_files':removed,
        'kotlin_unit_tests':'WRITTEN_NOT_EXECUTED', 'android_instrumentation':'WRITTEN_NOT_COMPILED', 'gradle':'BLOCKED_NO_NETWORK_ANDROID_SDK','android_apk':'NOT_BUILT','live_data':'NOT_VERIFIED','baseline_sha256':hashlib.sha256(orig.read_bytes()).hexdigest()}
(root/'BUILD668-STATIC-TEST-RAPORU.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
print(json.dumps(report,indent=2,ensure_ascii=False))
