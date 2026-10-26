# Kaynak son kontrol ve teslim raporu — 21.10.2026

**V6.3.35 / Build 665 · 983/983 JVM testi geçti · Lint 0 hata, 774 uyarı · APK/AAB yok.**

[Doğrulanan kaynak commit'i `3e84542353ad1ddd0641c579d5636ac6103c0e42`](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/commit/3e84542353ad1ddd0641c579d5636ac6103c0e42) · [PR #2](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/pull/2).

## 1. Başlangıç kaynağı

Güncel Build 665 çalışma dalındaki `4201b7160d96ed2d35c7f1842d8611b5cc8fe2fc` üzerinden devam edildi. 614 dosya başlangıç Git blob özetleriyle eşleşti. Başlangıç kaynak anlık görüntüsü ZIP SHA-256: `84ba19017248e374b1087fa37667ae75ef90927cd2358c3786681bcd7ec40da3`. Aktif Android projesi `android-v6323/` içindedir; eski sürüm klasörleri uygulanmadı.

## 2. Son kaynak kimliği

`GIT_COMMIT_SHA_FINAL=3e84542353ad1ddd0641c579d5636ac6103c0e42` test edilen ve uzak dosya içeriğiyle eşleştirilen kod commit'idir. Depo `ferittasbek-png/BIST-VIOP-V6.2.2`, dal `fix/deep-audit-no-apk`. Sonraki rapor commit'i uygulama kodunu değiştirmez; teslim commit'i dış `PROVENANCE-DELIVERY.json` dosyasında ayrıca kayıtlıdır.

## 3. GitHub dosya doğrulaması

25 değişen kaynak/test/CI/belge dosyasının tamamı uzak commit'ten geri okunarak eşleştirildi. Toplam 618 kaynak dosyasının Git blob ve SHA-256 özetleri çalışma kopyasıyla eşleşti. Ana dala yazılmadı; PR taslak olarak korundu.

## 4. Giderilen somut sorunlar

- 0–100 dışındaki RSI değerleri artık trendi güçlü/parlak duruma yükseltemiyor. Geçerli EMA yönü korunuyor; geçerli RSI sınırları ve mevcut renk/kontrast testleri geçiyor.
- VİOP değişim değeri NaN/sonsuz olduğunda metin ve renk birlikte nötr kalıyor. Geçersiz fiyat canlı etiketi alamıyor. Satır zaman etiketi, değerlendirme ve sayaçlarla aynı değerlendirme anını kullanıyor.
- VİOP kalite kapısı doğrulanmamış güncel seansı ve geçersiz son fiyatı reddediyor; sonsuz hacim likidite puanı sağlayamıyor. Bu kayıtlar doğrulanmış satır veya LONG/SHORT sayacı oluşturmuyor.
- Hisse, VİOP ve dayanak satırlarının metinleri 44 ortak kaynağa taşındı. Türkçe/İngilizce sayı biçimleri, yüzde işareti, satır sonu ve boş değerler için 108 kontrol geçti. Kaynak referanslarına geçen iki eski test, gerçek kaynak metnini çözerek aynı davranışı denetliyor.
- Beş yerleşimde 15 gereksiz baseline ölçümü kapatıldı. Üç bildirim/servis dosyasındaki 9 erişilemez eski Android sürüm koşulu kaldırıldı; minSdk 26 ve daha yeni sürümlerin gerekli kontrolleri korundu.
- README güncel proje konumunu ve APK üretmeden kontrol komutlarını açıklıyor; üretilen test çıktıları Git'e alınmıyor. CI en az 983 test ve yeni regresyon sınıflarını gerektiriyor.

Ayrıntılar: [CHANGE_REPORT.md](native-final-continuation-20261021/CHANGE_REPORT.md).

## 5. Gerçek test ve derleme sonuçları

| Kontrol | Sonuç |
| --- | --- |
| Tam native görev kümesi | PASS · 431,73 sn |
| JVM | **983 geçti / 0 başarısız / 0 hata / 0 atlanan**, 157 suite |
| Yeni regresyonlar | 17 test; VİOP kabul hataları düzeltme öncesinde 4 başarısız testle yeniden üretildi |
| Android test kaynakları | 53 class derlendi; cihazda yürütülmedi |
| Android Lint | **0 hata/fatal, 774 uyarı** |
| Normal tekrar kontrolü | PASS · 15,06 sn |
| Projenin Gradle Wrapper'ı ile tekrar | PASS · 19,32 sn; dağıtım SHA-256 doğrulandı |
| Gateway Node | 64/64 |
| Gerçek yerel Bun WebSocket | 1/1; 5 assertion |
| Metin biçimleri | 108/108; tr-TR ve en-US |
| Kaynak bütünlüğü | 618/618 dosya; doğrulama boyunca içerik aynı |
| Paketleme engeli | Beklenen `SOURCE_ONLY_APK_BLOCKED`; APK/AAB sayısı 0 |

İlk ortam denemesinde `javac` eksikti; tam JDK kuruldu. Ara derlemede bildirim temizliğinden kalan bir `immutableFlag` çağrısı bulundu ve düzeltildi. Düzeltme öncesi dört başarısız regresyon kaydı kanıt paketinde ayrı tutulur; son 983 testte başarısızlık yoktur. [BUILD_REPORT.md](native-final-continuation-20261021/BUILD_REPORT.md) yürütme ayrıntılarını içerir.

## 6. APK

Kullanıcının talimatıyla **APK/AAB üretilmedi**. APK adı, boyutu ve SHA-256 alanları uygulanamaz. Paketleme görev grafiği, işlem yapılmadan engellendi.

## 7. İmzalama

İmzalama yapılmadı; release veya üretime hazır APK iddiası yoktur. Paylaşılan erişim anahtarı kaynak veya raporlara eklenmedi.

## 8. GitHub Actions

[Kaynak commit'inin çalışması](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39588492586) `failure`: iş `119183255104`, runner 0, adım 0. GitHub üzerinde derleme/test adımları başlamadı; bunun hesap/altyapı nedeni bu erişimle kesinleştirilemedi. Başarılı native sonuçlar yerel gerçek JDK/Android SDK ortamına aittir.

## 9. Teslim dosyaları

Güncel kaynak ZIP'i, 157 JUnit XML raporunu ve tam Lint/derleme kayıtlarını içeren test kanıtı ZIP'i, bu rapor ve SHA256SUMS teslim edilir. Bunlar GitHub Actions artefaktı olarak adlandırılmaz. [PROVENANCE_REPORT.md](native-final-continuation-20261021/PROVENANCE_REPORT.md) kaynak kimliklerini açıklar.

## 10. Kalan işler ve sınırlar

819 → 774: net 45 daha az uyarı. Kaynak değişiklikleri 44 uyarıyı giderdi: 20 `SetTextI18n`, 15 `DisableBaselineAlignment`, 9 `ObsoleteSdkInt`. Önceki bir `GradleDependency` önerisi bu turda yeniden oluşmadı; bağımlılık yükseltilmedi ve bu fark kod düzeltmesi olarak sayılmadı. Suppression veya Lint baseline eklenmedi.

| Kalan kategori | Sayı |
| --- | ---: |
| `ApplySharedPref` | 7 |
| `NotifyDataSetChanged` | 5 |
| `ObsoleteSdkInt` | 3 |
| `NestedWeights` | 2 |
| `Overdraw` | 6 |
| `UnusedResources` | 116 |
| `UselessParent` | 2 |
| `TooManyViews` | 2 |
| `ButtonStyle` | 33 |
| `ViewConstructor` | 1 |
| `SetTextI18n` | 597 |
| **Toplam** | **774** |

Fiziksel cihazda görünüm/yazı ölçeği, Keystore/SQLite/yaşam döngüsü ve lisanslı canlı veri bağlantısı doğrulanmadı. Bu çalışma kaynak doğrulamasını tamamlar; mutlak hatasızlık veya üretime hazır olma kanıtı değildir. APK üretimi için ayrı kullanıcı talimatı beklenir.
