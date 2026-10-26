# BIST / VİOP V6.3.21 Build 651 — Değerleme listesi değişiklikleri

## Değiştirilen kod
- `app/src/main/java/tr/borsatakip/v5/ui/FullUniverseScanActivity.kt`: fiyat/temel mod seçimi, altı sıralama seçeneği, sonuç kartlarında temel finansal özet, ortak finansal değerlendirme önbelleği, teknik gösterge açıklamaları, detay gerekçesi ve renk düzeltmesi.
- `app/src/main/java/tr/borsatakip/v5/ui/ValuationResultSort.kt`: eksik sayıları sona atan güvenli sıralama; net nakit ve bankaları Net Borç/FAVÖK sıralamasından dışlama.

## Gerçekleştirilen doğrulamalar
- Python kaynak metni statik kontrolü: 12/12 geçti (derleme veya çalışma kanıtı **değildir**).
- Gradle `:app:compileDiagnosticKotlin --offline` denendi; Gradle dağıtımı yerel makinede olmadığından wrapper ağ indirmesi başarısız; Android/Kotlin derlemesi **yapılamadı**.
- APK **üretilmedi**; cihaz, emülatör ve kullanıcı arayüzü testleri **yapılmadı**.

## Eksik/engelli işlevler
- Kaynak finansal modelinde karşılaştırılabilir önceki kâr dönemleri olmadığı için kâr büyümesi puanı hesaplanmaz/sıralamada son sırada kalır.
- Aynı sektörde kullanılan tek tek emsal şirket adları ve farklı finansal dönem eşleştirmesi henüz veride taşınmıyor.
- Takip listesine ekleme düğmesinin mevcut takip deposuna yazması yerine bilgilendirme uyarısı vardır. İşlev tamamlanmış sayılmaz.
- Arka planda otomatik cache yenileme, zaman aşımı/yeniden deneme düğmesi ve gerçek finansallarla sektör kabul testi tamamlanmadı.
- Genel `Assessment` verisi tek bir zaman damgasıyla 15 dakika önbelleklenir; uygulama yeniden başlatıldığında finansal cache diskten geri yüklenmez.
- Ayrıntı ekranında grafik periyodu değiştiğinde kaynak satırı geçici grafik durumu ile değişebiliyor; tek-satır kaynak biçimi ve gerçek ekran yerleşimi ayrıca test edilmeli.
