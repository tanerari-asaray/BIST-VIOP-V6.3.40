# BIST/VİOP V6.2.2 — Aşama 3 Kaynak Düzeltme Kaydı

14.10.2026 — APK üretilmedi.

## Uygulanan
1. `scan/BistScanner.kt`: `classifyMissingSymbols` sağlayıcının mesaj haritasını da alabilir. Sembolleri normalize eder, öncelik sırasını (hata > veri yok > nedeni bilinmeyen) korur. Mesaj bulunmasını `DETAIL_AVAILABLE` olarak raporlar; kişisel bilgi veya kaynağı doğrulanmamış HTTP sebebi uydurmaz.
2. `scan/IndependentScanAudit.kt`: Gecikmeli verilerin canlı işleme kabul edilmemesini `DELAYED_RESULTS_NOT_TRADE_ELIGIBLE` koduyla ayırır. Anlık veri reddi `INTEGRITY_REJECTED` kalır. İki durumda da işlem güvenlik kapısı kapalı kalır.
3. `ui/SystemReportV2.kt`: 550/550 gecikmeli red gibi durumları veri sağlayıcı arızası olarak göstermemek için açıklama değiştirilmiştir. Eksik sembol tanı kodlarının sayıları ve gecikmeli işlem engeli metin/JSON çıktısına eklenmiştir.
4. Var olan COMPLETE/PARTIAL ayrımı, anlık koruma, VİOP canlı veri yapılandırılmadığında kapalı davranış ve neon kart değişiklikleri korunmuştur.

## Testler
- `python -m pytest server -q`: 7/7 başarılı.
- Kotlin/Android derlemesi burada çalıştırılamadı (Android SDK/Gradle yok). Kotlin kaynakları üzerinde statik denetim yapıldı. Yeni Android APK yoktur.

## Açık ve dış bağımlı işler
- 41 bilinmeyen sembol için upstream servis kayıtları olmadığından kök neden hâlâ bilinmiyor. Yalnızca sınıflandırma iyileştirildi.
- Gerçek Matriks/iDeal/dxFeed lisanslı API test erişimi yok; canlı bağlantı kurulmuş değildir.
- VİOP WebSocket'in detay ekranına bağlanması, sunucuda kalıcı pozisyon izleme, Android 16 bildirim ve boot testleri, Android testlerinde önceki 10 başarısızlık kapatılmadı.
- Kullanıcının APK üretimine ilişkin son onayı bekleniyor.
