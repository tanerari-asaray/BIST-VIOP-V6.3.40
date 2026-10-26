# Build 666 — yetkilendirilmiş APK üretimi

21 Ekim 2026 tarihinde kullanıcının “APK yi üret kontrol et hataları varsa düzelt” talimatı üzerine Build 665'in test edilmiş kaynağından devam edildi.

- Sürüm kimliği 6.3.36 / Build 666 yapıldı. Tanılama paket kimliği tr.borsatakip.v5.diagnostic.v666.work; yeni imza önceki kurulumu veya yerel verilerini değiştirmez.
- Gradle 8.10.2 Wrapper dağıtımı resmi SHA-256 değeriyle sabitlendi.
- Android Build Tools 35.0.0 açıkça seçildi. AGP'nin varsayılan 34.0.0 aracını eksik bulup farklı paket indirmeye çalışması giderildi; yerel ve CI derlemesi aynı araç sürümünü kullanır.
- Yalnız build/apk666-verified dalında yetkilendirilmiş APK akışı eklendi. Mevcut kaynak kontrolü dalının APK engeli korunur.
- En az 983 JVM testi, sıfır hata/başarısızlık/atlama, sıfır Lint hatası, Android test kaynaklarının derlenmesi, 64 gateway testi ve gerçek yerel WebSocket testi APK üretiminden önce zorunludur.
- APK imzası, paket/sürüm kimliği, DEX, ZIP CRC, hizalama ve gömülü kaynak commit'i doğrulanır. Aynı kaynağın ZIP'i, raporlar ve SHA-256 kayıtları oluşturulur.
- Paylaşılan gateway adresi derleme ayarıdır. API anahtarı APK içine eklenmez.
- Önceki RSI, VİOP kalite kapısı, renk/metin, tarama iptali ve HTTP hata tanılama düzeltmeleri korunur.
- Fiziksel cihaz ve lisanslı canlı sağlayıcı doğrulanmadan üretime hazır sonucu verilmez. İmza debug/tanılama imzasıdır.

