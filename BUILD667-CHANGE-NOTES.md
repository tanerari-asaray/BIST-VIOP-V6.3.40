# Build 667 — fiyat yenileme hata tanılaması

- Başlangıç, doğrulanmış Build 666 kaynağı ve 967e1cd136103d7dfb411ddbdf9191be8a95e62b rapor commitidir.
- Kullanıcının fiziksel testi fiyat yenilemenin başarısız olduğunu gösterdi. Yüklenen APK, önceki Build 666 APK ile SHA-256 düzeyinde eşleşti.
- Eksik sunucu adresi, geçersiz HTTPS adresi ve eksik uygulama API anahtarı ayrı hata kodlarıyla bildirilir. Yapılandırma eksik ve deneysel yedek kapalıysa hisse istekleri başlatılmaz.
- HTTP 503/404 artık veri şeması hatası sayılmaz. Uygulama erişim hatası ile sağlayıcı erişim hatası ayrılır; güvenli HTTP durum/aşama/kod kanıtı saklanır. Ham yanıt, URL sorgusu veya anahtar hata penceresine kopyalanmaz.
- Aynı hata her hisse için uzun uzun tekrarlanmaz. Neden, etkilenen hisse sayısı ve üç örnek sembol tek grupta gösterilir; en çok dört grup ve kalan grup sayısı verilir. Yeni ekran metinleri ortak Android string kaynaklarındadır.
- Başarısız yenileme önceki fiyatı güncel yapmaz ve analiz kararını yeniden hesaplamaz. Deneysel Yahoo yedeği yalnız kullanıcının mevcut açık tercihleriyle kullanılabilir.
- 12 JVM regresyon testi ve 2 Android cihaz testi eklendi. Android testlerinin derlenmesi cihazda yürütme anlamına gelmez. Üç HTTP testi düzeltme öncesi gerçek çalıştırmada başarısızdı.
- Sürüm 6.3.37 / Build 667. Paket tr.borsatakip.v5.diagnostic.v666.work korunur; aynı yerel debug sertifikası doğrulanarak Build 666 üzerine güncellenir.
- Railway üzerinde canlı kod BIST ve VİOP fiyat yollarını sağlayıcı bağlı değil hatasıyla sonlandırıyor. Bu geliştirme sunucuda sağlayıcı kurmaz; üretim dağıtımı yapılmaz.
