# PROVENANCE_REPORT — V6.2.6 / build 626

| Alan | Gerçek değer |
|---|---|
| SOURCE_BASE_FILE | BIST-VIOP-V6.2.4-BUILD624-TAM-KAYNAK(1).zip |
| SOURCE_BASE_SHA256 | ee7429d3ebd07525422279c6b1f594ba2aa8c7a41afc4338d669931a83357fc0 |
| GIT_COMMIT_SHA_START | 47f4335541bb66ab88737fbecfa1d25cd0ab32b9 |
| GIT_COMMIT_SHA_FINAL | 2a68959e8ffe4a1bd280abb9dfc174133e5e6149 |
| Çalışma dalı | fix/v6.2.6-data-pipeline |
| GitHub deposu / uzak commit | Bu çalışma için kaydedilmedi / yüklenmedi |
| Actions çalıştırma kimliği / bağlantısı | YOK — çalıştırılmadı |
| APK | BIST-VIOP-V6.2.6-DUZELTME-TANILAMA.apk |
| APK boyutu | 8622088 bayt |
| APK SHA-256 | c7d3bc71995ea928a44d82e2b45f0a4ade778e8a03e19e7ba058998fe89cd618 |
| Test sonucu | 730/730 PASS |
| İmzalama | Android Debug, RSA 2048, APK v2 doğrulandı; release değildir |
| Yeni imza sertifikası SHA-256 | 7d7d96696b2d6620ec480708ba3d4362af6c7f7531906cfd894d004e62b7b2c9 |
| Yüklenen eski APK sertifikası SHA-256 | 75980c804cb8c5d6d39a041ff3f844b9719389ddf3bd669d835835d11a1aa2d1 |

Bu işin tabanı kullanıcının bu konuşmada sağladığı V6.2.4/build624 dosyasıdır. Önceki V5.4.1'e geri dönülmedi. Eski sürümlerin tam kaynak zinciri veya bu ZIP'in eski APK'yı üreten commit olduğu ayrıca doğrulanmış değildir.

Commit kimlikleri yerel kaynak denetimine aittir; GitHub'a push veya Actions çalışması olarak sunulmaz. Kaynak kod ve test değişiklikleri son committe bulunur. Derleme raporları ve doğrulama kayıtları paketleme sırasında eklenmiştir. Kaynak ZIP'in son SHA-256 değeri döngüsel öz-hash oluşmaması için dışarıdaki SHA256SUMS.txt dosyasındadır.

Özgün V6.2.4 imzalama anahtarı verilmedi. Sertifikalar farklı olduğundan yeni paket kimliği tr.borsatakip.v5.diagnostic.v626fix seçildi. Bu bir yerinde güncelleme değildir: eski uygulama ve verileri korunur, yeni uygulama kendi ayarlarıyla başlar. Özel anahtar, parola veya API anahtarı kaynak paketine/rapora eklenmedi.

Açık kalan doğrulamalar: telefonda kurulum, Android bildirim izni ve kanal ayarları, otomatik tarama anahtarı, pil/arka plan teslimi, yeni gerçek sembol kapsamı, canlı backend ve API erişimi, VİOP sözleşme/quote/geçmiş zinciri, 24/48/72 saat süreklilik. Bu sonuçlar başarı olarak işaretlenmedi.

V6.2.6 çalışma dizini taşındıktan sonraki ilk denemede KSP üretilmiş dosya önbelleği çakıştı (FileAlreadyExistsException). Yalnız üretilen app/build ve proje .gradle önbelleği temizlendi; son temiz derlemede 730/730 test ve APK görevi geçti.
