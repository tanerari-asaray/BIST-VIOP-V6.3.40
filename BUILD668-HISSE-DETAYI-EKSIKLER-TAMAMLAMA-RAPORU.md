# Build 668 — İstenen eksiklerin uygulanması ve kontrolü

**Sürüm:** V6.3.38 / Build 668  
**Tarih:** 22.10.2026  
**Hedef:** Hisseler → Hisse Detayı (iki tasarım görselindeki ana düzen)

## Kaynakta tamamlananlar

- ✅ **Gösterge sırası:** `EMA9 → EMA21 → SMA50 → SMA200 → VWAP → Bollinger` aynı yatay kaydırılabilir sıraya taşındı. Taşan butonlar küçük ekranlarda yatay kaydırılır; kapanmış veriye ait göstergelerin işleyişi korunur.
- ✅ **Diğer göstergeler:** İkinci satırdaki EMA20/50/200, Trend ve Kanal kontrolleri kaybolmadı; `Diğer göstergeler` ile açılır/kapanır hale getirildi.
- ✅ **Teknik zaman dilimi kartları:** 5 dk, 10 dk, 1 saat, günlük kartlarına sağ ok ve gerçek dokunma olayları eklendi. Kart tıklaması Grafik sekmesine geçirip aynı dönemin gerçek OHLCV mumlarını ister.
- ✅ **Ayrıntılı Yorumu Aç:** Destek ve direnç alanlarının yanına ayrı buton eklendi; tıklanınca Teknik sekmesi ve sade açıklaması açılır.
- ✅ **Ekran ölçüleri:** Grafik çerçevesi cihazın `screenHeightDp` değerinden hesaplanıp 260–360 dp sınırına alınır. Tam ekran ikonu dönem seçim sırasına taşındı ve butonlar 44 dp dokunma yüksekliğinde.
- ✅ **Font, ikon, boşluklar:** Başlıca sekmelerde `sans-serif-medium`, düzgün padding ve yeni sağ ok vektörü uygulandı; 5–10 dk ve diğer dönem kartlarının erişilebilirlik metinleri güçlendirildi.
- ✅ **Trend gücü:** Gerçek trend puanına bağlı dört bölmeli güç çizgisi simgeleri eklendi; yeterli doğrulanmış mum yoksa puan/bant uydurulmaz.
- ✅ **Kullanıcı güvenliği:** TradingView, gerçek veri kontrolü, veri yok uyarıları, favori ve mevcut Plan bileşenleri korunmuştur.

## Test sonuçları

- ✅ Önceki Build 668 kaynak sözleşmesi: **17/17**.
- ✅ Yeni göstergeler/sekme etkileşimleri/ölçü ve erişilebilirlik kaynak sözleşmesi: **30/30** (`ci/verify_hisse_detail_pixel_contract.py`). Bu *statik* testtir, görsel piksel kıyaslaması değildir.
- ✅ Kotlin PSI sözdizim kontrolü: `StockDetailActivity.kt` ve `PriceChartView.kt` için **0 hata**.
- ✅ Kotlin JVM finans/teknik hesap testleri: **198/198**.
- ✅ ZIP dosyalarının CRC, dosya bütünlüğü ve SHA-256 listesi teslimatta tekrar kontrol edildi.
- ⚠️ **Cihaz/piksel eşleşmesi:** Android emülatörü, SDK/ADB olmadığı için çalıştırılamadı; birebir piksel eşleşmesi ve gerçek ekran görüntüsü teyidi **yapılamadı**.
- ⚠️ **APK:** `gradle-8.10.2-bin.zip` indirilemediği ve Android SDK bulunmadığı için Android `assembleDiagnostic` bu ortamda tamamlanamadı. APK üretilmedi.
- ⚠️ Gerçek sağlayıcı / canlı BIST uçtan uca testi bu ortamda tamamlanmadı.

## Sonraki doğrulama (SDK/CI ortamında)

```bash
cd android-v6323
./gradlew :app:testDiagnosticUnitTest :app:lintDiagnostic :app:assembleDiagnostic
```

Emülatör veya telefon görüntüsünde 360 dp ve 412 dp genişlikler, büyük yazı ölçeği, dört trend kartı ve göstergelerin yatay kaydırılması ayrı ayrı kontrol edilmelidir. Bunlar geçmeden **cihazda birebir tamamlandı** ifadesi kullanılmaz.
