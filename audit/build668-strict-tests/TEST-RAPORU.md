# BIST / VİOP Build 668 — Sıkı kaynak kontrolü ve test raporu

**Tarih:** 21.10.2026  
**Temel sürüm:** v6.3.38 / Build 668  
**Düzeltilmiş kaynak commit'i:** [44f5ae0dbf032e965d026b4ec91a8298be00be87](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/commit/44f5ae0dbf032e965d026b4ec91a8298be00be87)  
**Dal:** `build/apk668-chart-readable-analysis`

## Doğrulama sonucu

| Kontrol | Sonuç | Niteliği |
|---|---|---|
| İlk Build 668 ZIP CRC | BAŞARILI | Önceki teslim arşivi açılıyor |
| İlk Build 668 dosya manifesti | 642/642 BAŞARILI | SHA-256 birebir eşleşti |
| JVM üzerinde gerçek Kotlin hesaplama/test çalıştırma | **198/198 BAŞARILI** | `kotlinc` ile kaynaklardan derlenip JVM'de yürütüldü; 150 rastgele tohumlu senaryo dahil |
| Yerel JUnit uyumlu test sınıflarının metodları | **13/13 BAŞARILI** | Gerçek Kotlin kodu JVM'de, yerel minimal `org.junit` uyumluluk sınıflarıyla çağrıldı; resmi Gradle/JUnit koşucusu değildir |
| Kotlin üretim kodu sözdizimi | **255 dosya / 0 sözdizim hatası** | Kotlin PSI ayrıştırıcısı; Android tip çözümlemesini doğrulamaz |
| GitHub'a gönderilen düzeltilmiş kaynak | **4/4 blob özeti eşleşti** | Yerel Git SHA-1 değerleriyle GitHub ağacı birebir aynı |
| Gradle `testDiagnosticUnitTest` | ÇALIŞTIRILAMADI | Yerel Gradle paketi indirilemiyor (DNS); Android SDK da yok |
| GitHub Actions | BAŞARISIZ | [Düzeltilmiş commit koşusu 39623808344](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39623808344) sıfır adımla bitti; runner atanmadı, Android test sonucu değildir |
| Android lint / assemble / APK imzası | ÇALIŞTIRILAMADI | Android araç zinciri yok ve GitHub runner başlatılamadı |
| Emülatör/telefon görsel testi | YAPILAMADI | Android emülatör/ADB mevcut değil |
| Gerçek BIST/VİOP veri entegrasyonu | DOĞRULANAMADI | Lisanslı sağlayıcı bağlı değil; canlı veri başarısı iddia edilmiyor |

## Gerçekten bulunan ve düzeltilen hatalar

1. **Kesintili mumdan yanlış trend çıkarma:** Yalnız mum sayısının yeterli olması, 5 dakikalık grafikte ardışık verilerin gerçekten 5 dakika aralıklı olduğunu kanıtlamıyordu. Artık intraday analiz son 30 kapalı mumda aynı gün içindeki aralıkları doğrular ve yeterli ardışık çift yoksa `null` döndürür.
2. **Trend kartları geride kalıyordu:** Ana grafik yenilendiğinde 5dk/10dk/1s/günlük kartlar her seferinde yenilenmiyordu. `load(tf)` veri güncellemesinde karşılaştırmalar tekrar alınır; önceki asenkron istekler iptal edilir ve nesil kimliğiyle eski yanıtlar engellenir.
3. **Eski veri güncel eğilimmiş gibi görünüyordu:** Kartlar artık son incelenen mum zamanını ve gecikme/güncellik belirsizliği durumunu yazar. 7 günden eski intraday veya 14 günden eski günlük kayıtlar için **'Eski veri · Yön teyitsiz'** gösterilir; eski kayıtlar zaman dilimleri uyuşmazlığı hesabına girmez.
4. **Geleceğe tarihli mum güvenliği eksikti:** Çoklu zaman dilimi hesabında tarih geleceğe kaymış kayıtlar reddediliyor.
5. Regresyon için yeni testler eklendi: seans içi boşluk, yanlış periyot etiketleme, seyrek gün bazlı örneklem, geleceğe tarihli kayıt ve yenileme/eskime ekran sözleşmesi.

## Kapsam ve sınırlar

- Hesap testleri gerçek Kotlin `ChartTimeframe`, `ChartIndicators` ve `ReadableStockAnalysis` sınıflarını derleyerek yürütüldü. `Candle` için Android SDK bağımlılığını ayıran, aynı alan sözleşmesine sahip *yalnızca testte kullanılan* küçük bir Java-Serializable tür kullanıldı.
- JUnit uyumlu testi için minimal assert/annotation yardımcıları kullanıldı; bu işlem resmi Android Gradle/JUnit sonuçları diye sunulmamaktadır.
- Kotlin PSI 255 dosyanın sözdizimini okudu; Android API bağımlılıklarının çözülmesi, resource linking, Compose/UI etkileşimi, release imzası, izinler ve gerçek cihaz davranışı bu sonuçtan çıkarılamaz.
- Grafik hesabı gerçek zamanlı sinyal üretmez. Gecikmeli veri, geçmiş verinin güncel alış/satış fiyatı olduğu anlamına gelmez.
- **APK bu testte üretilmedi; bu nedenle 'tam üretime hazır' onayı verilmedi.**

## Sonraki zorunlu Android doğrulama komutları

```bash
cd android-v6323
./gradlew :app:testDiagnosticUnitTest :app:lintDiagnostic :app:compileDiagnosticAndroidTestKotlin :app:assembleDiagnostic
```

Android aygıtında ek olarak TradingView dış bağlantısı, 5dk/10dk/1s/günlük gerçek sağlayıcı senaryoları, veri bağlantısı kopması, yatay/dikey yerleşim ve erişilebilirlik sınanmalıdır.

**İlk kaynak ZIP SHA-256:** `f505b1b528fd644370102c60102ab15e027d6aab7a654037ac37679d37cf8668`  
**Yerel kaynak/GitHub blob SHA kontrolü:** `{
  "android-v6323/app/src/main/java/tr/borsatakip/v5/analysis/ReadableStockAnalysis.kt": "5cae6b0822efcbd6360aaba3a94fffd40b1e51f7",
  "android-v6323/app/src/main/java/tr/borsatakip/v5/ui/FullscreenChartActivity.kt": "0f3d4b6e89f545fb5b709e0d93643292f1d17419",
  "android-v6323/app/src/test/java/tr/borsatakip/v5/analysis/ReadableStockAnalysisTest.kt": "09fe7d1cd230b5d58c42562c3af1a707f4de4852",
  "android-v6323/app/src/test/java/tr/borsatakip/v5/analysis/Build668ScreenContractTest.kt": "14e7468d7748e1b3f13fece2e54600994a36fb3f"
}`
