# Build 668 — Hisseler > Hisse Detayı düzeltmesi

22.10.2026: Referans mobil tasarımları temel alınarak Hisse Detayı ekranının dört zaman dilimi **5 dk / 10 dk / 1 saat / günlük** ile tutarlı olması sağlandı. Uygulamada zaten mevcut olan `ChartTimeframe.TEN_MIN` kullanılarak 10 dakikalık mumların yalnızca eksiksiz 5 dakikalık veri çiftlerinden türetilmesi kuralı korunur.

## Değişen kaynak dosyalar

- `android-v6323/app/src/main/java/tr/borsatakip/v5/ui/StockDetailActivity.kt`: zaman dilimi seçim listesine `TEN_MIN` eklendi; dört dönem özetindeki 15 dakika, 10 dakika ile değiştirildi.
- `android-v6323/app/src/main/res/layout/activity_stock_detail.xml`: trend kutusunun etiketi `10 DK` yapıldı.

TradingView üst düğmesi, veri doğruluk uyarıları ve sade açıklamalar korunmuştur. Test durumu: statik dosya/bağlantı kontrolü gerçekleştirildi; Gradle/JUnit emülatör testleri yürütülemedi. APK üretildiği veya cihaz görüntüsünün doğrulandığı iddia edilmez. Railway erişimi onay/oturum nedeniyle kullanılamadı.
