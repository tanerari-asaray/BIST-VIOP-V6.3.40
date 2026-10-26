# Kaynak değişiklikleri — 21.10.2026

Başlangıç `4201b7160d96ed2d35c7f1842d8611b5cc8fe2fc` → doğrulanan kod `3e84542353ad1ddd0641c579d5636ac6103c0e42`. Uygulama kimliği 6.3.35 / 665 olarak korundu. 25 dosya değişti.

## Davranış ve gerekçe

`TrendUiPolicy` isteğe bağlı RSI kanıtını sonlu ve 0–100 aralığında kabul eder. Geçersiz RSI güçlü renk üretemez; geçerli EMA yönü ve mevcut LONG/SHORT strateji eşikleri korunur.

`ViopQuoteDisplayPolicy` yüzde değişiminin metin ve renk için aynı sonlu değerini sağlar. Canlı etiket için pozitif sonlu fiyat, doğrulanmış seans, sıfır gecikme ve en çok 60 saniyelik geçerli zaman gerekir. Adapter, değerlendirmede aldığı saat anını kullanır.

`ViopV6Policy.assess` güncel seansın doğrulanmasını ve pozitif sonlu son fiyatı ayrıca gerektirir. Hacim sonlu/pozitif olmadığında likidite etkinlik puanı verilmez. Geçersiz kayıtlar mevcut `LIVE_DATA_NOT_VERIFIED` veya `LIQUIDITY_NOT_VERIFIED` nedenleriyle elenir. Sağlıklı adayın manuel inceleme davranışı aynı kalır; emir gönderme eklenmedi.

Üç satır adapteri 44 metin kaynağını kullanır. Var olan görünür metinler/yüzde/satır sonları korundu; metin şablonları iki locale ile 108 kontrolde doğrulandı. İki eski kaynak testi, doğrudan kod içi metin aramak yerine bağlı XML metnini okuyarak aynı etiket/uyarıları denetler.

15 horizontal LinearLayout için gereksiz baseline ölçümü kapatıldı. MinSdk 26 olduğu için üç worker/serviste erişilemez API 23/26 dalları kaldırıldı; bildirim izinleri ve API 34 foreground türü kontrolleri korundu. Nihai native derleme ve Android test kaynak derlemesi geçti; fiziksel görünüm testi yapılmadı.

## Test kapsamı

17 yeni JVM testi: RSI 4, VİOP gösterimi 9, kalite kapısı 3, liste/sayaç 1. Son toplam 983. Dört VİOP kabul regresyonu üretim koşulu düzeltmesinden önce gerçekten başarısız oldu; son kümede geçti. CI eşiği 983 ve yeni sınıflar olarak güncellendi.

## Değişen dosyalar

- `.gitignore`
- `README.md`
- `android-v6323/app/src/main/java/tr/borsatakip/v5/analysis/TrendUiPolicy.kt`
- `android-v6323/app/src/main/java/tr/borsatakip/v5/analysis/ViopV6Policy.kt`
- `android-v6323/app/src/main/java/tr/borsatakip/v5/data/ViopQuoteDisplayPolicy.kt`
- `android-v6323/app/src/main/java/tr/borsatakip/v5/ui/StocksCompactAdapter.kt`
- `android-v6323/app/src/main/java/tr/borsatakip/v5/ui/ViopOpportunityAdapter.kt`
- `android-v6323/app/src/main/java/tr/borsatakip/v5/ui/ViopUnderlyingOpportunityAdapter.kt`
- `android-v6323/app/src/main/java/tr/borsatakip/v5/worker/AutoScanForegroundService.kt`
- `android-v6323/app/src/main/java/tr/borsatakip/v5/worker/OpportunityWorker.kt`
- `android-v6323/app/src/main/java/tr/borsatakip/v5/worker/RealtimeAlertService.kt`
- `android-v6323/app/src/main/res/layout/activity_main.xml`
- `android-v6323/app/src/main/res/layout/activity_opportunity.xml`
- `android-v6323/app/src/main/res/layout/activity_settings.xml`
- `android-v6323/app/src/main/res/layout/activity_stocks.xml`
- `android-v6323/app/src/main/res/layout/activity_viop_detail.xml`
- `android-v6323/app/src/main/res/values/market_rows.xml`
- `android-v6323/app/src/test/java/tr/borsatakip/v5/analysis/TrendUiPolicyTest.kt`
- `android-v6323/app/src/test/java/tr/borsatakip/v5/analysis/ViopDashboardPolicyTest.kt`
- `android-v6323/app/src/test/java/tr/borsatakip/v5/analysis/ViopV6SafetyGateTest.kt`
- `android-v6323/app/src/test/java/tr/borsatakip/v5/data/B125ViopConfigAndUiFixTest.kt`
- `android-v6323/app/src/test/java/tr/borsatakip/v5/data/ViopQuoteDisplayPolicyTest.kt`
- `android-v6323/app/src/test/java/tr/borsatakip/v5/ui/B131ViopMainUiRegressionTest.kt`
- `ci-verify-source-only.py`
- `ci/railway-verification-report.mjs`
