# Kaynak kalitesi devam raporu — 21.10.2026

**V6.3.35 / Build 665 · 966/966 JVM testi geçti · Lint 0 hata, 819 uyarı · APK yok.**

[Doğrulanan kod `335323216b3468ee40a6b7bd820f8859a56ae7b1`](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/commit/335323216b3468ee40a6b7bd820f8859a56ae7b1) · [PR #2](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/pull/2).
Bu rapor, aynı gün teslim edilen 1.322 uyarılı kaynak doğrulamasından sonraki çalışmayı kaydeder.

## 1. Başlangıç kaynağı

Aktif dal `fix/deep-audit-no-apk`, başlangıç commit'i `e6b075dd17e81b461ec8ee6a39ef4d01002a1080`.
Başlangıç kaynak arşivinin SHA-256 değeri:
`69e0a2c7909b0463653ae2df50caaacdda2e877b12f165d216d99a873001e78a`.

Güncel Build 665 kaynağı üzerinden devam edildi; önceki kaynak ve kanıt paketleri korundu.

## 2. Doğrulanan son kod

`GIT_COMMIT_SHA_FINAL=335323216b3468ee40a6b7bd820f8859a56ae7b1` test edilen kod commit'idir. Sonraki rapor commit'i kodu değiştirmez; teslim commit'i dış provenance dosyasında ayrıca kayıtlıdır.

## 3. Uzak dosya doğrulaması

**46 kaynak/test/CI dosyası** güncellendi. Tüm değişen dosyalar GitHub commit'inden geri okunarak yerel içerikle karşılaştırıldı; 46/46 eşleşti. Tam liste [JSON kaydındadır](SOURCE-QUALITY-CONTINUATION-20261021.json).

## 4. Kaynakta yapılanlar

- 348 sabit XML metin başvurusu, 278 ortak metin kaynağına taşındı. Kaçış/boşluk yapısı denetlendi; dört taşınan metindeki üç nokta bilinçli olarak “…” karakterine çevrildi.
- XML'de 12sp altındaki 151 yazı boyutu 12sp yapıldı. Bağlantı kartı içeriğe göre büyüyor; uç nokta metninin satır sınırı kaldırıldı.
- 12 arama/sayısal alanda otomatik doldurma davranışı açıkça tanımlandı. İki arama alanında ipucunu örten erişilebilirlik etiketi kaldırıldı.
- SwitchCompat ve AppCompat drawable yüklemesi kullanıldı; mevcut LTR davranışı açıkça belirtildi, 13 tek taraflı padding tanımı tamamlandı.
- Dört grafik görünümünde Path/Paint/tarih nesneleri yeniden kullanılıyor. Gözlem grafiğinin veriden türeyen değerleri veri güncellemesinde hesaplanıyor. Trend rengi/gücü kuralları korundu.
- Eski ve yeni Android yedekleme biçimlerinde uygulama verilerini dışlayan kurallar eklendi. Bulut ve cihaz aktarımı için üç yeni JVM regresyonu geçti.
- Uyarlanabilir/tek renk uygulama simgesi eklendi. Bir eski kaynak testi, görünür uç nokta metnini gerçek metin kaynağından çözecek şekilde düzeltildi.
- CI doğrulaması, en az 966 test ve yeni yedekleme regresyon sınıfını gerektiriyor.

Ayrıntı: [CHANGE_REPORT.md](native-quality-20261021/CHANGE_REPORT.md).

## 5. Gerçek doğrulama sonuçları

| Kontrol | Sonuç |
| --- | --- |
| Tam native görev kümesi | PASS · 236,4 sn |
| JVM | **966 geçti / 0 başarısız / 0 hata / 0 atlanan**, 156 suite |
| Android test kaynakları | **53 class derlendi**; cihazda yürütülmedi |
| Lint | **0 hata/fatal, 819 uyarı** |
| Zorunlu KSP tekrarı | PASS · 36 sn; iki Room dosyasının özetleri aynı |
| Normal tekrar derleme | PASS · 20 sn |
| Node testleri | **64/64** |
| Gerçek yerel Bun WebSocket testi | **1/1**, 5 assertion |
| Kontrollü sağlık / HTTP matrisi | **108 + 400**, 0 hata |
| Sunucu bundle eşitliği | PASS · üç modül |
| Kaynak bütünlüğü | **611 dosya**; derleme kopyası ve kaynak aynı, doğrulama boyunca değişmedi |
| APK engeli | PASS · beklenen paketleme reddi; **0 APK/AAB** |

İlk turdaki tek başarısızlık eski testin metni doğrudan XML içinde aramasıydı. Test aynı üç uç noktanın görünür metinde bulunduğunu kaynak çözümleyerek denetliyor. Son turda başarısız/atlanan test yok.

Native çıktılar, çalışma alanı eşitlemesinden ayrı bir geçici dizinde üretildi; kopyanın kaynak özetleri asıl kaynakla karşılaştırıldı. Bu bir mock veya yalnız sözcüksel kontrolden ibaret değildir. [BUILD_REPORT.md](native-quality-20261021/BUILD_REPORT.md) komutları ve sınırları içerir.

## 6. APK

Kullanıcı talimatıyla **APK/AAB üretilmedi**. APK adı/boyutu/SHA-256 yoktur. `assembleDiagnostic --dry-run` paketleme yürütülmeden `SOURCE_ONLY_APK_BLOCKED` ile reddedildi; bu kontroldeki exit 1 beklenen sonuçtur.

## 7. İmzalama

İmzalama yapılmadı. Kimlik bilgileri kaynağa eklenmedi.

## 8. GitHub Actions

[Kaynak commit'inin Actions çalışması](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39564382598) `failure`: iş `119103032136`, runner 0, adım 0. GitHub üzerinde native görevler çalışmadı. Başarılı test sonuçları yerel JDK/SDK ortamına aittir.

## 9. Teslim

Kaynak dal ve PR #2 üzerindedir. Güncel kaynak ZIP'i, gerçek XML/Lint/derleme kayıtlarının bulunduğu kanıt ZIP'i ve SHA256SUMS ayrı teslim edilir. Bunlar GitHub Actions artefaktı olarak sunulmaz.
[PROVENANCE_REPORT.md](native-quality-20261021/PROVENANCE_REPORT.md) kaynak kimliklerini kaydeder.

## 10. Kalanlar ve dürüst durum

**1.322 → 819: net 503 daha az uyarı.** Önceki uyarılardan **504'ü** giderildi. Son Lint turunda RecyclerView için **bir sürüm güncelleme önerisi** ayrıca göründü. Mevcut 1.3.2 bağımlılığıyla doğrulama yapıldı; bu turda bağımlılık güncellenmedi.

| Kalan kategori | Sayı |
| --- | ---: |
| Dinamik metinlerin kaynaklaştırılması — SetTextI18n | 617 |
| Kullanılmayan kaynak adayları — UnusedResources | 116 |
| ButtonStyle | 33 |
| DisableBaselineAlignment | 15 |
| ObsoleteSdkInt | 12 |
| Eşzamanlı kalıcı kayıt — ApplySharedPref | 7 |
| Overdraw | 6 |
| NotifyDataSetChanged | 5 |
| NestedWeights / UselessParent / TooManyViews | 2 + 2 + 2 |
| Programatik finansal panel kurucusu — ViewConstructor | 1 |
| Bağımlılık sürüm önerisi — GradleDependency | 1 |
| **Toplam** | **819** |

Hiçbir uyarı suppression veya Lint baseline eklenerek gizlenmedi. Kalıcı kaydın tamamlanma garantisi için kullanılan commit çağrıları korundu. Yaşam döngüsü scope'u/sembol gerektiren programatik finansal panele sahte varsayılan kurucu eklenmedi. Kullanılmayan kaynak adayları dinamik kullanım kontrolü yapılmadan silinmedi.

Gerçek cihazda yazı ölçeği/dar ekran görünümü, Keystore/SQLite/yaşam döngüsü ve lisanslı canlı sağlayıcı bağlantısı doğrulanmadı. Üretim dağıtımı ve ana dala birleştirme yapılmadı. **Kaynak kontrolleri geçti; mutlak hatasızlık veya üretime hazır olma sonucu çıkarılamaz.**
