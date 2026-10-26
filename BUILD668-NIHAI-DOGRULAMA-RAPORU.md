# BIST–VİOP V6.3.38 / BUILD 668 — Güncel kaynak ve gerçek doğrulama raporu

**Tarih:** 22 Ekim 2026  
**Kaynak:** Sohbette teslim edilen son tipografi/ikon paketinden türetilmiştir; eski düzeltmeler korunmuştur.  
**Hedef ekran:** Hisseler → Hisse Detayı; ek olarak Tam Ekran Grafik.  
**Durum:** Kaynak güncellendi; **APK üretilemedi; cihazda birebir görsel test yapılmadı.**

## Uygulanan değişiklikler

- ✅ Hisse detayının en altında görsellerdeki altı açıklama kartı: Fiyat nasıl hareket ediyor?, Hangi seviyeler önemli?, Bu yorum ne zaman değişir?, Kısaca ne iş yapıyor?, Neden ilgi görüyor?, Dikkat edilmesi gerekenler.
- ✅ Altı kart gerçek XML görünümüne, Kotlin bağlarına ve veri eksikliğinde temizleme davranışına eklendi. Tam ekran grafik içinde de aynı altı konu korunuyor.
- ✅ Doğrulanmamış sektör, şirket haberi, ilgi gerekçesi, fiyat veya hedef uydurulmuyor; yeterli kaynak yoksa kullanıcıya söyleniyor.
- ✅ **30 dakika:** 5 dakikalık gerçek mumların yalnızca tam ve kesintisiz altılı grupları birleştiriliyor. Eksik aralık doldurulmuyor.
- ✅ **Aylık:** Gerçek günlük OHLCV verileri İstanbul saat diliminde takvim aylarına ayrılıyor; oluşmakta olan son dönem kesinleşmiş kapanış kabul edilmiyor.
- ✅ Seçili ve dört bağımsız zaman dilimi trendi, henüz kapanmamış olabilecek son mum kullanılmadan hesaplanıyor.
- ✅ Hata ekranında sağlayıcıdan gelen ham hata gövdesi / istisna metni kullanıcıya aktarılmıyor.
- ✅ Grafik göstergeleri, TradingView bağlantısı, dört sağ ok, sekmeler, alt menü ve tipografi/ikon düzeltmeleri korundu.
- ✅ Paket içindeki eksik Gradle Wrapper JAR, önceki Build 668 kaynak arşivinin dosyasından geri alındı.

## Gerçekten çalıştırılan testler

| Denetim | Sonuç | Kapsam |
|---|---|---|
| Ekran kaynak sözleşmesi | ✅ 30/30 | XML/Kotlin kaynak ve etkileşim bağları; cihaz değil |
| Tipografi/ikon statik kontrolleri | ✅ 23/23 | XML/vektör/font/referanslar; cihaz değil |
| Kaynak ve güvenli veri sözleşmesi | ✅ 19/19 | Altı kart, veri temizleme, 30dk/aylık menü, hata güvenliği |
| Önceki finansal hesaplama regresyonu | ✅ 198/198 | Önceden derlenmiş çevrimdışı JVM test paketi |
| Yeni zaman dilimi testleri | ✅ 5/5 | Bu oturumda Kotlin kaynak kodundan derlenmiş smoke test |
| Değişen Kotlin dosyaları | ✅ 6 dosya, 0 sözdizimi hatası | Kotlin PSI parser; Android bağlama/derleme değil |
| Android XML dosyaları | ✅ Ayrıştırılabildi | XML parser; Android AAPT2 değil |
| Android Gradle / JUnit / Lint | ⚠️ Yürütülemedi | Android SDK yok, Gradle indirilemiyor |
| Android emülatör ekran görüntüleri | ⚠️ Üretilemedi | Emülatör ve SDK yok |
| APK imza, DEX, kurulum, SHA-256 | ⚠️ APK olmadığı için doğrulanamadı | Eski APK yeniden adlandırılmadı |

## APK üretiminin engeli — gerçek komut sonucu

`cd android-v6323 && ./gradlew --offline --no-daemon :app:compileDiagnosticKotlin :app:testDiagnosticUnitTest :app:lintDiagnostic :app:assembleDiagnostic`

- Gradle Wrapper artık bulunuyor, ancak `gradle-8.10.2-bin.zip` dağıtımı indirilmeye çalışıldığında `java.net.UnknownHostException: services.gradle.org` alındı.
- Ortamda Android SDK (`sdkmanager`, `adb`, `emulator`, `aapt2`) mevcut değil.
- Gerekli Android bağımlılıkları ve gerçek derleme çıktıları sağlanamadı. **Uygulamanın Android üzerinde çalıştığı iddia edilemez.**

## Görsel karşılaştırma sınırı

Referans görseller mevcuttur; ancak gerçek Android cihaz/emülatör ekran görüntüsü elde edilemediğinden piksel fark haritası, cihaz genişliği taşma testi veya kesin birebir görsel onayı yoktur. Ölçü ve hizalama kaynak seviyesinde kontrol edilmiştir.

## İmza ve kimlik uyarısı

`versionCode = 668`, `versionName = 6.3.38`. Tanılama yapılandırmasında ayrı uygulama kimliği eki `.diagnostic.v666.work` kullanılır. Önceki yüklü APK ile aynı imza anahtarı doğrulanmadan üzerine kurulum garantisi verilmez.

## GitHub durumu

GitHub'da mevcut `tanerari-asaray/BIST-V-OP-V6.3.38` ana dalındaki kaynak ZIP sohbet içindeki son tasarım ZIP'inden geridedir. Bu paketin tüm güncel kaynak dosyaları henüz GitHub'a yüklenmemiştir. Önceki taslak PR #1 yalnız 10 dakikalık ek düzeltme betiğini içerir; bu teslimin tamamını içermez.

## Sonuç

**Bu teslim yalnız tam güncellenmiş kaynak ZIP + çalıştırılmış kaynak/JVM test raporudur.** Kullanıcı talimatındaki zorunlu APK ve gerçek ekran görüntüsü kabul ölçütleri henüz karşılanmamıştır.
