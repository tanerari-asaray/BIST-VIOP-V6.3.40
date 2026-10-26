# BUILD 670 — Kaynak düzeltmeleri · 26 Ekim 2026

## Yapılan değişiklikler
- Kullanıcının açık talebiyle ekli v6.3.40 / BUILD 670 kaynak paketi esas alındı; eski V5 paketine dönülmedi.
- Hisse satırı iki yatay bölüme ayrıldı. Favori ve detay düğmeleri 48×48 dp, ortalı ve klavye odağı alabilir hâle getirildi. Grafik/fiyat alt bölüme taşındı; fiyat alanı esnek genişlik alıyor. Uzun durum metni iki satırla sınırlandı; tam açıklama erişilebilirlik açıklamasında korunuyor.
- Favori testi artık Espresso click() kullanıyor; ikon/veritabanı değişimi ve Activity yeniden oluşturulduktan sonra kalıcılık kontrol ediliyor. Detay düğmesi tam görünürlük kontrolünden sonra gerçek click() ile açılıyor; hedef Activity ve seçilen TESTZ sembolü kontrol ediliyor. Espresso görünürlük eşiği gevşetilmedi.
- Test, kullanıcı kaydırmasını temsil edecek şekilde daralan üst başlığı kapatıp ilk satırı görünür alana getiriyor. İlk rapordaki kırpılmanın tek nedeni henüz cihazda kanıtlanmış değildir.
- build-670-touch-regression.yml eklendi: Android 15, 320/411 dp ve 1.0/1.3/1.5 yazı ölçeği kombinasyonları; MarketTouchRuntimeTest ve mevcut Build670ChartRuntimeTest. Başarısız testte APK üretim adımı atlanır.
- Mevcut iş akışlarındaki setup-java@v4 kullanımları @v6 yapıldı; JDK sürümleri korundu. Diğer Action sürümleri bu değişiklikte güncellenmedi.

## Gerçek doğrulama sonucu
PASS: Kaynak ZIP CRC/bütünlüğü; değişen XML parse; satırdaki tüm görünüm kimliklerinin korunması; 48 dp hedef boyutları; gerçek click ve kalıcılık kontrolü kaynak denetimi; tüm workflow YAML parse; çıktı ZIP bütünlüğü.
ENGELLİ: Gradle wrapper 8.10.2 indirmesi `java.net.SocketException: Network is unreachable` ile kesildi. JVM testleri, Android test derlemesi ve Lint çalıştırılamadı. Android SDK/adb/emülatör hazır değil. Bu raporda önceki 1.048 testin sonucu yeni kaynak için tekrarlanmış sayılmıyor.
ÇALIŞTIRILMADI: GitHub Actions; Android 15 dokunma/grafik/S-D cihaz doğrulaması; canlı veri, fiziksel cihaz, imza ve APK kontrolleri.

## Kaynak ve teslim kimliği
SOURCE_BASE_SHA256: 9ac85c853bc2e5d3d3f7dd055da67c93191bad4c918326c8ee749c7eb1f077e9
GIT_COMMIT_SHA_START: ZIP içinde .git yok; kullanıcı raporundaki 8e21475 bağımsız doğrulanmadı.
GIT_COMMIT_SHA_FINAL / çalışma dalı / uzak yükleme: Yok; bu teslim kaynak ZIP düzeltmesidir.
Actions çalışma kimliği/sonucu: Yok, çalıştırılmadı.
APK adı/boyutu/SHA256 ve imzalama: Yok; APK üretilmedi.
Sürüm: 6.3.40 / versionCode 670 korundu.
Nihai dağıtım: Cihaz testleri ve derleme tamamlanana kadar doğrulanmadı.

## Değişen dosyalar
- `.github/workflows/build-666-verified-apk.yml`
- `.github/workflows/build-667-quote-diagnostics.yml`
- `.github/workflows/build-668-chart-analysis.yml`
- `.github/workflows/build-apk.yml`
- `.github/workflows/build-from-zip.yml`
- `.github/workflows/build-v627-diagnostic.yml`
- `.github/workflows/build-v628-diagnostic.yml`
- `.github/workflows/build-v629-fundamentals.yml`
- `.github/workflows/build-v630-financial-import.yml`
- `.github/workflows/build-v631-valuation-button.yml`
- `.github/workflows/build-v6315.yml`
- `.github/workflows/build-v6318.yml`
- `.github/workflows/build-v632-api-route.yml`
- `.github/workflows/build-v6321.yml`
- `.github/workflows/build-v6323.yml`
- `.github/workflows/build-v633-fundamentals.yml`
- `.github/workflows/build-v634-route-diagnostic.yml`
- `.github/workflows/build-v635-source-diagnostic.yml`
- `.github/workflows/build-v636-probe-truthfulness.yml`
- `android-v6323/app/src/androidTest/java/tr/borsatakip/v5/ui/MarketTouchRuntimeTest.kt`
- `android-v6323/app/src/main/res/layout/item_stock_compact.xml`
- `.github/workflows/build-670-touch-regression.yml`
