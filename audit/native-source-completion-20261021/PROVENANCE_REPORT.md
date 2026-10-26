# PROVENANCE_REPORT — kaynak doğrulama

| Alan | Değer |
| --- | --- |
| SOURCE_BASE_SHA256 | `a9f553e6088f6f183b06b2b9f44b5fb03c13a3c79fef2250baa6cbaf1d56f4d0` |
| GIT_COMMIT_SHA_START | `4d8e097eee767ef000a0fa0edd8224b685fe4260` |
| GIT_COMMIT_SHA_FINAL (doğrulanan kod) | `e78aa12b20a212475213d46f3c027a08e2935ee4` |
| Depo | [ferittasbek-png/BIST-VIOP-V6.2.2](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2) |
| Dal | `fix/deep-audit-no-apk` |
| PR | [#2](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/pull/2) · taslak |
| Kaynak | Aktif Build 665 PR kaynağı; 600 Git blob doğrulandı |
| Uzak doğrulama | Kod dosyaları commit üzerinden geri okundu ve eşleşti |
| Native kanıt | 963/963 JVM, derlenmiş 53 cihaz class, Lint 0 hata/1322 uyarı |
| JS kanıt | Node 64/64, Bun 1/1, 508 kontrollü matris senaryosu |
| Actions | [39556552967](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39556552967) · failure, runner 0, steps 0 |
| APK/AAB | Kullanıcı talimatıyla üretilmedi; 0 dosya |
| APK adı/boyutu/SHA-256 | Uygulanamaz |
| İmzalama | Yapılmadı |
| Cihazda yürütme | Yapılmadı; test kaynakları yalnız derlendi |
| Canlı sağlayıcı/işlem | Doğrulanmadı |
| Üretim dağıtımı / ana dala birleştirme | Yapılmadı |

Rapor commit'i kod doğrulamasından sonra oluşturulur. Nihai teslim commit'i ve ZIP özetleri paketlemede oluşturulan dış provenance/SHA256SUMS dosyalarında kayıtlıdır; bu dosya kendi commit kimliğine yönelik döngüsel iddia içermez.

Başlangıç paketinde güncel Android/sunucu/test/CI dosyaları yer alır. Depodaki altı tarihsel kök ZIP'i güncel kaynak paketiyle karıştırılmadı. Derleme klasörleri, SDK/JDK/Gradle önbellekleri, yerel ayarlar ve kimlik bilgileri kaynak paketine alınmaz.

[Değişiklikler](CHANGE_REPORT.md) · [Derleme/test kapsamı](BUILD_REPORT.md) · [Ana rapor](../NATIVE-SOURCE-COMPLETION-20261021.md).
