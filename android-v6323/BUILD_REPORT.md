# BUILD_REPORT — V6.2.6 / build 626

| Kontrol | Gerçek sonuç |
|---|---|
| JDK / Gradle / SDK | JDK 17, Gradle 8.10.2, Android compile SDK 35 |
| `:app:testDiagnosticUnitTest` | PASS — 730 test, 113 sınıf, 0 başarısız, 0 hata, 0 atlanan |
| `:app:assembleDiagnostic` | PASS — APK üretildi |
| Son Gradle sonucu | BUILD SUCCESSFUL — 1m 17s |
| APK ZIP bütünlüğü | PASS |
| Android apksigner | PASS — v2 imzası doğrulandı |
| Paket / sürüm / uygulama adı | PASS — tr.borsatakip.v5.diagnostic.v626fix / 6.2.6 / 626 / BIST / VİOP V6.2.6 |
| Asgari / hedef SDK | 26 / 35 |
| Fiziksel cihaz / emülatör | ÇALIŞTIRILMADI — ADB cihaz listesi boş |
| Canlı BIST/VİOP / 24-72 saat süreklilik | ÇALIŞTIRILMADI |
| Python sunucu testleri | Bu işte sunucu kodu değiştirilmedi; çalıştırılmadı |
| GitHub Actions | ÇALIŞTIRILMADI — yerel Gradle derlemesi kullanıldı |

Çalıştırılan komut:

```sh
./gradlew --offline --no-daemon --max-workers=2 :app:testDiagnosticUnitTest :app:assembleDiagnostic
```

İlk çalıştırmada 730 testten 12 kaynak metni kontrolü başarısızdı. Bunların 11'i, değiştirilmemiş V6.2.4 kaynak üzerinde de başarısız olduğu doğrulanan tarihsel kontrollerdi (68 kaynak kontrolünde 11 başarısız). Bir kontrol yeni doğrudan intraday yeteneği yerine eski sağlayıcı kimliği satırını arıyordu. Eski sürüm sabitleri ve değişmiş ekran/geçmiş gösterim kontrolleri mevcut davranışla karşılaştırılıp güncellendi; testler devre dışı bırakılmadı. Son çalıştırmada 730/730 geçti.

Yeni davranış testleri: quote/günlük birleşiminde TRY korunması; eksik veya yabancı para biriminin TL sayılmaması; fiyat ve veri zamanının birlikte taşınması; farklı sembollerin birleştirilmemesi; yönlendirici üzerinden periyot taramasında günlük bootstrap'ın çağrılmaması; eksik sembollerin neden kodu ve önceki tur tanılarının ayrılması; ham mesaj yerine kontrollü tanı kodunun dışa aktarılması.

APK: `BIST-VIOP-V6.2.6-DUZELTME-TANILAMA.apk`
Boyut: 8622088 bayt
SHA-256: `c7d3bc71995ea928a44d82e2b45f0a4ade778e8a03e19e7ba058998fe89cd618`
