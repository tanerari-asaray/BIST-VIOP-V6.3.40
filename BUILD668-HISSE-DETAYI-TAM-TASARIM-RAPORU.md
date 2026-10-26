# Build 668 — Hisseler / Hisse Detayı tam tasarım uygulaması

Tarih: 22.10.2026 · Sürüm: **6.3.38 / Build 668**

## Gerçekten değiştirilenler

- `activity_stock_detail.xml`: üst sembol başlığı, geri/favori, TradingView en üstte, fiyat ve kaynak durumu, 2×2 trend kartları, üç görünür sekme (Grafik/Teknik/Plan).
- `StockDetailActivity.kt`: sekme geçişleri, 5/10 dk–1 saat–günlük doğrudan dönem seçimi, 4 ayrı veri talebi, EMA20/50 açıklaması, güncellik uyarısı, veri ile beslenen destek/direnç ve yön senaryoları, gerçek RSI/ATR/hacim göstergesi, sade dilde teknik açıklama.
- Yeni 6 kenarlıklı/koyu çizim kaynağı; mevcut OHLCV grafik renderer'ı ve tüm eski dinamik ID'ler korundu. Eski sinyal, canlı bağlantı ve risk alanları Plan sekmesine taşındı.
- Grafikde EMA9/EMA21/VWAP ve diğer hareketli ortalama/çizgi seçenekleri; 10 dakika, sadece tam 5 dk mum çiftlerinden oluşturulur.
- Veri yoksa grafik değeri, fiyat seviyesi veya yön uydurulmaz; özet kartları ve açıklamalar 'veri yok' gösterir.

## Doğrulama ve sınırlar

- XML/ID/kullanıcı etkileşimi/statik sözleşme denetimleri: **16/16 GEÇTİ** (`design668_check.py`).
- Kotlin JVM analiz testi: **GEÇTİ** (10 dk eksiksiz mum eşleme, eksik çiftin reddi, regresyon, RSI/ATR hesabı). Bu test Android UI veya Gradle derlemesi yerine geçmez.
- Gradle Android derlemesi: **TAMAMLANAMADI**. Wrapper `gradle-8.10.2-bin.zip` indirmeye çalıştı, ağ DNS (`services.gradle.org`) kapalı (`BUILD668-GRADLE-ATTEMPT.log`).
- Android emülatör/fiziksel cihaz ekran görüntüsü doğrulaması: **YAPILMADI**; APK: **ÜRETİLMEDİ**.
- Railway bağlantısı yetkilendirme/oturum reddi nedeniyle doğrulanamadı; sunucuya dağıtım yapılmadı.
- Bu kaynak paketi, kullanıcıya verilen iki statik görselin Android yerleşim yorumudur; cihazda piksel piksel eşleşme doğrulanmadı.

## Android derleme/test komutları

```
cd android-v6323
./gradlew :app:testDiagnosticUnitTest :app:compileDiagnosticAndroidTestKotlin :app:lintDiagnostic :app:assembleDiagnostic
```

Dosyalardaki örnek `KAYSE / 2,65 TL` sabitleri uygulamaya alınmadı. Veri doğrulaması olmadan işlem önerisi üretilmez.
