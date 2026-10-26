# Build 542 — BGDK/1 Kaynak Kod Uygulaması

## Gerçekten uygulanan değişiklikler
- Yeni `scan/IndependentScanAudit.kt`: bağımsız, yalnız-okunur ve fail-closed analiz kapısı. Provider READY ve en fazla 5 dk önce ölçülmüş probe şartı; scan COMPLETE, REALTIME_ONLY, en fazla 5 dk eski sonuç ve sıfır integrity reject şartları; sembol evreni, mükerrerleri ayıklanmış kapsam, tanı mutabakatı ve UNKNOWN neden denetimi.
- `ui/SystemReportV2.kt`: insan-okunur rapora `[17] BAĞIMSIZ TARAMA DENETİM KAPISI` bölümü; JSON'a `independentScanAudit` nesnesi, kanıt ve blokaj kodları eklendi.
- Denetim sonucu **asla** işlem emri gönderme yetkisi veya kesin canlı-data doğrulaması olarak sunulmaz. Var olan sinyal yayın politikası değiştirilmemiştir.

## Açık kalanlar
- Gerçek lisanslı veri sağlayıcısı, backend URL ve kimlik bilgileri: verilmedi, uygulamaya gömülmedi.
- Borsa ve VİOP sağlayıcı adaptörü, Android WSS uçtan uca test, fiziksel cihaz bildirimleri ve stabilite testi: doğrulanmadı.
- Bağımsız denetim raporu yazılırken çalışır; zamanlayıcı/sinyal yayın motoruna entegrasyon için ayrı entegrasyon testi gerekir.
- **APK oluşturulamadı:** Gradle wrapper 8.10.2 dağıtımı sistemde yok. Gradle denemesi internet bağımlılığı nedeniyle dağıtımı indiremiyor; Android SDK da tespit edilmedi. Derlemenin başarılı olduğu iddia edilmez.

## Yerel kontrol
- ZIP bütünlüğü doğrulanır. Kaynak değişiklikleri ve rapor JSON anahtarları yerinde doğrulanmıştır.
- Tam Android/Kotlin derlemesi ve cihaz üzerinde çalışma testi yapılmamıştır.
