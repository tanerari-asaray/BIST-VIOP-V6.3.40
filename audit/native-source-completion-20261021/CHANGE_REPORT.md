# CHANGE_REPORT — 21.10.2026

Doğrulanan kod: `e78aa12b20a212475213d46f3c027a08e2935ee4`. Ayrıntılı önceki bulgu açıklamaları: [20 Ekim düzeltme raporu](../DEEP-FIX-VALIDATION-20261020.md).

| Alan | Son davranış ve nedeni | Doğrulama |
| --- | --- | --- |
| F01 Tarama kilidi | İş başlamadan iptal edilse de tamamlanma işleyicisi kirayı bırakır. | LeasedBistJobTest |
| F02 HTTP taşıma | Gövde okuma/başlatma/kapatma hataları isteği sonlandırır; toplam süre, iptal ve boyut sınırı uygulanır. | BackendHttpTransportTest |
| F03 Bağlantı testi | Test sahipliği ve süre sınırı; eski TESTING kaydı toparlanır. | ProviderProbeGateTest; cihaz senaryoları yalnız derlendi |
| F04 Ayar anlık görüntüsü | URL/anahtar/revision birlikte alınır; eski sonucun yeni ayara kaydı engellenir. | BackendUrlPolicyTest, ProviderProbeGateTest; gerçek prefs/Keystore yürütmesi bekliyor |
| F05 Otomasyon | İki rapor ortak COMPLETED kuralını kullanır. | AutoScanCompletionTest |
| F06 Rapor gizleme | JSON nesne/dizi olarak temizlenir; serileştirme yapıyı korur. | ReportRedactorTest, DeepAuditJsonRedactionTest |
| F07 SHORT MFE | Üç hesap noktasında payda giriş fiyatıdır; 100→80 sonucu %20. | DeepAuditShortExcursionTest |
| F08 HTTP tanılama | HTTP kodu, uç nokta, izin verilen sağlayıcı aşaması/upstream kodu korunur; uygulama/sağlayıcı kimlik hataları ayrılır. | BackendHttpDiagnosticTest, BackendHttpTransportTest |
| F09 VİOP eskime | Liste ve sayaçlar aynı saatle yeniden hesaplanır; kaydırma korunur. | ViopDashboardPolicyTest |
| F10 URL | Ortak HTTPS/canonical adres ve uç nokta kuralları kullanılır. | BackendUrlPolicyTest; beş gerçek Gradle release kontrolü |
| F11 Tarama kaydı | Kaynak kimliği, revision, mod ve yaş uyuşmayan checkpoint devam ettirilmez. | ObservationSourcePolicyTest |
| Native derleme | FullUniverseScanActivity içindeki üç paket adı gölgelenmesi giderildi. | Gerçek Kotlin derlemesi |
| Gövde/ağ tanılama | İçteki SocketTimeout/DNS/TLS/IO nedeni korunur; HTTP 200 alınıp gövde okunamaması başarı sayılmaz. | İki ek JVM regresyonu |
| Release URL | Query, port 0, dot path ve büyük harfli localhost reddi ortak politikayla tutarlı. | Beş Gradle olumsuz/kontrol senaryosu |
| Eski test sözleşmeleri | B115/B116 ortak BackendUrlPolicy ve anahtar kontrolünü arar. | İki eski testi gerçek davranış sözleşmesine uyarlayan 963/963 paket |
| Android görünüm | HTTPS tarayıcı görünürlüğü, yatay kaydırma çocuk genişliği, VİOP özet görünüm erişimi ve FavoriteRepository Context tutma iyileştirildi. | Kotlin/kaynak derlemesi; hedeflenen yedi Lint uyarısı giderildi |
| Room kod üretimi | `room.generateKotlin=true`; Java gölge dosyası çakışmasının yaşandığı yol kullanılmaz. Runtime/şema sürümü korunur. | Temiz derleme + zorunlu tekrar; iki dosyada aynı SHA-256 |
| Test/CI girdileri | Kaynak okuyan testler src/main ve Gradle dosyalarını girdi sayar; CI Lint'i ve yeni test eşiğini denetler. | Varsayılan Gradle turu + ci-verify-source-only.py |

18 güncel dosyanın tam listesi ana JSON raporundadır. Önceki bulguların fiziksel cihaz doğrulaması kapsam dışında tutuldu; test sınıfı adlarının listelenmesi bu cihaz testlerinin çalıştığı anlamına gelmez.

Room Kotlin üretimi mevcut 2.6.x sürümünde desteklenen seçenektir: [resmî AndroidX Room sürüm notları](https://developer.android.com/jetpack/androidx/releases/room#version_260).
