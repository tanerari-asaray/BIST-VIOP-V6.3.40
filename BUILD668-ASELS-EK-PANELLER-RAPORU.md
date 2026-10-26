# BIST–VİOP v6.3.38 / Build 668 — ASELS benzeri Hisse Detayı panelleri

Tarih: 22 Ekim 2026 · Temel sürüm: son doğrulanan Build 668 kaynak ZIP.

## Yapılanlar

| Referanstaki istek | Kaynağa uygulanan | Sonuç |
|---|---|---|
| Sekiz fiyat ve temel veri kartı | En düşük, en yüksek, açılış, önceki kapanış ve hacim doğrulanmış günlük OHLCV'den; piyasa değeri, F/K ve PD/DD yalnız geçerli KAP finansal değerlendirmesinden. Yatay kaydırılabilir sekiz kart. | ✅ Kodda |
| Altı trend satırı | 5 dk, 10 dk, 1 saat, günlük, haftalık, aylık dönemler OHLCV deposuna ayrı istek yapar; son teyitsiz bar dışlanır; tüm kartlar tıklanabilir. | ✅ Kodda |
| RSI/MACD/Stokastik/CCI/ADX/Momentum | RSI ve MACD mevcut ChartIndicators; diğer göstergeler ExtendedStockEvidence hesaplamasına bağlandı. Yetersiz veri → "—". | ✅ Kodda |
| 3 direnç + son fiyat + 3 destek | Kapanmış mumlarda gerçek pivotlardan, 0.3% yakın seviyeler birleştirilerek en fazla üçer kademe; son fiyat veri sağlayıcı zamanı ile gösterilir. | ✅ Kodda; eksik pivot durumunda boş |
| 3 analist konsensüsü hedefi | Üç ayrı hedef kartı ve kaynak uyarısı var. Bağımsız, tarihli analist konsensüsü sağlayıcısı yok; rakam üretilmedi. | ⚠️ Yerleşim var, veri bağlantısı yok |
| Sekizli gezinme menüsü | Grafik, Şirket Bilgileri, Finansal Tablolar, Haberler, Temettü, Ortaklık Yapısı, Resmî Mevzuat, Analist Raporları. Tıklama işlevleri var; KAP/mevzuat genel bağlantıları şirket detayına özgü değil. | ⚠️ Menü var; bazı içerikler kısmi |
| ASELS türü başlık + TradingView | Önceki dinamik başlık, şirket, favori ve TradingView bağlantısı bozulmadan korundu. | ✅ Kaynakta korunmuş |

**Finansal güvenlik:** Örnek ASELS fiyatları, hayalî analist hedefleri, sektör bilgileri ve finansal oranlar kodda sabitlenmedi. Finansal verinin tarihi ve kaynağı ayrı gösterilir. Mum fiyatıyla bilanço tutarları birbirine karıştırılmaz.

**Görünüm:** Küçük mobil ekranda sekiz metrik yatay kaydırılır, ayrıntılı paneller altta dikey açılır. Geniş ASELS görselinin masaüstü çok sütunlu yerleşimiyle piksel düzeyinde birebir uyum iddia edilmez.

## Kaynak ve test kanıtları

- Yeni XML ve kod sözleşmesi: **70/70 başarılı**.
- Bağımsız Kotlin teknik gösterge testi: **13/13 başarılı** (gözlem verileri deterministik test girdileri, uygulama fiyatı değildir).
- Mevcut kaynak hesaplama regresyonu: **198/198 başarılı**.
- Kotlin PSI sözdizimi denetimi: **3 dosya, sıfır hata**.
- Android XML ayrıştırma: tüm res XML dosyaları başarılı.
- JUnit `ExtendedStockEvidenceTest.kt` testleri Android projesine eklendi fakat **Gradle çalışmadığından JUnit yürütülmedi**.
- Gradle diagnostic denemesi: **BAŞARISIZ**. `services.gradle.org` için DNS `UnknownHostException`; ayrıntılar `ANDROID-GRADLE-DERLEME-LOG.txt`.
- APK, DEX/imza, gerçek cihaz görüntüsü ve piksel fark testi: **YAPILAMADI**; başarı diye işaretlenmedi.

## Değişen / yeni kaynaklar

- `android-v6323/app/src/main/res/layout/activity_stock_detail.xml`
- `android-v6323/app/src/main/res/layout/include_stock_detail_extended_metrics.xml`
- `android-v6323/app/src/main/res/layout/include_stock_detail_extended_panels.xml`
- `android-v6323/app/src/main/java/tr/borsatakip/v5/ui/StockDetailActivity.kt`
- `android-v6323/app/src/main/java/tr/borsatakip/v5/analysis/ExtendedStockEvidence.kt`
- `android-v6323/app/src/test/java/tr/borsatakip/v5/analysis/ExtendedStockEvidenceTest.kt`

## Bilinen eksikler

1. Analist konsensüsünde doğrulanmış kurumsal veri feed'i yok; hedefler "—" kalır.
2. Temettü/ortaklık için özel API yok; menü açık uyarı verip resmî genel KAP sayfasını açar.
3. Android Gradle derlemesi/JUnit/Lint/cihaz ekran testi tamamlanmadı; bu nedenle kaynak düzeyinde doğrulama APK doğrulaması değildir.
4. Kaynak ZIP bu sohbette teslim edilmiştir; GitHub ana dalına otomatik yükleme yapılmadı.
