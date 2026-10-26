# Build 668 — Hisse Detayı yazı tipi, ikon ve boşluk iyileştirme raporu

**Sürüm:** V6.3.38, Build 668  
**Ekran:** Hisseler → Açılan Hisse Detayı  
**Referans:** Kullanıcının iki tasarım görseli (`image(20261022-095225).png`, `image(20261022-095229).png`)  
**İşleme alınan temel kaynak:** `BIST-VIOP-V6.3.38-BUILD668-HISSE-DETAYI-EKSIKLER-TAMAMLANDI.zip`

## Kaynakta uygulananlar

- ✅ **Yazı sistemi:** Başlık, fiyat, sekmeler, dört trend kartı, dört teknik kart, uyarılar, metrikler ve açıklama alanları düzenlendi. `sans-serif`/`sans-serif-medium` kullanıldı. Gereksiz font üst-alt iç boşluğu önemli metin alanlarında kapatıldı. `stock_detail_design_tokens.xml` ile ortak font/boyut/boşluk değerleri tanımlandı.
- ✅ **Sekme aktifliği:** Grafik / Teknik / Plan seçildiğinde kalınlık ve turkuaz alt çizgi birlikte güncelleniyor. Önceden yalnız renk/zemin değiştiği için Grafik yazısı aktif olmadığı durumda da kalın kalabiliyordu.
- ✅ **Üst ikonlar:** Geri oku platform yazı karakteri yerine beyaz çizgisel Android VectorDrawable; favori yıldızı dolu/boş iki vektör olarak işlendi. Favori ekle/çıkar durumunda ikonun değişmesi korunuyor.
- ✅ **Grafik ikonları:** Tam ekran işareti platforma bağlı `⛶` karakterinden 23dp vektöre dönüştürüldü. Teknik kartların dört sağ oku korundu. Uyarı panelleri sarı çizgisel vektör simge kullanıyor; teknik uyarıda yinelenen Unicode işareti kaldırıldı.
- ✅ **Alt menü:** Diğer uygulama bölümleri etkilenmesin diye yalnız Hisse Detayı için `include_stock_detail_bottom_nav.xml` oluşturuldu. Ana Sayfa, Hisseler, VİOP, Favoriler ve Ayarlar için beş tutarlı vektör simge eklendi. Hisseler turkuaz, diğerleri açık beyaz gösteriliyor. Tıklama kimlikleri korunuyor.
- ✅ **Aralıklar:** Sayfa dış kenar boşluğu 10dp; trend kartları ortak en az 62dp; teknik kartlar en az 64dp; teknik satır iç boşlukları dengeli; sekmeler 40dp; zaman dilimi ve tam ekran dokunma alanları en az 44dp. İndikatör düğmeleri daha sıkı hizalandı. Ayrıntılı yorum yazısı tek satıra alınıp 8–11sp otomatik ölçekle taşmaya karşı korundu.
- ✅ **Referans yerleşimi:** Trend bölümü üstündeki görselde bulunmayan ek başlık gizlendi; bölüm daha kompakt hale geldi. Teknik analiz kartları, TradingView, veri kontrolü ve gerçek OHLCV akışı korunuyor.

## Kontrol sonuçları

- ✅ **30/30** mevcut kaynak ekran/etkileşim testi (`python ci/verify_hisse_detail_pixel_contract.py`). Adındaki pixel kelimesine rağmen bu yalnız statik kaynak sözleşmesi testidir.
- ✅ **23/23** ek tipografi, simge, kaynak referansı ve korunması gereken kimlik testleri (`BUILD668-TIPOGRAFI-KONTROL-SONUCLARI.json`).
- ✅ **198/198** çevrimdışı Kotlin hesaplama regresyonu (`b668-new-source-analysis-tests.jar` ile kontrol).
- ✅ XML dosyaları parse edilebiliyor; vektörler ve yerel drawable/dimen/layout referansları doğrulandı.
- ⚠️ **Android Gradle/APK derlemesi yapılamadı.** Kaynak paketinde `gradle/wrapper/gradle-wrapper.jar` eksik: `org.gradle.wrapper.GradleWrapperMain` sınıfı bulunamıyor. Android SDK / emulator da mevcut değil. Bu aşamada gerçek cihaz veya emülatör ekran görüntüsü alınamadı.
- ⚠️ **Piksel düzeyinde birebir doğrulama yapılmadı.** Statik boyutlar tasarım görseline yaklaştırıldı; font rasterizasyonu, ekran yoğunluğu, sistem font ölçeği, cihaz navigasyon çubukları ve bileşen taşması için cihaz görüntüsüyle karşılaştırma gerekir. 360dp, 393dp ve 412dp genişlikte; normal ve %130 font ölçeğinde ayrıca doğrulama önerilir.
- ⚠️ Gerçek piyasa verisiyle uçtan uca test ve GitHub'a aktarım bu teslimatın parçası değil.

## Korunan davranışlar

TradingView düğmesi, gerçek veri yoksa sahte fiyat üretilmemesi, seçili 5/10/60 dakikalık ve günlük grafiğe gitme, 6 gösterge, Ayrıntılı Yorumu Aç düğmesi, favori durumu ve alt menü bağlantıları korunmuştur.
