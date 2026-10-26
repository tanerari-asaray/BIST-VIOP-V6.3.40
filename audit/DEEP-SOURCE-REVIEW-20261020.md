# Derin kaynak kodu incelemesi ve test raporu

İnceleme tarihi: 20.10.2026. Depo: `ferittasbek-png/BIST-VIOP-V6.2.2`.
İncelenen uygulama kaynağı: `e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5`.
Denetim dalı: `audit/deep-source-review-20261020`.
Yeni JVM regresyonlarının commit'i: `8640eb01b7b1de777f6b2edeb2e78e6415f3c35a`.
Denetim çekirdeği ve CI denemesinin commit'i: `b9b10c213cba96bb8254b5c13539120bf5ec2cf6`.

## Sonuç

**11 bulgu kaydedildi: 4 yüksek öncelikli (P1), 7 orta öncelikli (P2).** Uygulama için hatasız veya üretime hazır sonucu verilemez.
Kaynakta açık kontrol akışı sorunları var. JSON ve SHORT MFE karşıörnekleri, kaynak desenleri/ifadeleri V8 ortamında değerlendirilerek gösterildi. Android üzerinde çalıştırılarak yeniden üretim yapılmadı.
Bu turda uygulama davranışını değiştiren düzeltme yayımlanmadı; denetim dosyaları ve beş JVM regresyon testi ayrı dala eklendi. Bulgular açıktır.
**APK/AAB üretilmedi. Cihaz/emülatör testi yapılmadı. Ana Railway servisine dağıtım uygulanmadı.**

## Kapsam ve kanıtların anlamı

- 498 metin dosyası alındı; SHA ve yolları `DEEP-AUDIT-SCOPE-20261020.json` içinde kayıtlı.
- Güncel Android modülünde 244 üretim Kotlin dosyası ve 34.273 kaynak satırı, 145 JVM test dosyası, 14 Android test dosyası bulunuyor.
- Üretim kaynaklarında desen taraması yapıldı; bağlantı/sağlayıcı, coroutine iptali, tarama kilidi, kalıcılık, otomasyon, trend sunumu, MFE ve rapor akışları elle izlendi. Dosyaların alınması her satırın elle incelendiği anlamına gelmez.
- Önceki kaynakların 905 JVM test tanımı mevcut; bu denetimle beş regresyon eklendi. 910 test tanımı başarılı yürütme sayısı değildir.
- 39 test dosyası kaynak dosyalarının metnini okuyor; bu dosyalarda toplam 210 test tanımı var. Bu sayının tümü davranış testi sayılmaz; dosya düzeyinde kaynak kontrolü ile gerçek ağ/UI testini ayırmak gerekir.

| Kanıt türü | Bu raporda anlamı |
|---|---|
| Gerçek JavaScript mantığı yürütüldü | Depodaki `gateway_diagnostics.mjs` fonksiyonları V8 içinde çalıştırıldı. |
| Kaynak ifadesi deneyi | Kotlin dosyasındaki aynı regex veya aritmetik ifade V8 ile değerlendirildi; Kotlin/JVM çalıştırılmadı. |
| Statik çağrı zinciri | Kaynakta ilgili yol ve tetikleyici izlendi; cihazda görülmüş olay olduğu iddia edilmez. |
| JVM regresyonu eklendi | Test kodu kaydedildi; bu oturumda Gradle/JVM çalışması başlamadı. |

## Test ve kontrol sonuçları

| Kontrol | Sonuç | Sınır |
|---|---|---|
| Mevcut saf JS tanılama testleri | 27 geçti, 0 başarısız | V8; Node/Bun/HTTP/Android değil. |
| Piyasa sağlık matrisi | 108 senaryo, 0 ihlal | 3 kapsam × 6 BIST durumu × 6 VİOP durumu. |
| Sağlayıcı HTTP kod matrisi | 400 senaryo, 0 ihlal | BIST ve VİOP için 400–599; gerçek HTTP isteği yok. |
| JSON maskeleme ifadeleri | 4 senaryodan 2'sinde JSON yapısı bozuldu | Gerçek desenlerin V8 karşıörneği; JVM değil. |
| SHORT MFE ifadeleri | İki motorda %20 yerine %25 | Gerçek aritmetik ifadelerin V8 karşıörneği. |
| Otomatik durum sözleşmesi | Raporun beklediği değer worker enum'unda yok | Kaynak sözleşmesi kontrolü. |
| Activity/manifest taraması | 88 başvuruda eksik bildirim bulunmadı | Ekranın açıldığı veya tuşların çalıştığı testi değil. |
| Kaynak ID/yerel kaynak taraması | Eksik başvuru bulunmadı | `app_name` Gradle resValue ile üretiliyor; Android kaynak derlemesi değil. |
| Tam Node/Bun, JVM ve Android derlemesi | Çalıştırılamadı | GitHub işi adım başlamadan başarısız; Railway repo erişimi reddedildi. |

Sağlık matrisinde sağlıklı, yapılandırılmamış, sağlayıcı kimlik doğrulama reddi, ağ hatası, bozuk JSON ve HTTP 200/ok=false durumları kullanıldı. Diğer piyasanın çağrılmadığı, sırların rapora taşınmadığı ve sağlık başarısından işlem/fiyat doğrulaması türetilmediği denetlendi.

## F01 — P1: İş başlamadan iptalde tarama kilidi sızıyor

Konum: [FullUniverseScanRuntime.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/FullUniverseScanRuntime.kt#L29); [BistScanGate.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/scan/BistScanGate.kt#L20).

`BistScanGate.tryAcquire` coroutine başlatılmadan önce çağrılıyor. Kilit sadece coroutine gövdesindeki `finally` içinde bırakılıyor. `cancel()` işi IO çalıştırıcısı gövdeye girmeden iptal ederse bu `finally` hiç kurulmaz.
Kullanılan kotlinx-coroutines 1.9.0'ın DEFAULT başlangıç sözleşmesi bu duruma izin verir: [1.9.0 CoroutineStart kaynağı](https://github.com/Kotlin/kotlinx.coroutines/blob/1.9.0/kotlinx-coroutines-core/common/src/CoroutineStart.kt).

**Etki:** Aynı süreçte sonraki BIST/evren taramaları sürekli meşgul görünebilir. Bu, kullanıcı raporundaki SKIPPED_BUSY olayının kesin nedeni olarak sunulmuyor; yeni bulunan bağımsız bir kilit sızıntısı yoludur.

Yeniden üretim planı: kontrollü dispatcher ile işi kuyruğa al, gövde başlamadan iptal et, kuyruğu ilerlet ve `BistScanGate.snapshot()` değerinin null olmasını doğrula.
Düzeltme: kirayı coroutine içindeki korumalı bölgede edinmek veya gövde hiç başlamasa da çalışan bir tamamlama temizliği kurmak; ilk kurulum hatalarını da aynı temizleme kapsamına almak.
Kanıt: statik kontrol akışı + kullanılan kütüphanenin sözleşmesi. Native yürütme yok.

## F02 — P1: BIST gövde okuma hatası bekleyen işi tamamlamayabiliyor

Konum: [MobileMarketDataProvider.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/MobileMarketDataProvider.kt#L286); [MobileMarketDataProvider.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/MobileMarketDataProvider.kt#L59); [BistScanner.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/scan/BistScanner.kt#L185).

`onResponse` içinde `body.string()` try/catch dışında. Başlık geldikten sonra gövde kopar veya okuma zaman aşımı oluşursa bu callback yolu continuation'ı başarı/hata ile tamamlamadan çıkabiliyor. `listSymbols/loadSymbols` çevresinde coroutine için ayrı toplam süre sınırı yok; OkHttp'nin ağ süresi sınırı continuation'ın tamamlandığı anlamına gelmez.

**Etki:** Sembol listesi aşaması bekleyebilir; tarama kirayı tutuyorsa sonraki taramalar meşgul kalır.
Yeniden üretim planı: yanıt başlıklarını gönderen, sonra gövdeyi kesen bir sahte sunucu ile `listSymbols` çağır; süre sınırı içinde hatayla dönmesini ve tarama kilidinin bırakılmasını doğrula.
Düzeltme: callback'in gövde okuma/kapama hata yollarını korumak, her yolda continuation'ı bir kez tamamlamak, çağrı zincirine toplam zaman sınırı koymak ve gövde boyutunu okuma sırasında sınırlamak.
Kanıt: statik callback yolu. Ağ kopması testi bu oturumda çalıştırılmadı.

## F03 — P1: İptal edilen sağlayıcı testi kalıcı TESTING bırakıyor

Konum: [ProviderReadinessService.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/ProviderReadinessService.kt#L146); [ViopActivity.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt#L231); [ViopActivity.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt#L347).

`test()` ağ çağrısından önce `PROVIDER_TESTING` durumunu kalıcı ayara yazıyor. İptal/istisna yolunda durumu temizleyen finally yok. `localConfigState()` bu durumu süre sınırı olmadan tekrar okuyor. VİOP ekranının toparlama yolu TESTING'i yeniden sınamıyor ve tarama düğmesi TESTING sırasında devre dışı.

**Etki:** Ekran kapanırken, test iptal edilirken veya süreç sonlanırken uygulama tekrar açıldığında sağlayıcı süresiz test ediliyor görünebilir.
Yeniden üretim planı: testte sağlık/sözleşme yanıtını beklet, ekranı kapat veya coroutine'i iptal et, yeni servis örneği oluştur ve durumun tekrar test edilebilir olmasını doğrula.
Düzeltme: test kimliği ve başlangıç zamanı saklamak; iptal/istisnada doğru yapılandırmanın durumunu geri almak; eski TESTING kaydı için toparlama sınırı eklemek.
Kanıt: kalıcı durum ve lifecycle çağrı zinciri. Cihazda yeniden üretim yok.

## F04 — P1: Yapılandırma değişikliği ile çalışan test arasında yarış var

Konum: [BackendPreflightClient.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/BackendPreflightClient.kt#L115); [BackendPreflightClient.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/BackendPreflightClient.kt#L212); [ProviderReadinessService.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/ProviderReadinessService.kt#L196); [SettingsStore.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/SettingsStore.kt#L26).

BIST testi base URL'yi başlangıçta alıyor; her HTTP isteği Authorization başlığını değişebilir SettingsStore'dan tekrar okuyor. Test sonucu, yapılandırmanın sürümü/origin'i hâlâ aynı mı kontrol edilmeden kaydediliyor. Ayarı değiştirmek geçmiş gözlemleri temizliyor fakat hâlen çalışan eski istekleri ve onların geç yazmasını engellemiyor.

Somut zamanlama: A sunucusunda sağlık isteği beklerken kullanıcı B adresini ve B anahtarını kaydeder; A sağlık yanıtı geldikten sonra eski A base değeriyle yapılan preflight isteği B anahtarını okuyabilir. Aynı origin'de anahtar değişiminde eski test sonucu yeni ayarın readiness kaydını da geri yazabilir.
**Etki:** Yanlış bağlantı kanıtı; eşzamanlı origin/anahtar değişikliğinde yeni anahtarın eski adrese gönderilmesi riski. Bu oturumda gerçek anahtar sızıntısı gözlenmiş değildir.
Düzeltme: origin, anahtar ve yapılandırma revizyonunu atomik bir istek bağlamında almak; adımlar boyunca aynı bağlamı kullanmak; sonuç kaydında revizyon eşleşmiyorsa sonucu atmak ve eski işleri iptal etmek.
Test: iki origin ve bekletilebilir sahte transport ile adımlar arasında ayar değiştir; başlık/origin eşleşmesini ve eski sonucun yeni durumu değiştirmediğini doğrula.
Kanıt: statik eşzamanlı işlem sırası. Güvenlik önceliği nedeniyle native testle kapatılmadan yayımlanmamalı.

## F05 — P2: Başarılı otomasyon bile doğrulanmadı uyarısı üretiyor

Konum: [AutomaticScanRunner.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/worker/AutomaticScanRunner.kt#L29); [SettingsDetailActivity.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/SettingsDetailActivity.kt#L189); [SystemReportV2.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/SystemReportV2.kt#L86).

Worker sonucunu `Outcome.COMPLETED.name` olarak saklıyor. Kısa ve ayrıntılı rapor `COMPLETE` ile karşılaştırıyor. COMPLETE, worker'ın olası sonuçlarının hiçbirinde yok.
**Etki:** Otomatik tur gerçekten tamamlansa da AUTO_SCAN_UNVERIFIED/AUTO_SCAN_NOT_VERIFIED uyarısı oluşabilir. Bu yazım hatasını düzeltmek PARTIAL veya SKIPPED_BUSY durumlarının gerçekten tamamlandığını kanıtlamaz.
Düzeltme: tipli/ortak durum sözleşmesi kullanmak; tam başarıyı COMPLETED, kısmi sonucu PARTIAL olarak ayrı değerlendirmek.
Test: worker'ın tüm sonuçlarını rapora ver; COMPLETED için uyarı üretme, PARTIAL/NO_DATA/SKIPPED_BUSY için doğru ayrı açıklamayı koru.
Kanıt: çalıştırılmış kaynak sözleşmesi kontrolü.

## F06 — P2: Hata metni maskelenirken JSON kapanışı silinebiliyor

Konum: [SystemReportV2.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/SystemReportV2.kt#L596); [SettingsDetailActivity.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/SettingsDetailActivity.kt#L252).

Serileştirilmiş JSON'a uygulanan genel anahtar regex'inde değer kısmı çift tırnak ve kapanış süslü parantezini de tüketiyor. Bu yüzden sır gizlenirken dosyanın JSON yapısı bozuluyor.

Sentetik karşıörnek:

```json
{"message":"Authorization: Bearer SYNTHETIC_AUDIT_TOKEN_123"}
```

Kaynak deseninin ürettiği çıktı:

```text
{"message":"Authorization: [GİZLENDİ]
```

`api_key=synthetic_audit_key_123` içeren mesaj da aynı şekilde bozuluyor. Normal JSON ve doğrudan bilinen anahtar değiştirme kontrolü yapıyı koruyor.
**Etki:** Dışa aktarılan rapor JSON olarak okunamayabilir; bağlantı tanısı için gerekli dosya kullanılamaz.
Düzeltme: JSON ağacındaki string/değer alanlarını maskelemek ve sonra serileştirmek; TXT maskelemesini ayrı ele almak; son çıktıyı gerçek JSON ayrıştırıcısıyla kontrol etmek.
Üç JVM testi eklendi: Authorization mesajı, api_key mesajı ve normal JSON kontrolü. Henüz çalıştırılmadı.
Kanıt: aynı regex desenleriyle V8 karşıörneği; Kotlin/JVM sonucu değil.

## F07 — P2: SHORT MFE yüzdesi yanlış paydadan hesaplanıyor

Konum: [TradeForwardEngine.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/analysis/TradeForwardEngine.kt#L94); [V540BacktestEngine.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540BacktestEngine.kt#L390); [V540BacktestEngine.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540BacktestEngine.kt#L396).

SHORT lehine fiyat hareketi `(entry / low - 1) * 100` ile hesaplanıyor. Giriş nominaline göre hesaplanan getiri ile tutarlı oran `(entry - low) / entry * 100` olmalı.

| Giriş | En düşük fiyat | Kodun MFE sonucu | Giriş nominaline göre sonuç |
|---:|---:|---:|---:|
| 100 | 80 | %25 | %20 |

**Etki:** Forward sonuçlarına yazılan MFE ve backtest ortalama MFE/karşılaştırma ölçümleri şişebilir. Bu bulgu MFE metriğine aittir; ayrı hesaplanan her P/L alanının yanlış olduğu sonucu çıkarılmamalı.
Düzeltme: iki motordaki normal ve intrabar SHORT excursion hesaplarını ortak yönlü getiri yardımcı fonksiyonuyla tutarlı hale getirmek.
İki JVM regresyonu eklendi: gerçek TradeForwardEngine ve V540BacktestEngine için 100→80 SHORT hareketinde %20 beklentisi. Henüz çalıştırılmadı.
Kanıt: gerçek kaynak aritmetik ifadelerinin V8 değerlendirmesi.

## F08 — P2: Önceki HTTP tanı düzeltmesi gerçek BIST istemcisine tam yayılmamış

Konum: [MobileMarketDataProvider.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/MobileMarketDataProvider.kt#L302); [market_proxy.ts](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/server/market_proxy.ts#L94).

Gateway, BIST üst sağlayıcının HTTP 401/403 hatasını PROVIDER_AUTH kanıtıyla döndürebiliyor. Ancak MobileMarketDataProvider durum kodunu görüp tamamını uygulama/backend kimlik doğrulama hatası olarak sınıflandırıyor; gövdedeki stage, providerCode ve upstreamHttpStatus kayboluyor.
**Etki:** Sağlık/preflight başarılı olup quote/history için sağlayıcı yetkisi reddedildiğinde kullanıcı yanlışlıkla uygulama anahtarını değiştirmeye yönlenebilir. BIST tarama hatalarının ayrıntıları hâlâ eksik kalır.
Düzeltme: BIST'in gerçek veri istemcisinde de ortak BackendHttpEvidence/typed hata yolunu kullanmak; gateway ve sağlayıcı kimlik doğrulamasını ayrı tutmak.
Test: aynı HTTP 401 ile INVALID_GATEWAY_APP_TOKEN ve BIST_UPSTREAM_HTTP_401 yanıtlarını ver; farklı hata aşamalarını ve kalıcı rapordaki kanıtı doğrula.
Kanıt: istemci/sunucu hata sözleşmesi karşılaştırması. Native HTTP testi yok.

## F09 — P2: VİOP canlı aday sayaçları yaşlanınca güncellenmiyor

Konum: [ViopActivity.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt#L55); [ViopActivity.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt#L585); [ViopActivity.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt#L653).

shortlist bir kez hesaplanıp productionItems'a atanıyor. On saniyelik yenileme adapter satırlarını ve provider başlığını yeniliyor; güncelliğe göre shortlist ve LONG/SHORT toplamlarını yeniden hesaplamıyor. Satır yeniden bağlanınca assessment eski fiyatı reddedebilir, fakat özet önceki sayıyı taşır.
**Etki:** Aynı ekranda satır İZLE/ESKİ KAYIT olurken üstte doğrulanmış aday sayısı eski kalabilir; yön filtresi de eski aday kümesini kullanır.
Düzeltme: satır, filtre ve özetleri aynı now/snapshot ve güncellik değerlendirmesinden üretmek; sabit listeyi yalnız yeniden boyamakla yetinmemek.
Test: bir doğrulanmış adayla ekranı kur; saati 60.001 ms ilerlet; satır, filtre, özet ve sinyal seçicinin tutarlı davranmasını doğrula.
Kanıt: statik UI yenileme zinciri. Ekran testi yapılmadı.

## F10 — P2: URL doğrulaması ile URL'yi kullanan kod uyuşmuyor

Konum: [ProviderReadinessService.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/ProviderReadinessService.kt#L216); [BackendPreflightClient.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/BackendPreflightClient.kt#L305); [BackendProvider.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/BackendProvider.kt#L79); [ProviderRouter.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/ProviderRouter.kt#L46).

Doğrulayıcı HTTPS şemasını büyük/küçük harften bağımsız kabul ediyor; pek çok gerçek istemci yalnız `startsWith("https://")` kullanıyor. Ayrıca sorgu içeren base URL reddedilmiyor, ancak uç noktalar base + path ile birleştiriliyor.
Örnek: `HTTPS://example.invalid` ayarlarda geçerliyken gerçek veri istemcisinde yapılandırılmamış sayılabilir. `https://example.invalid?tenant=one` üstüne /v1/health eklendiğinde yol sorgunun parçası olur.
**Etki:** Geçerli görünen ayarların farklı istemcilerde farklı davranması ve yanlış uç nokta çağrıları.
Düzeltme: tek bir canonical base URL doğrulayıcısı ve URL kurucu kullanmak; kabul edilmeyen query/fragment/userInfo bileşenlerini reddetmek veya destekleniyorsa açıkça birleştirmek; şemayı normalize etmek.
Test: büyük harfli HTTPS, boşluk, port, son slash, path, query, userInfo ve fragment için tüm istemcilerde aynı kabul/ret tablosunu çalıştırmak.
Kanıt: statik doğrulama ve birleştirme sözleşmesi. Native URI/istemci testi yok.

## F11 — P2: Devam kaydı başka backend altında yeniden kullanılabiliyor

Konum: [ObservationCheckpoint.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/data/ObservationCheckpoint.kt#L13); [FullUniverseScanRuntime.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/FullUniverseScanRuntime.kt#L62); [FullUniverseScanRuntime.kt](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/blob/e9f32f7c9a84e89ae4c0b16006d0196f2b17d0b5/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/FullUniverseScanRuntime.kt#L99).

Checkpoint yalnız local bayrağı, evren, satırlar ve zamanı taşıyor; backend origin'i veya yapılandırma revizyonu yok. Devam koşulu localMode ve altı saatlik yaşa bakıyor. Backend değiştirildiğinde bu kayıt temizlenmiyor; saved.universe kullanıldığında yeni sağlayıcının BIST evreni tekrar keşfedilmiyor.
**Etki:** Önceki sağlayıcının evreni ve satırları yeni sağlayıcıyla aynı devam turunda birleşebilir; yeni kaynağın keşfedilen kapsamı doğru ölçülmez.
Düzeltme: checkpoint'e sır içermeyen kaynak/yapılandırma kimliği eklemek; eşleşmeyen kaydı devam için reddetmek ve yeni evren keşfi yapmak. Kullanıcı eski sonuçları ayrıca tarih/kaynak etiketiyle görüntüleyebilir.
Test: A kaynağında kısmi kayıt oluştur, B kaynağına geç, devam et; A kaydının B taramasının keşfi/başarı sayacına katılmadığını doğrula.
Kanıt: kalıcılık ve devam çağrı zinciri. Native devam testi yok.

## Test altyapısının güncel durumu

Yerel shell/Gradle/Node/Bun çalışma ortamı mevcut değil.

Yeni denetim dalı için kaynak-only CI tekrar tetiklendi:
[GitHub Actions 39498021427](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39498021427).
İş: `118888032463`; conclusion=failure, runner_name boş, steps=[]. **Hiçbir test adımı başlamadı.** Bunun kaynak derleme hatası olduğu veya beş yeni JVM testinin çalışıp başarısız olduğu söylenemez. GitHub'daki asıl çalıştırıcı engelinin ödeme/kota kaynaklı olduğu da doğrulanmadı.

[Railway resmi durum sayfası](https://status.railway.com/) son kontrolde Fully Operational gösteriyor. Önceki genel derleme duraklatması artık güncel engel olarak gösterilmiyor.
`bist-viop-source-validation` adlı boş test servisi oluşturuldu: `d2832e5d-c636-4e25-9e78-3d8e7c1ef8c4`.
Dockerfile yolu ve NEVER yeniden başlatma politikası kaydedildi. Serviste uygulama sırrı veya veri sağlayıcı değişkeni yok.
Ancak kaynak bağlama isteği Railway tarafından `User does not have access to the repo` ile reddedildi. Bu, Railway bağlantısının özel depoya erişim engelidir; GitHub bağlantısının depoyu okuyamadığı anlamına gelmez.
Okunan servis yapılandırmasında kaynak yok; dağıtım listesi boş. Dolayısıyla Railway'de de Gradle/Node/Bun çalışması başlamadı. Yeni genel arıza veya kaynak kodu derleme hatası iddia edilmiyor.

## Canlı veri ve dağıtım durumu

- Ana gateway hâlâ eski `6.3.4-route-fix` kodunu çalıştırıyor.
- `75c20020-6f91-494d-879a-e5dcffbb9555` patch'i STAGED. Bekleyen kod incelenen tek dosya paketiyle eşleşiyor; üretime alınmadı.
- Üretim servisindeki özel değişken yalnız BORSA_GATEWAY_APP_KEY. BORSA_BIST_UPSTREAM_URL ve BORSA_VIOP_UPSTREAM_URL yok.
- Lisanslı veri sağlayıcı uç noktası/erişimi olmadan DATA_SOURCE_NOT_CONNECTED kapanmış sayılmaz.
- Bu denetimdeki yeni hatalar, asıl 503 hatasının kesin nedeni olarak sunulmaz. Asıl bağlantı eksikliği ile kod hataları ayrı işlerdir.

## Düzeltme ve doğrulama sırası

1. F01–F04: kilit, callback tamamlama, test iptali ve yapılandırma revizyonu. İptal/ağ kesilmesi/ayar değişikliği yarışlarını native testlerle doğrula.
2. F05–F08: ortak otomasyon durumu, JSON ağacı üzerinde maskeleme, giriş nominaline göre SHORT MFE ve BIST HTTP kanıtı. Mevcut/yeni regresyonları çalıştır.
3. F09–F11: yaşlanan aday sayaçları, ortak URL kurucu ve kaynak kimlikli devam kaydı. Kontrollü saat ve iki sağlayıcıyla davranış testleri yap.
4. GitHub çalıştırıcı veya Railway özel depo erişimini düzelt; APK üretmeden tam sunucu testlerini, tüm JVM testlerini ve Android test kaynakları/kaynak derlemesini çalıştır.
5. Kaynak doğrulaması sonrası yeni gateway kodunu yayımla ve canlı sürümünü oku; yetkili veri sağlayıcılarını bağlayıp her piyasayı bağımsız sınayarak doğrula.
6. APK üretimi için kullanıcının sonraki talimatı beklenir. Cihaz testi ancak uygun paket ve izinli aşama sağlandığında ayrıca yapılır.

## Tekrar çalıştırma

Denetim dalı proje kökünde Node 24 ile:

```sh
node audit/run-deep-audit.mjs
```

Bu komut gerçek JS mantığını ve raporda açıkça belirtilen kaynak ifadesi/sözleşmesi kontrollerini yürütür. Bilinen bulgular duruyorsa çıkış kodu 1 üretmek üzere tasarlanmıştır. Kotlin derlemesi yerine geçmez.

APK üretmeden native kontrol:

```sh
cd android-v6323
./gradlew --no-daemon --continue -I ../ci-source-only.init.gradle :app:testDiagnosticUnitTest :app:compileDiagnosticAndroidTestKotlin :app:processDiagnosticResources
```

Başarısız testler de dahil sonuçlar `ci/deep-audit-jvm-report.py` ile sınırlandırılmış kanıt olarak raporlanabilir. Bu komutlar bu oturumda native ortamda çalıştırılmış değildir.

Makine tarafından okunabilir kanıt: `DEEP-AUDIT-EVIDENCE-20261020.json`; bulgu dizini: `DEEP-AUDIT-FINDINGS-20261020.json`.
Hiçbir başarı sayısı APK'nın oluşturulduğu, doğru APK'nın doğrulandığı, gerçek cihazda test edildiği veya canlı işlem yetkisinin doğrulandığı anlamına gelmez.
