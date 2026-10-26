# Kaynak kod tamamlama ve doğrulama raporu

**21.10.2026 · V6.3.35 / Build 665 · Kaynak kontrolleri başarılı, uyarılar mevcut.**

Doğrulanan kod: [`e78aa12b20a212475213d46f3c027a08e2935ee4`](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/commit/e78aa12b20a212475213d46f3c027a08e2935ee4).
Çalışma dalı: `fix/deep-audit-no-apk`; [taslak PR #2](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/pull/2).

Önceki 20 Ekim raporunda çalıştırılamayan Android kontrolleri bu çalışmada gerçek JDK/Android SDK ortamında tamamlandı. Önceki rapor tarihsel kanıt olarak korundu. Bu sonuçlar mevcut kaynak içindir; kurulu telefondaki uygulama ayrıca güncellenmedi.

## 1. Başlangıç kaynağı

Devam edilen kaynak PR #2 üzerindeki `4d8e097eee767ef000a0fa0edd8224b685fe4260` sürümüdür. 600 güncel dosyanın Git blob kimliği doğrulandı. Altı tarihsel kök ZIP'i yeni kaynak paketine alınmadı; güncel Android, sunucu, test ve CI dosyaları korundu.

`SOURCE_BASE_SHA256=a9f553e6088f6f183b06b2b9f44b5fb03c13a3c79fef2250baa6cbaf1d56f4d0`

## 2. Son kod kimliği

`GIT_COMMIT_SHA_FINAL=e78aa12b20a212475213d46f3c027a08e2935ee4` doğrulanan uygulama/test/yapılandırma kodunu belirtir. Sonraki rapor commit'i bu kodu değiştirmez. Paketleme sırasında teslim commit'i ayrıca dış provenance dosyasına kaydedilir.

## 3. GitHub'a kaydedilen kaynaklar

Bu tamamlama aşamasında başlangıca göre **18 kaynak/test/CI dosyası** değişti. Dosya listesi [JSON raporunda](NATIVE-SOURCE-COMPLETION-20261021.json), değişiklik nedenleri [CHANGE_REPORT.md](native-source-completion-20261021/CHANGE_REPORT.md) içindedir. Kod commit'lerinin uzak dosyaları geri okunarak eşitlik doğrulandı.

## 4. Uygulanan düzeltmeler

Önceki 11 denetim bulgusuna ait düzeltmeler korundu ve ilgili JVM regresyon paketi gerçek JVM üzerinde çalıştı: tarama kilidi/iptal, HTTP gövde hataları, test sahipliği, sabit bağlantı ayarları, otomatik tarama durumu, JSON gizleme, SHORT MFE, HTTP hata ayrımı, VİOP liste/sayaç tutarlılığı, ortak URL kuralı ve tarama kayıt kimliği.

Yeni native çalıştırma üç gerçek derleme hatasını ortaya çıkardı: yerel `tr` değişkeni paket adını gölgeliyordu. Bunlar import ve doğrudan sınıf başvurularıyla giderildi. HTTP gövde zaman aşımının sınıflandırılması iki yeni testle tamamlandı. İki eski kaynak sözleşmesi ortak HTTPS/anahtar kuralını denetleyecek şekilde güncellendi.

Room'un Kotlin kod üretimi etkinleştirildi. Temiz derleme ve zorunlu tekrar derlemesi geçti; iki üretilmiş dosyanın SHA-256 özetleri aynı kaldı. Room bağımlılığı ve veritabanı şema sürümü değiştirilmedi. Kaynak okuyan testlerin Gradle girdileri de açıkça tanımlandı.

Hisse/VİOP trend sunumu korundu: yön rengi ve güç/parlaklık kararlarını kapsayan `TrendUiPolicyTest` **11/11**, `OpportunityTrendPresentationTest` **8/8** geçti. Ekran görüntüsü ve fiziksel ekran davranışı cihaz üzerinde bu çalışmada doğrulanmadı.

## 5. Gerçek test ve derleme sonuçları

| Kontrol | Sonuç |
| --- | --- |
| Temiz native derleme: JVM, Android test kaynakları, kaynaklar, Lint | PASS · 351,5 sn |
| JVM | **963 geçti; 0 başarısız, 0 hata, 0 atlanan; 155 suite** |
| Android test kaynakları | **53 class derlendi; cihazda çalıştırılmadı** |
| Android Lint | **0 hata / 0 fatal; 1.322 uyarı** |
| Zorunlu KSP tekrarı | PASS · 39,1 sn; iki üretilmiş Kotlin dosyasının özetleri değişmedi |
| Normal tekrar derleme | PASS · 18,0 sn |
| Node entegrasyon/birim testleri | **64/64**, 0 başarısız/atlanan |
| Gerçek yerel Bun WebSocket testi | **1/1**, 5 assertion |
| Sağlık / upstream HTTP matrisi | **108 + 400 senaryo**, 0 hata; kontrollü girdiler |
| Release URL olumsuz/kontrol durumları | **5/5 beklenen sonuç** |
| Railway sunucu bundle eşitliği | PASS · üç modül aynı |
| Doğrulama boyunca kaynak bütünlüğü | **513 girdi değişmedi** |
| APK engeli / APK-AAB araması | PASS · beklenen paketleme reddi / **0 dosya** |

Native test çalıştırması metin/parantez denetiminden ayrıdır. Ayrıntılı komutlar ve kanıt kapsamı [BUILD_REPORT.md](native-source-completion-20261021/BUILD_REPORT.md) içindedir. JSON ve XML kayıtları teslim kanıt paketine dahil edilir.

## 6. APK adı ve SHA-256

**APK/AAB üretilmedi.** Kullanıcının ayrı talimatını bekleme kısıtı uygulandı. APK adı, boyutu ve SHA-256 değeri yoktur. `assembleDiagnostic --dry-run` görevi yürütülmeden `SOURCE_ONLY_APK_BLOCKED` ile reddedildi; bu kontroldeki exit code 1 beklenen başarı koşuludur.

## 7. İmzalama

İmzalama yapılmadı; imzalama anahtarı istenmedi ve kaynak içine kimlik bilgisi eklenmedi.

## 8. GitHub Actions

[Doğrulanan kodun Actions çalışması](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39556552967) **failure** sonucuyla bitti. İş `119077550282`: `runner_id=0`, çalıştırıcı adı boş, **0 adım**. GitHub üzerinde derleyici/test yürütülmedi. Altyapı nedeni kesinleşmedi; yerel native sonuçlar GitHub Actions başarı sonucu olarak sunulmaz.

## 9. Kaynak ve kanıt teslimi

Güncel kaynak çalışma dalında ve PR #2 içindedir. İndirilebilir kaynak ZIP'i ile ham doğrulama ZIP'i ayrı hazırlanır; dış `SHA256SUMS.txt` bu dosyaları doğrular. GitHub Actions artefaktı oluşturulduğu iddia edilmez. [PROVENANCE_REPORT.md](native-source-completion-20261021/PROVENANCE_REPORT.md) kaynak kimliğini ve yetki sınırlarını kaydeder.

## 10. Açık sınırlar ve kalan uyarılar

1. Lint uyarıları: `SetTextI18n=617`, `HardcodedText=350`, `UnusedResources=116`, `SmallSp=87`; diğer kategoriler toplam 152. Bunlar gizlenmedi. Metin kaynakları, erişilebilirlik, çizim ve yerleşim iyileştirmeleri için teknik borç sürüyor.
2. SharedPreferences/Keystore, yaşam döngüsü, gerçek SQLite açılışı ve ekrandaki görünüm cihazda yürütülmedi. Beş yeni bağlantı yaşam döngüsü testi dahil Android test kaynakları derlendi.
3. Paylaşılan `503 / DATA_SOURCE_NOT_CONNECTED` raporu lisanslı veri adaptörü bağlantısının eksik olduğunu bildiriyordu. Bu çalışmada canlı sağlayıcı, güncel sunucu yapılandırması veya gerçek işlem doğrulanmadı. Başarısız sağlık testi kaynakta başarılıymış gibi işaretlenmez.
4. Üretim dağıtımı ve ana dal birleştirmesi yapılmadı. **Kaynak kontrollerinin geçmesi üretime hazırlık veya mutlak hatasızlık garantisi değildir.**
