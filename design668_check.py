from pathlib import Path
from lxml import etree
import re,json,sys
r=Path(__file__).resolve().parent
p=r/'android-v6323/app/src/main/res/layout/activity_stock_detail.xml'
src=r/'android-v6323/app/src/main/java/tr/borsatakip/v5/ui/StockDetailActivity.kt'
xml=p.read_text();kt=src.read_text();a='http://schemas.android.com/apk/res/android'
tree=etree.parse(str(p));ids=[e.get('{%s}id'%a) for e in tree.getroot().iter() if e.get('{%s}id'%a)]
checks={
 'xml_well_formed': True,
 'unique_android_ids':len(ids)==len(set(ids)),
 'all_original_contract_ids': all('@+id/'+n in ids for n in 'title subtitle liveQuote liveDecision chart chartStatus btnTradingViewChart btnTimeframeMenu trend5m trend15m trend1h trend1d chartUnavailablePanel btnChartRetry btnChartRetry btnConnectionSettings trendInfoPanel overview technicalDetails dataQuality validity btnTechnicalScreen btnNewsScreen btnSignalScreen'.split()),
 'tradingview_on_top':xml.index('@+id/btnTradingViewChart')<xml.index('@+id/multiTimeframeSummary')<xml.index('@+id/detailTabChart')<xml.index('@+id/chart'),
 'three_functioning_tabs': all('@+id/'+x in ids and 'R.id.'+x in kt for x in ('detailTabChart','detailTabTechnical','detailTabPlan')),
 'four_timeframe_chips': all('@+id/'+x in ids and 'R.id.'+x in kt for x in ('quick5m','quick10m','quick1h','quick1d')),
 'all_4_periods_requested': all('ChartTimeframe.'+x in kt for x in ('FIVE_MIN','TEN_MIN','ONE_HOUR','ONE_DAY')),
 'distinct_10_minute_aggregation': 'TEN_MIN("10 DK", 5' in (r/'android-v6323/app/src/main/java/tr/borsatakip/v5/analysis/ChartTimeframe.kt').read_text(),
 'reference_indicators_bound': all('@+id/'+x in ids and 'R.id.'+x in kt for x in ('toggleEma9','toggleEma21','toggleSma50','toggleSma200','toggleVwap','toggleBollinger')),
 'levels_and_scenarios_from_chart':all('@+id/'+x in ids and 'R.id.'+x in kt for x in ('supportDetail','resistanceDetail','graphSupport','graphResistance','breakoutUp','breakoutDown')),
 'plain_language_at_technical_end':(lambda v:v.get('{%s}id'%a)=='@+id/plainExplanation')(tree.xpath('//*[@android:id="@+id/detailTechnicalSection"]',namespaces={'android':a})[0][-1]),
 'no_sample_price_embedded':'2,65' not in xml and '2,65' not in kt,
 'stale_quote_disclosure':'GÜNCEL DEĞİL' in kt and 'GECİKMELİ' in kt,
 'favoriting_uses_repository':'favoriteRepository.toggle' in kt,
 'preserves_tab_state':'outState.putString("detailTab"' in kt,
 'all_drawables_exist':all((r/'android-v6323/app/src/main/res/drawable'/n).is_file() for n in ('bg_stock_outline.xml','bg_stock_inset.xml','bg_stock_green.xml','bg_stock_red.xml','bg_stock_warning.xml','bg_stock_tab_active.xml')),
 'source_ref_has_no_unknown_ids': len([x for x in set(re.findall(r'R\.id\.(\w+)',kt)) if ('@+id/'+x) not in ids and x not in (r/'android-v6323/app/src/main/res/layout/include_bottom_nav.xml').read_text() and x not in ('bottomNav',)])==0,
}
print(json.dumps(checks,ensure_ascii=False,indent=2))
if not all(checks.values()):sys.exit(1)
