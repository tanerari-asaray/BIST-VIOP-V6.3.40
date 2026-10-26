# BIST–VİOP v6.3.38 Build 668 — APK üretim ve ekran kontrol sonucu

**Tarih:** 21 Ekim 2026  
**GitHub commit:** [c589725b5f814469c4fe77c9c0baa4c15fb09815](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/commit/c589725b5f814469c4fe77c9c0baa4c15fb09815)  
**Dal:** `build/apk668-chart-readable-analysis`  
**Son GitHub Actions koşusu:** [39626367955](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39626367955)

## Kesin sonuç

**Yeni APK üretilemedi; gerçek Android uygulamasından ekran görüntüsü alınamadı.**
Bu dosyada bir APK veya ekran görüntüsü varmış gibi gösterilmez. Gradle wrapper `services.gradle.org` adresini çözemedi (DNS); Gradle ve Android SDK mevcut değil. GitHub Actions denemesi **runner atanmadan, hiçbir adım çalışmadan başarısız** oldu. Bu hesap/proje CI engelinin kesin nedeni GitHub yönetim arayüzünden araştırılmalıdır; yalnızca sıfır adım bilgisi belirli bir faturalama/izin nedenini ispatlamaz.

## Kontroller

| Kontrol | Durum |
|---|---|
| Kotlin kaynak+test sözdizimi | 433 dosya / 0 ayrıştırma hatası (tip çözümlemesi değildir) |
| Kotlin analitik hesap kontrolü | 198/198 geçmiş sıkı kontrol — yeni değişiklikler yalnız Android screenshot testleri ve CI dosyasında |
| JVM unit test uyumluluğu | 13/13 geçmiş kontrol — gerçek Gradle/JUnit koşumu yapılamadı |
| Yeni Android ekran testleri | 2 adet test / 3 ekran görüntüsü tanımlandı, çalıştırılamadı |
| GitHub Actions | 39626367955 başarısız: 0 adım, runner atanmamış |
| Android APK | ÜRETİLEMEDİ: Gradle dağıtımı ve Android SDK bulunmuyor, internet DNS erişimi kapalı |
| Gerçek ekran görüntüleri | ALINAMADI: Android emulator/ADB mevcut değil, Actions runner başlamadı |
| Canlı veri | Test edilmedi: lisanslı BIST/VİOP fiyat sağlayıcısı yapılandırılmamış |

## Kaynağa eklenen testler

`android-v6323/app/src/androidTest/java/tr/borsatakip/v5/ui/Build668VisualScreenshotTest.kt`
Android cihaz veya emülatörde TEST fikstürüyle açılış ekranı, aşağıya kaydırılan kolay açıklama bölümü ve hatalı mum verisinde “Veri olmadan yorum yapmıyoruz” ekranını yakalar. Çalıştırıldığında şu görüntüleri dışa aktarmak için tasarlanmıştır:

- `01-grafik-ust.png`
- `02-kolay-aciklama-alt.png`
- `03-veri-yok.png`

Bu **dosyalar henüz üretilmiş değildir**. GitHub Actions akışına emülatör ve ekran görüntüsü artifact adımları eklendi, fakat çalışan runner gerekli. Uygulamanın ekran görüntüleri, önceki yapay tasarım referanslarıyla karıştırılmamalıdır.

## Kaynakta önceden düzeltilmiş veri güvenliği

- 5/10 dakikalık ve 1 saatlik veride ardışıklık kontrolü;
- Kaybolan/boş mumdan sahte analiz yapılmaması;
- Tarihi gelecekte olan mumların reddi;
- Asenkron yenilemede eski trend yanıtının kartlara taşınmaması;
- Tarihi geçmiş mum için “Eski veri · Yön teyitsiz” uyarısı;
- TradingView'in en üstte olması, 5dk/10dk/1s/günlük trend ve en altta sade açıklama.

## Üretim için bekleyen somut adımlar

1. GitHub Actions işinin neden *runner atanmadan* başarısız olduğunu depo **Actions** ekranındaki yönetim/hesap uyarısından belirleyip giderin.
2. Android SDK 35, build-tools 35.0.0, JDK 17 ve Gradle 8.10.2 bulunan bir build ortamında şu komutu çalıştırın:
   ```bash
   cd android-v6323
   ./gradlew --no-daemon :app:testDiagnosticUnitTest :app:lintDiagnostic :app:compileDiagnosticAndroidTestKotlin :app:assembleDiagnostic
   ```
3. Emulator testlerini çalıştırıp `build668-device-screens` içindeki gerçek PNG görüntülerini alın ve referans görselle kontrol edin. Testteki ekran görüntüleri TEST verisidir, gerçek fiyat değildir.
4. APK'nın `applicationId`, `versionCode=668`, SHA-256, imzası ve telefon kurulumunu doğrulayın. Sonuç başarıyla gözlemlenmeden “APK hazır” demeyin.

**Not:** Railway üzerindeki üretim BIST API servisine Android derlemesi amacıyla müdahale edilmedi.
