# BIST/VİOP V6.2.2 – uygulanmış kaynak düzeltmeleri ve açık işler

Bu paket son fırsat-listesi düzeltilmiş sürümünden türetilmiştir. **APK oluşturulmadı.** Telefon ve Android derleme doğrulaması yapılmadı.

## 1. Fiyat ve analiz ayrımı – UYGULANDI
- `BackendPreflightClient.kt` VİOP fiyat denetiminde günlük geçmişi `minimumBars=0` ile ayrıca sorgular.
- 220 kapalı günlük mumdan az geçmişle **fiyat bağlantısı başarılı** kalabilir, fakat `viopHistoryOk=false` olur ve `ANALİZ YETERSİZ` mesajı çıkar.
- Birim fiyat erişiminin canlı sinyal yetkisi sayılmaması korunmuştur.

## 2. Bağımsız VİOP akışı – ALTYAPI EKLENDİ, EKRAN ENTEGRASYONU BEKLİYOR
- Yeni `data/ViopLiveSocket.kt`: WSS `/v1/viop/live` kullanır; yalnız HTTPS yapılandırmasından WSS üretir, bearer yetkilendirme ister.
- Yalnız VIOP pazarına, abone olunan gerçek kontrata, artan sequence numarasına, milisaniye exchangeTimestamp'e, güncel / gecikmesiz ve seans içi quote'a izin verir.
- Kopma ve mesaj doğrulama hatalarını bildirir; socket açılması gerçek fiyat izni sayılmaz. Yaşam döngüsü ve ekran bağlama henüz yapılmadı.

## 3. 220 günlük strateji – BİLİNÇLİ KORUNDU
- `ViopScanner.kt` ana strateji günlük 220 bar ister. Bunu 5 dk 220 barla değiştirmek strateji dönüşümü olduğu için backtest olmadan uygulanmadı.
- `ViopActivity.kt` geçmişi yetersizse fiyat/analiz ayrımı devam eder.

## 4. Son fiyat tazeliği – ÖNCEKİ SÜRÜMDE MEVCUT
- `ViopScanner.kt` sınırlı eşzamanlı son fiyat yenilemesi ve yeniden giriş/RR kontrolü içerir.

## 5. Lisanslı sağlayıcı – BEKLİYOR
- Sağlayıcı API belgeleri, test erişimi ve veri kullanım lisansı olmadığı için çalışan gerçek adaptör veya fiyat üretilemez.
- `server/viop_gateway.py` erişim anahtarı ve lisanslı adaptör olmadığı durumda kapalı kalır.

## 6. Kesintisiz sunucu pozisyon izleyicisi – BEKLİYOR
- Sunucuda gerçek sürekli akış, kalıcı pozisyon ve olay deposu ile push dağıtımı gerektirir.
- Telefondaki 15 dakikalık WorkManager bu görevin yerini tutmaz.

## Test kanıtı
- Yerel `python -m pytest server/tests -q`: 7/7 başarılı (lisanssız stub üzerinden sözleşme ve güvenlik testleri; **canlı piyasa testi değildir**).
- Kotlin/Android Gradle derlemesi bu ortamda çalıştırılamadı; yeni sınıfın derleme ve çalışma testi gereklidir.
- Üretim sunucusu kurulmadı, gerçek VİOP API bağlantısı yapılmadı, APK üretilmedi.
