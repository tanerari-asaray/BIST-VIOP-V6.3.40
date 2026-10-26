# BIST / VİOP v6.3.38 — Build 668 kaynak geliştirmesi

Kaynak tabanı: doğrulanmış Build 667. Yeni dal için öneri: `build/apk668-chart-readable-analysis`.

## Tasarım koduna uygulananlar

- TradingView bağlantısı grafik ekranında sembol başlığından hemen sonra, ilk eylem olarak gösterilir. BIST sembolü TradingView'in `symbols/BIST-SYMBOL/` sayfasına; VİOP sözleşmesi doğrulanamıyorsa TradingView aramasına gider. Sembol filtrelenir.
- Ekranın Grafik sekmesi kaydırılabilir hale getirildi. Grafik ve destek/direnç/gösterge deneyimi korunurken en alta **Bu hisseyi kolayca anlayalım** açıklamaları eklendi.
- 5 dakika, 10 dakika, 1 saat ve günlük farklı mum kaynaklarıyla bağımsız olarak hesaplanır. Yeni 10 dakikalık veri iki eksiksiz 5 dakikalık mumdan türetilir; eksik gruplar kullanılmaz.
- Yön belirsizse nötr gösterilir, veri yoksa tahmini fiyat/yön üretilmez. Zaman dilimleri birbiriyle çelişirse dikkat notu gösterilir. Görüş alış/satış sinyali değildir.
- `ReadableStockAnalysis` son mumu dışarıda bırakarak EMA9/EMA21, önceki 20 mum destek/direnç, RSI, hacim oranı ve ATR ile kısa anlaşılır açıklamalar oluşturur. Seçili dönemin kaynağı ve tarih başlığı gösterilir.
- VİOP sözleşmesi için hisse şirket analizi yerine teminat/kaldıraç/vade riski açıklaması gösterilir.
- Teknik özet hesabı da son mumu dışarıda bırakır; mum kapanışı kesinliği kanıtlanmadan işlem sinyali verilmez.

## Test tanımı ve mevcut engeller

- `ReadableStockAnalysisTest`: eksik veri, yön, açık mum güvenliği, gerçek destek/direnç, bozuk mumları eleme.
- `Build668ScreenContractTest`: TradingView'in en üste yerleştirilmesi, dört dönem ve sade açıklama sözleşmesi.
- `Build668ChartRuntimeTest`: emülatör/cihazda çalıştırılması için hazırlanmış offline UI testi. Sadece derlenmesi çalıştırıldığı anlamına gelmez.
- CI: `.github/workflows/build-668-chart-analysis.yml`; `ci/build668-quality.py` gerçek JUnit XML ve APK çıktısı olmadan başarı bildirmez.
- Yerel çalışma ortamında Android SDK, Gradle dağıtımı ve ağ erişimi olmadığından `./gradlew testDiagnosticUnitTest` **çalıştırılamadı** (`UnknownHostException: services.gradle.org`).
- Gerçek canlı BIST/VİOP sağlayıcısı Build 667 raporunda bağlı değildi; bu tasarım canlı veri sorununu çözmez. Gerçek ekranın fiziksel cihazda doğrulanması ve APK imzası uyumluluğunun ölçülmesi ayrıca gereklidir.
