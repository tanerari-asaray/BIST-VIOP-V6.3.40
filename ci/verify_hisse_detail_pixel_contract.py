#!/usr/bin/env python3
"""Build 668: deterministic source/layout interaction checks (NOT device visual verification)."""
from pathlib import Path
from lxml import etree
import json, re, sys
root=Path(__file__).resolve().parents[1]
app=root/'android-v6323/app/src/main'
layout=app/'res/layout/activity_stock_detail.xml'
code=app/'java/tr/borsatakip/v5/ui/StockDetailActivity.kt'
xml=layout.read_text(encoding='utf-8'); kotlin=code.read_text(encoding='utf-8')
namespace={'a':'http://schemas.android.com/apk/res/android'}
a='{http://schemas.android.com/apk/res/android}'
doc=etree.parse(str(layout)); tree=doc.getroot()
def get(id):
    nodes=tree.xpath('//*[@a:id="@+id/'+id+'"]',namespaces=namespace)
    assert len(nodes)==1, (id,len(nodes))
    return nodes[0]
def nearest_parent(id): return get(id).getparent()
def check_refs():
    ids={v.get(a+'id').replace('@+id/','') for v in tree.iter() if v.get(a+'id')}
    # Resolve every statically included layout, not only the top-level XML tree.
    # New plain-language cards belong to an <include>, so their IDs are real runtime IDs.
    for child in tree.iter('include'):
        resource=child.get('layout', '')
        if resource.startswith('@layout/'):
            include_path=app/'res/layout'/ (resource.split('/', 1)[1]+'.xml')
            if include_path.exists():
                included=etree.parse(str(include_path))
                ids.update(v.get(a+'id').replace('@+id/','') for v in included.iter() if v.get(a+'id'))
    return not (set(re.findall(r'R\.id\.(\w+)',kotlin))-ids-{
       'navHome','navStocks','navViop','navFavorites','navSettings','bottomNav'})
seq=['toggleEma9','toggleEma21','toggleSma50','toggleSma200','toggleVwap','toggleBollinger']
row=nearest_parent('toggleEma9')
actual=[node.get(a+'id','').replace('@+id/','') for node in row]
checks={
  'xml_valid':True,
  'reference_indicators_order':actual==seq,
  'all_six_indicators_same_row':all(nearest_parent(x) is row for x in seq),
  'single_horizontal_scroll_indicator_group':row.getparent().tag=='HorizontalScrollView',
  'bollinger_event_bound':'toggle(R.id.toggleBollinger' in kotlin or 'bind(R.id.toggleBollinger' in kotlin,
  'advanced_controls_hidden_initially':get('advancedIndicatorRow').get(a+'visibility')=='gone',
  'advanced_controls_disclosure_click':'bindIndicatorDisclosure()' in kotlin and 'advanced.visibility = if (expand)' in kotlin,
  'four_technical_cards_have_chevron':all(get(x).get(a+'drawableEnd')=='@drawable/ic_stock_detail_chevron' for x in ('tech5m','tech10m','tech1h','tech1d')),
  'chevron_vector_present':(app/'res/drawable/ic_stock_detail_chevron.xml').is_file(),
  'tech_cards_are_clickable':all(get(x).get(a+'clickable')=='true' for x in ('tech5m','tech10m','tech1h','tech1d')),
  'tech_cards_connect_to_actual_periods':all((f'R.id.{x} to ChartTimeframe.{t}') in kotlin for x,t in [('tech5m','FIVE_MIN'),('tech10m','TEN_MIN'),('tech1h','ONE_HOUR'),('tech1d','ONE_DAY')]),
  'tech_card_action_loads_real_chart':'showDetailTab("Grafik")\n                loadTimeframe(timeframe)' in kotlin,
  'technical_card_accessible':all(get(x).get(a+'contentDescription') for x in ('tech5m','tech10m','tech1h','tech1d')),
  'detailed_commentary_cta_visible_in_chart':get('btnDetailedCommentary').getparent() is get('graphSupport').getparent(),
  'detailed_commentary_cta_functional':'R.id.btnDetailedCommentary).setOnClickListener' in kotlin and 'showDetailTab("Teknik")' in kotlin,
  'technical_plain_language_retained':get('plainExplanation').getparent()==get('detailTechnicalSection'),
  'responsive_chart_frame':get('chartFrame').tag=='FrameLayout' and 'screenHeightDp * 0.43f' in kotlin and 'coerceIn(260, 360)' in kotlin,
  'fullscreen_on_timeframe_row':get('chartFullscreen').getparent()==get('quick5m').getparent(),
  'timeframe_touch_targets_44dp':all(get(x).get(a+'layout_height')=='44dp' for x in ('quick5m','quick10m','quick1h','quick1d','chartFullscreen')),
  'typography_rule_all_tabs':all(get(x).get(a+'fontFamily')=='sans-serif-medium' for x in ('detailTabChart','detailTabTechnical','detailTabPlan')),
  'chart_icon_accessibility':bool(get('chartFullscreen').get(a+'contentDescription')),
  'screen_still_has_bottom_navigation':any(x in xml for x in ('@layout/include_bottom_nav','@layout/include_stock_detail_bottom_nav')),
  'tradingview_retained':get('btnTradingViewChart') is not None and 'openTradingViewChart()' in kotlin,
  'data_protection_unchanged':'clearSelectedAnalysis()' in kotlin and 'OHLCV verisi bulunamadı. Sahte veri üretilmedi.' in kotlin,
  'no_fake_prices':'2,65' not in xml and '2,65' not in kotlin,
  'trend_strength_4_bars':'"▰".repeat(filled) + "▱".repeat(4 - filled)' in kotlin,
  'current_timeframe_10min':'Triple(R.id.trend15m, ChartTimeframe.TEN_MIN' in kotlin,
  'card_accessibility_follows_data':'contentDescription = "$explanation. Dokunarak $label grafiğini aç."' in kotlin,
  'no_duplicate_ids':len([x.get(a+'id') for x in tree.iter() if x.get(a+'id')])==len({x.get(a+'id') for x in tree.iter() if x.get(a+'id')}),
  'no_dead_kotlin_id_references':check_refs(),
}
report={'suite':'Build668 Hisse Detayı source interaction and sizing contract','checks':checks,'passed':sum(checks.values()),'total':len(checks),'real_android_build':'NOT_VERIFIED','pixel_comparison':'NOT_VERIFIED'}
(root/'BUILD668-EKSIK-TASARIM-TEST-SONUCLARI.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
for k,v in checks.items():print(('PASS' if v else 'FAIL'),k)
print('RESULT',report['passed'],'/',report['total'])
if not all(checks.values()):sys.exit(1)
