# Kaynak ve kanıt kimliği — 21.10.2026

- `SOURCE_BASE_SHA256=84ba19017248e374b1087fa37667ae75ef90927cd2358c3786681bcd7ec40da3`
- `GIT_COMMIT_SHA_START=4201b7160d96ed2d35c7f1842d8611b5cc8fe2fc`
- `GIT_COMMIT_SHA_FINAL=3e84542353ad1ddd0641c579d5636ac6103c0e42` — doğrulanan uygulama/test/CI kaynağı
- Depo: `ferittasbek-png/BIST-VIOP-V6.2.2`
- Dal: `fix/deep-audit-no-apk`; PR #2 taslak, ana dala birleştirme yok
- Başlangıç: 614/614 Git blob eşleşmesi; son kaynak: 618/618 Git blob ve SHA-256 eşleşmesi
- Değişen 25 dosya uzak commit'ten tam içerik geri okumasıyla eşleşti
- JVM 983/983, 157 suite; Lint 0 hata/fatal, 774 uyarı; Android test class 53, cihazda yürütme yok
- Düzeltme öncesi seçili 23 testte 4 beklenen başarısız regresyon; son kümede 0 başarısız
- Actions: https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39588492586 — failure, runner 0, adım 0
- APK/AAB adı/boyutu/SHA-256: uygulanamaz, kullanıcı talimatıyla üretilmedi
- İmzalama: yapılmadı; production dağıtımı: yapılmadı
- Fiziksel cihaz ve lisanslı canlı sağlayıcı: doğrulanmadı

Başlangıç ZIP'i güncel Git snapshot'ının paketidir. Dosyalar yalnız mevcut commit'in Git blob kimliğiyle eşleştiğinde kabul edildi; eski sürüm kaynağına geçilmedi. Tarihsel Android klasörleri ve eski kök ZIP'ler güncel teslim paketine eklenmez. Gradle Wrapper JAR'ı başlangıç kaynağıyla aynı, Gradle dağıtımı 8.10.2 ve resmi SHA-256 eşleşmesi doğrulandı.

Raporların sonraki commit'i uygulama kodunu değiştirmez. Bu rapor kendini içeren commit'in özetini kendine yazamaz; gerçek teslim commit'i ve oluşturulan ZIP ad/boyut/SHA-256 değerleri, paketleme sonrası `PROVENANCE-DELIVERY.json` ve `SHA256SUMS.txt` içinde kaydedilir. Kaynak ZIP'i GitHub'a kaydedilmiş dosyaları, test kanıtı ZIP'i gerçek derleme/test çıktılarını taşır; kaynak ZIP'i APK veya Actions artefaktı olarak sunulmaz.

774 uyarı açık kalır. Yeni suppression/baseline eklenmedi. 44 uyarı kodda giderildi; önceki bir bağımlılık önerisinin bu turda görünmemesi bağımlılık düzeltmesi değildir.
