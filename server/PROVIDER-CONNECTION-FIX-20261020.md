# BIST/VİOP bağlantı düzeltmeleri ve doğrulama raporu — 20.10.2026

**Sonuç:** Kaynak düzeltmeleri kaydedildi. Tam Android/sunucu doğrulaması, üretim dağıtımı ve gerçek veri bağlantısı tamamlanmış değildir. APK/AAB üretilmedi; cihaz testi yapılmadı.

Kaynak kodun son değişiklik commit'i: `5ba2ad3f8d7675434f8006e52e1d74c75ebcae40`.
Dal: `fix/provider-connection-diagnostics-no-apk`.
İnceleme: [Taslak PR #1](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/pull/1).
Önceki trend rengi/gücü değişikliklerini içeren `7f379721955fdd5388b74c099c2768370087f553` tabanı korunur.
Bu rapor kaynak, test, dağıtım ve cihaz durumlarını ayrı değerlendirir.

## Sorun bazında durum

| Sorun | Yapılan değişiklik / tespit | Durum |
|---|---|---|
| HTTP ve sağlayıcı hatasının kısa raporda kaybolması | HTTP durum kodu, uç nokta, sağlayıcı kodu, hata aşaması, üst sağlayıcı HTTP kodu ve gateway kimlik doğrulama kanıtı taşınır; kalıcı kayda ve raporlara eklenir. | Kaynakta uygulandı; Android çalışması doğrulanmadı. |
| BIST yanıtının VİOP bağlantısını hazır gösterebilmesi | Varsayılan sağlık kontrolü yalnız VİOP; açık BIST/VİOP kapsamları bağımsız; yetkili /v1/readiness iki sonucu ayrı raporlar. | Kaynakta uygulandı; 27 saf mantık testi geçti. |
| VİOP hatası nedeniyle BIST testinin atlanması | BIST testi ayrı çağrılır ve ayrı kaydedilir; ayarlara ayrı BIST testi eklendi. | Kaynakta uygulandı; cihazda doğrulanmadı. |
| Canlı tanılama sonucunun silinmesi | Test düğmesi ayrıntılı sonucu saklayıp gösterir. | Kaynakta uygulandı; cihazda doğrulanmadı. |
| Parçalı HTTP yanıtının eksik okunması | Tek Reader.read çağrısı yerine boyut sınırına/EOF'a kadar okuyan ortak döngü kullanılır. | Kaynakta uygulandı; JVM regresyonları çalışmadı. |
| Bağlantı kurulumu ve bozuk JSON sırasında kanıtın kaybolması | URL/başlık kurulumu try içine alındı; alınan HTTP durumu ayrıştırma/okuma öncesinde tutulur. | Kaynakta uygulandı; JVM regresyonları çalışmadı. |
| Tanı metninde erişim bilgilerinin görünmesi | Bearer/uygulama anahtarı, URL kullanıcı bilgisi ve sorgu değerleri gizlenir; büyük harfli HTTPS de kapsanır. Ham sağlayıcı hata gövdesi rapora kopyalanmaz. | Kaynakta uygulandı; JVM regresyonları çalışmadı. |
| Yanlış hazır/üretim sonucu | Sağlık başarısı fiyat, sözleşme veya işlem doğrulaması yerine geçmez. Veri kaynağı eksikken fiyat ya da başarı üretilmez. | Saf JavaScript testleriyle sınandı. |
| Kaynak kontrolünün APK üretmesi riski | Gradle görev koruması; sadece JVM testleri, Android test kaynağı derlemesi ve kaynak doğrulaması; paket yokluğu kontrolü. | Koruma kaynakta mevcut; bu tur paket üretilmedi. |
| Tam doğrulama işinin başlayamaması | GitHub denemeleri araştırıldı; alternatif Docker/Railway kontrol dosyaları eklendi. | Dış çalışma ortamı engeli sürüyor. |
| Yeni sunucu kodunun canlıya geçmemesi | Railway fonksiyonu tek dosya paket olarak hazırlandı ve bekleyen değişiklikle birebir karşılaştırıldı. | STAGED; canlıda eski sürüm. |
| DATA_SOURCE_NOT_CONNECTED | Gerçek BIST/VİOP üst sağlayıcı ayarlarının bulunmadığı doğrulandı. | Çözülmedi; yetkili sağlayıcı bağlantısı gerekli. |
| Otomatik taramanın tamamlandığının doğrulanamaması | Gerçek veri bağlantısı ve başarılı çalışma kanıtı yok. | Çözülmüş sayılmıyor; SKIPPED_BUSY/PARTIAL cihazda yeniden sınanmalı. |

## Gerçekte çalıştırılan kontroller

- Saf JavaScript tanılama modülünün depodaki gerçek kodu ve test gövdeleri V8 ortamında çalıştırıldı: **27 test geçti, 0 başarısız**. Bu HTTP/Node/Bun entegrasyonu veya Android testi değildir.
- Tek dosyalı Railway paketi, üç kaynak modülüyle ve Railway'deki STAGED fonksiyon koduyla birebir eşleşiyor.
- Değişen kaynaklarda gerçek uygulama erişim anahtarı bulunmadığı ve APK görev korumasının korunduğu denetlendi.
- Yeni CI sonuç raporlayıcısı V8 içinde sentetik kanıtla 8 kabul/ret senaryosunda kontrol edildi: geçerli kanıt kabul edildi; başarısız/atlanmış/eksik JVM sonuçları, APK varlığı, eksik commit, Node başarısızlığı ve eksik Node özeti reddedildi. Bu kontrol uygulama testlerinin geçtiği anlamına gelmez.

**Çalıştırılamayanlar:** Toplam 20 yeni JVM regresyonu ve tam JVM tabanı, yerel Node/Bun sunucu testleri, Android derlemesi, cihaz testleri ve gerçek sağlayıcı uçtan uca testi.

Eklenen sunucu entegrasyon regresyonu: 8. Tam Node test kümesinde 64 test tanımı, Bun WebSocket kümesinde 1 test vardır.
Tam JVM kontrolü en az 905 test sonucu, sıfır hata/başarısız/atlanmış test bekler.
Bu sayılar başarılı yürütme sonucu değildir.

## GitHub Actions engeli

Son kaynak kontrolü:
[39472937524](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39472937524), commit `5ba2ad3f8d7675434f8006e52e1d74c75ebcae40`.

İş `118801412743`: conclusion=failure, runner_name boş, steps=[].
Herhangi bir test adımı çalışmadı.
Önceki [39471426741](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39471426741) ve [39469411529](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39469411529) denemeleri de adım başlamadan durdu.
Tam günlük alınamadı; GitHub sebebinin kota, ödeme veya kaynak derleme hatası olduğu iddia edilmiyor.

Yerel shell/Gradle/Node/Bun çalışma ortamı da mevcut değil.

## Alternatif doğrulama yolu ve Railway engeli

Kaydedilen dosyalar:

- `ci/railway-source-checks.Dockerfile`
- `ci/railway-source-checks.sh`
- `ci/railway-verification-report.mjs`

Bu yol Node 24, Bun, JDK 17 ve Android SDK 35 ile kaynak kontrollerini çalıştırmak, APK/AAB görevlerini engellemek ve sonuçları commit kimliğiyle raporlamak için hazırlanmıştır.
Gerçek veri sağlayıcısı veya uygulama erişim anahtarı gerektirmez. Docker görüntüsü henüz derlenemedi; başarılı çalışması doğrulanmadı.

Ayrı `bist-viop-source-validation` servisini oluşturma isteği Railway tarafından **Deploys have been paused temporarily** hatasıyla reddedildi.
Ardından servis listesi tekrar okundu: QA servisi oluşmadı; yalnız mevcut gateway bulunuyor.

[Railway'nin resmi durum sayfası](https://status.railway.com/) kontrol anında 20 Ekim için genel yeni derleme duraklatmasını duyuruyor:

- İlk bildirim: 17:03 UTC / Türkiye 20:03.
- Son görülen güncelleme: 17:18 UTC / Türkiye 20:18; duraklatma tüm çalışma alanlarını kapsıyor.
- Halihazırda çalışan servisler bu olaydan etkilenmiyor.
- Duraklatma sırasında tetiklenen dağıtımlar, derlemeler yeniden açıldıktan sonra tekrar tetiklenmeli.

Bu, Railway platformundan gelen bir engeldir; kullanıcı onayı reddi değildir.
GitHub işinin başlamamasına da aynı olayın neden olduğu iddia edilmiyor.

## Canlı ortam ve bekleyen değişiklik

Proje: `BIST-VIOP-API-v1`.
Servis: `bist-viop-gateway-v1`.
Adres: https://bist-viop-gateway-v1-production.up.railway.app

- Mevcut servis Online, 1 çalışan kopya.
- Canlı dağıtım: `d0ce21f6-2dee-4fe4-95db-a746d702721f`; eski `6.3.4-route-fix` kodu.
- Bekleyen patch: `75c20020-6f91-494d-879a-e5dcffbb9555`, STAGED; yalnız gateway kodu değişikliği.
- Yeni paket sürümü: `6.3.35-gateway-diagnostics.1`.
- Üretime dağıtım uygulanmadı. Bekleyen kodun canlıda çalıştığı iddia edilmiyor.
- Eski uygulama HTTP 503 hatası ile bugünkü Railway derleme arızası farklıdır: eski gateway, sağlayıcı bağlı olmadığı için uygulama düzeyinde 503 döndürür.

## Gerçek veri bağlantısı için eksik olanlar

İncelenen üretim servisinde yalnız `BORSA_GATEWAY_APP_KEY` özel değişkeni yapılandırılmış.
`BORSA_BIST_UPSTREAM_URL` ve `BORSA_VIOP_UPSTREAM_URL` yok.

Bu nedenle uygulamanın gateway anahtarını tekrar girmek gerçek piyasa veri kaynağını bağlamaz.
Yetkili sağlayıcının v1 sözleşmesini sunan HTTPS uç noktaları ve gerekiyorsa bu sağlayıcının erişim bilgileri yapılandırılmalı.
Gateway'nin kendi adresi üst sağlayıcı olarak kullanılamaz; kaynakta buna karşı koruma eklendi.
Gerçek sağlayıcı, sözleşme, fiyat veya canlı işlem başarısı uydurulmadı.

## Tamamlanma koşulları

1. Bir doğrulama ortamında APK üretmeden tam Node/Bun/JVM testleri ve Android derleme kontrolleri başarılı olmalı.
2. Sonuçlar kaynak commit kimliğiyle kaydedilmeli; eksik/atlanmış test ve APK/AAB bulunmamalı.
3. Railway derlemeleri açıldıktan ve kaynak doğrulaması tamamlandıktan sonra yeni gateway kodu yayımlanıp canlı sürüm okunarak doğrulanmalı.
4. Yetkili BIST/VİOP sağlayıcı ayarları tamamlanmalı; sağlık, kimlik doğrulama, sembol/sözleşme, fiyat ve geçmiş ayrı ayrı sınanmalı.
5. Otomatik tarama ve uygulama görünümü cihazda doğrulanmalı. APK üretimi için kullanıcının sonraki talimatı beklenmeli.

Bu koşullar sağlanmadan productionReady/liveTradingVerified=true, tüm testler geçti veya tüm sorunlar çözüldü sonucu verilemez.
