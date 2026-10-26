# BUILD_REPORT — 21.10.2026 kalite devamı

Doğrulanan kod: `335323216b3468ee40a6b7bd820f8859a56ae7b1`.

JDK 17.0.20, Gradle Wrapper 8.10.2, AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Room 2.6.1; compile/target SDK 35, min SDK 26. Node 24.19.0, Bun 1.4.3.

611 dosyanın aynı özetlere sahip izole kopyası kullanıldı. Tüm Gradle turları sıralı yürütüldü. Çalışma alanı eşitlemesi derleme çıktılarını değiştirirken oluşan ortam hatasını önlemek için çıktılar geçici dizinde tutuldu ve Gradle bitince geri alındı.

`android-v6323` dizininde:

```bash
./gradlew --no-daemon --stacktrace --continue -I ../ci-source-only.init.gradle \
  :app:testDiagnosticUnitTest :app:compileDiagnosticAndroidTestKotlin \
  :app:processDiagnosticResources :app:lintDiagnostic

./gradlew --no-daemon -I ../ci-source-only.init.gradle \
  :app:kspDiagnosticKotlin --rerun-tasks

./gradlew --no-daemon --stacktrace --continue -I ../ci-source-only.init.gradle \
  :app:testDiagnosticUnitTest :app:compileDiagnosticAndroidTestKotlin \
  :app:processDiagnosticResources :app:lintDiagnostic
```

Tam son tur 236,4 sn, zorunlu KSP tekrarı 36 sn, normal tekrar 20 sn: hepsi PASS. KSP'nin iki Room Kotlin çıktısının SHA-256 özetleri aynı kaldı.

- 156 XML suite: **966 test; 0 başarısız/hata/atlanan**.
- Yeni BackupExclusionPolicyTest: **3/3**.
- Android test kaynakları: 53 class; cihazda çalıştırılmadı.
- Lint XML: 0 Error/Fatal, 819 Warning. 504 önceki uyarı çözüldü; bir yeni GradleDependency önerisiyle net azalma 503.
- Kotlin/Java uyarıları hata sayan mevcut derleme kuralları korundu.
- `assembleDiagnostic --dry-run` yürütmeden SOURCE_ONLY_APK_BLOCKED reddi verdi. Exit 1 beklenen başarı koşulu; APK/AAB 0.
- `ci-verify-source-only.py`: en az 966 test, yeni regresyon sınıfı, atlanan/başarısız 0, Lint hata 0, cihaz sınıfları ve APK yokluğu doğrulandı.
- `node --test server/tests/*.test.mjs`: 64/64.
- `bun test server/tests/websocket.bun.test.ts`: 1/1, gerçek yerel WebSocket el sıkışması, 5 assertion.
- `node audit/run-deep-audit.mjs`: 108 sağlık +400 HTTP senaryosu; 0 hata. Kontrollü girdiler; uzak canlı sağlayıcı testi değildir.
- `python3 server/build_railway_bundle.py --check`: üç modül eşit.
- `node --check ci/railway-verification-report.mjs` ve `git diff --check`: PASS.

İlk temiz turda uygulama/kaynaklar/Android testleri derlendi; 966 testin biri eski XML metin varsayımı nedeniyle başarısızdı. Sözleşme düzeltilip son kaynak üzerinde yukarıdaki görevlerin tamamı başarılı çalıştırıldı. Bu ilk tur son kaynak için başarılı temiz derleme olarak gösterilmez.

[Actions 39564382598](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39564382598): failure, runner 0, adım 0. Başarılı yerel sonuçlar GitHub Actions başarısı olarak sunulmaz. Gerçek cihaz, Keystore/SQLite yaşam döngüsü, görünüm ve lisanslı canlı bağlantı doğrulanmadı.
