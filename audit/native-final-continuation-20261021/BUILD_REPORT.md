# Gerçek derleme ve test kaydı — 21.10.2026

Doğrulanan kaynak `3e84542353ad1ddd0641c579d5636ac6103c0e42`. İzole native kopyası ile asıl kaynağın 618 SHA-256 değeri aynı ve kontrol boyunca değişmedi. GitHub geri okuması 25/25; Git blob eşleştirmesi 618/618.

## Ortam

Tam JDK Temurin 17.0.20.1+1, Android Platform 35 (rev 2), kurulu Build Tools 35.0.0, Gradle 8.10.2, AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Room 2.6.1. Uygulama bağımlılık sürümleri değiştirilmedi. Gradle 8.10.2 dağıtımı resmi SHA-256 ile doğrulandı: `31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26`.

## Çalıştırılan native görevler

```text
--no-daemon --max-workers=2 --stacktrace -I ../ci-source-only.init.gradle
:app:testDiagnosticUnitTest
:app:compileDiagnosticAndroidTestKotlin
:app:processDiagnosticResources
:app:lintDiagnostic
```

İlk tam başarılı tur Gradle 8.10.2 yürütücüsüyle 431,73 saniye sürdü. Normal tekrar 15,06 saniye geçti. Sonra projenin değişmemiş `gradlew`/Wrapper JAR'ı aynı dağıtımla aynı dört görevi 19,32 saniyede başarıyla doğruladı. Tekrar turlarında görevlerin çoğu `UP-TO-DATE`; testlerin iki kez yeniden yürütüldüğü iddia edilmez. Son kaynak için temiz derleme iddiası yapılmaz.

983 test, 157 JUnit suite; başarısız/hata/atlanan 0. Android Lint 0 hata/fatal, 774 uyarı. Android instrumentation kaynakları 53 class üretti; APK ve fiziksel cihaz yürütmesi yok.

`assembleDiagnostic --dry-run` görev grafiği beklenen exit 1 ve `SOURCE_ONLY_APK_BLOCKED` ile reddedildi. Bu kontrol paketleme görevi çalıştırmadı; APK/AAB 0.

## Regresyonların gerçek yeniden üretimi

Üretim VİOP kabul koşulu düzeltilmeden `ViopV6SafetyGateTest` ve `ViopDashboardPolicyTest` seçilerek çalıştırıldı: 23 test, 4 beklenen başarısız, 0 hata/atlanan. Başarısızlar seans doğrulaması, geçersiz son fiyat, sonsuz hacim ve buna bağlı liste/sayaç davranışıydı. Koşullar düzeltildi; aynı testler son 983'lük kümede geçti. Önceki başarısız XML'ler kanıt ZIP'inde `BEFORE_FIX` altında son sonuçlardan ayrılır.

İlk ortam denemesinde JRE içinde `javac` bulunmadı. Tam JDK SHA-256 doğrulanarak kuruldu. Bildirim temizliği ara derlemesinde unutulan bir `immutableFlag` çağrısı bulundu ve nihai kodda `PendingIntent.FLAG_IMMUTABLE` ile düzeltildi. Bu ara kayıtlar `INTERMEDIATE` altında korunur.

## Diğer kontroller

Node gateway 64/64; Bun 1.4.3 ile gerçek yerel WebSocket 1/1, 5 assertion; sunucu/deploy bundle eşitliği geçti. Üç modül için bundle doğrulaması çalıştı. Metin kaynakları 108/108 (44 kaynak; tr-TR ve en-US), tüm yeni metin kaynaklarının kullanımı ve XML yapısı doğrulandı. `git diff --check` ve CI JavaScript sözdizimi kontrolü geçti.

Test sonucu önce çalışma kopyasında üretildi. Uzak commit'in aynı 618 dosyaya sahip olduğu Git blob/SHA-256 ile doğrulandıktan sonra kaynak doğrulayıcısı `GITHUB_SHA=3e84542353ad1ddd0641c579d5636ac6103c0e42` ile gerçek raporları yeniden okuyup bağladı. `source-verifier-bound.log` ve `postvalidation-source-binding.json` bu kimlik bağını kaydeder; test yürütmesi taklit edilmedi.

## Sınırlar

GitHub Actions run 39588492586 runner 0/adım 0 nedeniyle native test yürütmedi. Yerel sonuçlar Actions başarısı veya Actions artefaktı değildir. Telefon/yazı ölçeği/Keystore/SQLite/arka plan kabulü ve lisanslı canlı sağlayıcı doğrulaması yoktur. İmzalama ve üretim dağıtımı yapılmadı.
