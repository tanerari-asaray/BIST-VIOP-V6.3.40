#!/usr/bin/env python3
"""Build668 source-only assertions. A passing result is NOT an Android build/device test."""
from pathlib import Path
import json,re,hashlib,zipfile,subprocess,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1]
S=R/'android-v6323/app/src/main'
stock=(S/'java/tr/borsatakip/v5/ui/StockDetailActivity.kt').read_text()
full=(S/'java/tr/borsatakip/v5/ui/FullscreenChartActivity.kt').read_text()
tf=(S/'java/tr/borsatakip/v5/analysis/ChartTimeframe.kt').read_text()
repository=(S/'java/tr/borsatakip/v5/data/ChartHistoryRepository.kt').read_text()
layout=ET.parse(S/'res/layout/activity_stock_detail.xml')
footer=ET.parse(S/'res/layout/include_stock_easy_explanation.xml')
A='{http://schemas.android.com/apk/res/android}'
ids={v.get(A+'id','').split('/')[-1] for f in (layout,footer) for v in f.iter() if v.get(A+'id')}
needed=('easyPriceMovement','easyImportantLevels','easyChangeConditions','easyCompany','easyWhyInterest','easyRisks','easyDataSource')
checks={}
def check(k,v):checks[k]=bool(v)
check('version_code_668', 'versionCode = 668' in (R/'android-v6323/app/build.gradle.kts').read_text())
check('version_name_6_3_38', 'versionName = "6.3.38"' in (R/'android-v6323/app/build.gradle.kts').read_text())
check('all_six_cards_in_stock_detail', all(x in ids for x in needed))
check('footer_after_all_tabs', layout.getroot().find('.//include[@layout="@layout/include_stock_easy_explanation"]') is not None)
check('six_cards_in_fullscreen', all(s in full for s in ('Fiyat nasıl hareket ediyor?', 'Hangi seviyeler önemli?', 'Bu yorum ne zaman değişir?', 'Kısaca ne iş yapıyor?', 'Neden ilgi görüyor?', 'Dikkat edilmesi gerekenler')))
check('business_not_invented','faaliyet alanına ilişkin doğrulanmış KAP açıklaması' in stock)
check('interest_reason_not_invented','fiyat veya hacim artışı' in stock.lower())
check('no_fake_prices',all('64,50 TL' not in s for s in (stock,full)))
check('safe_previous_candle_trends', 'LinearTrendAnalyzer.analyze(clean.dropLast(1))' in stock and 'LinearTrendAnalyzer.analyze(candles.dropLast(1))' in stock)
check('clear_previous_in_footer','resetEasyMarketCards()' in stock and 'clearSelectedAnalysis()' in stock)
check('no_raw_source_error', '${error.message' not in stock and '${error.javaClass.simpleName' not in stock)
check('monthly_ohlcv_from_source', 'fun monthly(input: List<Candle>)' in tf and 'OhlcvResampler.monthly(clean)' in repository and 'OhlcvResampler.monthly(raw)' in full)
check('30min_complete_series', 'THIRTY_MIN("30 DK", 5,' in tf and 'aggregateMinutes = 30)' in tf)
check('monthly_interval_available', 'ONE_MONTH("1 AY"' in tf and 'ChartTimeframe.ONE_MONTH' in stock and 'ChartTimeframe.ONE_MONTH' in full)
check('tradingview_retained', 'openTradingViewChart()' in stock and 'private fun openTradingView()' in full)
check('android_wrapper_restored', (R/'android-v6323/gradle/wrapper/gradle-wrapper.jar').is_file())
# Parse all file-backed resources, not just the main layout. Static XML validity != aapt2 build.
xmls=list((S/'res').rglob('*.xml'))
invalid=[]
for x in xmls:
 try:ET.parse(x)
 except ET.ParseError as e:invalid.append(f'{x.relative_to(S)}: {e}')
check('android_resource_xml_all_parse',not invalid)
# New card IDs must be Kotlin-bound: prevents decorative/placeholder-only cards.
check('six_cards_bound',all(('R.id.'+x) in stock for x in needed))
check('no_generated_apk_claim',not (R/'android-v6323/app/build/outputs/apk/diagnostic/app-diagnostic.apk').exists())
results={'status':'PASS' if all(checks.values()) else 'FAIL','scope':'Static source and offline model tests, NOT Android build, install, or screenshot validation','checks':checks,'passed':sum(checks.values()),'total':len(checks),'invalid_xml':invalid,'android_compile':'NOT_RUN (Gradle distribution DNS unresolved)','android_lint':'NOT_RUN','emulator_capture':'NOT_RUN','apk':'NOT_PRODUCED','timestamp':'2026-10-22'}
(R/'BUILD668-FINAL-KAYNAK-TESTLERI.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
for key,val in checks.items(): print(('PASS' if val else 'FAIL'),key)
print('FINAL',results['passed'],'/',results['total'])
if not all(checks.values()):raise SystemExit(1)
