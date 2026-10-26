# BIST / VİOP V6.3.15 Build 645 — kaynak revizyonu

16.10.2026

## Gerçekten uygulananlar
- FullUniverseScanActivity tarama ekranına koyu lacivert/neon yeşil stil uygulandı; kontrol alanları sıkıştırıldı.
- Tarama sonuçlarında trend yönüyle renklendirilmiş kartlar korundu; koyu ekran üzerinde okunaklı hale getirildi.
- Sonuca dokununca gerçekte mevcut `closes` serisini Android Canvas ile çizen grafik ve teknik/temel veri uygunluğu açıklamaları eklendi. Eksik fiyat geçmişi ve eksik bilanço alanlarında fiyat/sinyal/ucuzluk uydurulmaz.
- Build 645'te bulunan, Activity yaşam döngüsünden bağımsız process-scoped tarama devamlılığı korundu.

## Sınırlar
- Kaynak paketin Android APK'ya derlenmesi BU ORTAMDA BAŞARILAMADI. Gradle 8.10.2 indirilemedi (`UnknownHostException: services.gradle.org`); Android SDK/Gradle önbelleği de yok.
- `FullUniversePolicy.kt` saf Kotlin derlemesi başarılı; 6/6 kontrol başarılı. Bu, Android uygulaması derleme/doğrulama veya gerçek cihaz testi yerine geçmez.
- Grafik yalnızca mevcut kapanışlardan oluşur; 1G/5G vb. kullanıcı seçilebilir zaman aralığı ve hacim grafiği bu revizyonda eklenmedi.
- Lisanslı VİOP sağlayıcısı, yetkili canlı piyasa kaynağı, temel değerleme skoru ve gerçek cihaz testi hâlâ eksiktir.
- Mevcut paketle aynı build numarası korunmuştur; kaynak revizyonudur, imzalanmış dağıtım APK'sı değildir.

## APK üretmek için
Android SDK 35, JDK 17, Gradle 8.10.2 erişimi bulunan ortamda `./gradlew :app:assembleDiagnostic` çalıştırın. APK çıktısı `app/build/outputs/apk/diagnostic/` klasöründe oluşur; testler için `./gradlew :app:testDiagnosticUnitTest` kullanın. Yalnızca derleme ve testler başarılıysa cihazda kurulum denenmelidir.
