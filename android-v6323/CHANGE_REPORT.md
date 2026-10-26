# CHANGE_REPORT — V6.2.6 / build 626

- Yahoo fiyat ve günlük geçmiş birleşiminde fiyatın para birimi artık kaybolmaz. Para birimi yoksa veya TRY/TL değilse TL kabul edilmez. Gerçek sağlayıcı metadatası kullanılır.
- Yönlendirici üzerinden yapılan gecikmeli intraday tarama doğrudan seçilen periyodun OHLCV verisini ister. 5 DK analizi için önce 220 günlük mum indirme zorunluluğu kaldırıldı. Seçilen periyodun kendi asgari mum koşulu korunur.
- Yahoo günlük taraması ve periyot taramasında boş sonuçlar, HTTP 404/403/429/5xx, DNS, ağ ve zaman aşımı sorunları kontrollü neden kodlarıyla kaydedilir. Bilinmeyen upstream nedenler tahmin edilmez.
- Sağlayıcının neden kodları tarama tanısına ve sistem raporuna taşınır. Önceki turun tanıları doğrudan intraday taramaya karışmaz. Ham hata mesajları yerine kontrollü kodlar dışa aktarılır.
- Gecikmeli gözlemlerin canlı işlem kapısından engellenmesi bilgi düzeyinde ve açık ifadeyle gösterilir. İşlem kapısı, güncellik kontrolleri ve gecikmeli verinin doğrulanmış sinyal geçmişine yazılmaması korunur.
- Otomatik tarama anahtarı Android 13+ bildirim izni eksikse izin ekranını açar. Zamanlayıcı talebi reddedilirse ayarlarda açık/başarılı gösterilmez. Bildirim ekranı uygulama tercihiyle Android iznini ayrı değerlendirir ve doğrudan bildirim ayarına gider.
- Uygulama adı Gradle sürümünden üretilir. Eski sabit V6.2.1 etiketi, eski proje adı ve Actions artefakt adı düzeltildi. 7 yeni regresyon testi eklendi.
- Tarihsel kaynak metni kontrolleri güncel sürüm ve mevcut davranışla uyumlu hale getirildi. Sürüm alt sınırı, gerçek kontrat/quote, geçmiş yetersizliği ve destek/direnç engeli kontrolleri korunup açıklaştırıldı. Bu çalışma kapsamında yatırım formülleri, VİOP sinyal üretim kuralları veya canlı veri erişim yetkileri değiştirilmedi.

Bu değişiklikler 632 sembolün tamamı için Yahoo verisi bulunduğunu veya eski 590 kaydın tümünün artık geçeceğini kanıtlamaz. Yeni cihaz taramasında fiyat, para birimi, dönem yeterliliği ve kapsam tekrar ölçülmelidir.
