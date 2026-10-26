# BUILD_REPORT — 21.10.2026

Kaynak kod commit'i: `e78aa12b20a212475213d46f3c027a08e2935ee4`.

## Ortam ve komutlar

- JDK 17.0.20; Gradle Wrapper 8.10.2; AGP 8.7.3.
- Kotlin 2.0.21; KSP 2.0.21-1.0.28; Room 2.6.1.
- Android compile/target SDK 35, min SDK 26; platform/build-tools yerel SDK'da hazırlandı.
- Node 24.19.0; Bun 1.4.3.
- Native komutlar aynı workspace üzerinde **sıralı** yürütüldü.
- Kotlin ve Java derlemesinin uyarıları hata sayan mevcut kuralları korundu.

`android-v6323` içinde:

```bash
./gradlew --no-daemon --stacktrace --continue -I ../ci-source-only.init.gradle \
  :app:clean :app:testDiagnosticUnitTest :app:compileDiagnosticAndroidTestKotlin \
  :app:processDiagnosticResources :app:lintDiagnostic

./gradlew --no-daemon -I ../ci-source-only.init.gradle \
  :app:kspDiagnosticKotlin --rerun-tasks

./gradlew --no-daemon --stacktrace --continue -I ../ci-source-only.init.gradle \
  :app:testDiagnosticUnitTest :app:compileDiagnosticAndroidTestKotlin \
  :app:processDiagnosticResources :app:lintDiagnostic
```

Üç çalıştırma da başarılıdır. Temiz çalıştırma 351,5 sn, zorunlu KSP tekrarı 39,1 sn, varsayılan tekrar 18,0 sn sürdü. İki üretilen `*_Impl.kt` dosyasının içerik özetleri tekrar turunda aynı kaldı.

APK engeli ayrıca `assembleDiagnostic --dry-run` ile sınandı. `SOURCE_ONLY_APK_BLOCKED` beklenen reddi, exit 1 ve 0 APK/AAB doğrulandı. Paketleme yürütülmedi.

Depo kökünde:

```bash
node --test server/tests/*.test.mjs
bun test server/tests/websocket.bun.test.ts
node audit/run-deep-audit.mjs
python3 server/build_railway_bundle.py --check
python3 ci-verify-source-only.py
python3 ci/deep-audit-jvm-report.py
git diff --check
```

## Sonuçlar ve ham kanıt

- JVM: 155 XML suite, 963 test; başarısız/hata/atlanan 0.
- Android test kaynakları: 53 derlenmiş class. Cihaz/emülatör yürütmesi yok.
- Lint: hata/fatal 0, uyarı 1322. Tam XML/HTML/TXT teslim kanıt paketindedir.
- Node: 64/64; Bun: gerçek yerel WebSocket el sıkışması 1/1, 5 assertion.
- Sağlık/HTTP matrisi: 108+400 kontrollü senaryo, 0 hata. Bunlar uzak canlı sağlayıcı testi değildir.
- Release URL: query, port 0, traversal ve LOCALHOST reddedildi. Geçerli HTTPS/base path kontrolü URL aşamasını geçip eksik attestation ayarında beklenen şekilde durdu. Bu beş exit 1 beklenen negatif/kontrol sonucudur.
- Bundle: üç sunucu modülünde tekrar üretim eşitliği geçti.
- 513 kaynak/yapılandırma girdisi başta ve sonda aynı SHA-256'ya sahipti.
- `ci-verify-source-only.py`, yeni regresyon sınıfları +963 test eşiği +0 başarısız/atlanmış +0 Lint hatası +derlenmiş cihaz sınıfı +APK yokluğunu doğruladı.

## Kalan kalite işleri

| Lint kategorisi | Uyarı |
| --- | ---: |
| SetTextI18n | 617 |
| HardcodedText | 350 |
| UnusedResources | 116 |
| SmallSp | 87 |
| Diğer 23 kategori | 152 |
| **Toplam** | **1322** |

Uyarılar silinmedi veya baseline/suppression eklenerek gizlenmedi. 0 Lint hatası sonucu 0 uyarı anlamına gelmez.

Gerçek cihazda Keystore/prefs, SQLite açılışı, yaşam döngüsü ve görünüm kontrolleri APK izni sonrası ayrı yürütülmelidir. Canlı lisanslı BIST/VİOP bağlantısı ve imzalama bu çalışmada test edilmedi.

GitHub Actions [39556552967](https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2/actions/runs/39556552967) çalıştırıcı atanmadan sona erdi: runner 0, adım 0. Bu rapordaki başarılı native sonuçlar yerel ortamın gerçek çıktılarıdır.
