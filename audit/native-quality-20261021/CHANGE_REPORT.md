# CHANGE_REPORT — kaynak kalitesi, 21.10.2026

Kod: `335323216b3468ee40a6b7bd820f8859a56ae7b1`; başlangıç: `e6b075dd17e81b461ec8ee6a39ef4d01002a1080`. 46 değişen dosyanın listesi [JSON raporundadır](../SOURCE-QUALITY-CONTINUATION-20261021.json).

| Değişiklik | Neden / etkisi | Kanıt |
| --- | --- | --- |
| 348 XML metni / 278 kaynak | Ortak metinler yeniden kullanılabilir ve çevrilebilir; kaçışlar korunur. | 348 başvurunun eşitlik denetimi; dört bilinçli ellipsis normalizasyonu |
| 151 XML yazı boyutu | 12sp altındaki yazılar büyütüldü. | SmallSp 87→0; kaynak derlemesi; cihaz görünümü bekliyor |
| Esnek bağlantı kartı | Büyük yazıda uç nokta açıklamalarının sabit yükseklik/satır sınırına takılmasını önler. | XML/kaynak derlemesi; B122 görünür metin sözleşmesi |
| 12 alan ve iki arama ipucu | Arama/sayısal girdiler otomatik doldurma açısından açık; erişilebilirlik ipucunu örten etiketler kaldırıldı. | Autofill 12→0, TextFields 1→0, ContentDescription 2→0 |
| SwitchCompat / drawable yükleme | Mevcut AppCompat temasıyla tutarlı bileşen/vektör yüklemesi. | İlgili 9 Lint uyarısı giderildi; uygulama ve Android test kaynakları derlendi |
| Grafik nesnelerini yeniden kullanma | Aynı Path/Paint/tarih nesneleri, veri değişiminde hesaplanan gözlem değerleri; kare başına gereksiz yeniden oluşturma azalır. | DrawAllocation 17→0; trend ve grafik regresyon paketi geçti |
| Gözlem grafiği kurucuları | Context/AttributeSet standart kurucuları ve eski compact çağrısı birlikte korunur. | ViewConstructor 2→1 |
| Yedekleme/aktarım kuralları | Mevcut yedeklemeyi kapatma niyeti eski/yeni XML formatında açıkça uygulanır. | Üç yeni JVM regresyonu; manifesto ve her iki aktarım modu |
| Uyarlanabilir uygulama simgesi | Eksik simge giderildi; tek renk katmanı eklendi. | Kaynak derlemesi; MissingApplicationIcon/MonochromeLauncherIcon 0 |
| Kaynak sözleşmesi | B122 aynı üç VİOP uç noktasını, XML'de başvurulan metinleri çözerek arar. | Önceki tek başarısızlık giderildi; 966/966 |
| CI eşiği | Yeni üç test ve yedekleme regresyon sınıfı zorunlu. | ci-verify-source-only.py geçti |

Kalan 819 uyarı [ana raporda](../SOURCE-QUALITY-CONTINUATION-20261021.md) açıkça listelenir. Gerçek cihaz görünümü, canlı bağlantı ve performans/FPS ölçümü yapılmadı.

Resmî başvurular: [Android yedekleme/aktarım kuralları](https://developer.android.com/identity/data/autobackup) ve [uyarlanabilir simgeler](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
