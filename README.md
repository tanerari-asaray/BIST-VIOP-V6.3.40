# BIST–VİOP v6.3.40 / BUILD 670 — audit revision

Current source: user-authorized BUILD670 audit corrections on `fix/build670-audit-20261026`.
Read CHANGE_REPORT.md, BUILD_REPORT.md and PROVENANCE_REPORT.md for scope and limits.
CI: `.github/workflows/build-670-touch-regression.yml`. Only a successful native job plus every Android 15 matrix job permits the tested diagnostic artifact. This is not a production release.

Local Android checks: `cd android-v6323` then `bash ./gradlew :app:testDiagnosticUnitTest :app:lintDiagnostic :app:assembleDiagnosticAndroidTest`.

Historical documentation below describes earlier versions and is not current test evidence.

---

# BIST–VİOP Build 668 güncellenmiş kaynak

**APK bu pakette bulunmaz.** Son kontrol sonuçları için önce `BUILD668-NIHAI-DOGRULAMA-RAPORU.md` dosyasını okuyun. Android kaynak kökü `android-v6323/` içindedir.
