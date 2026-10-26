# BIST / VİOP V6.2.8 • Build 628 • Çakışan tarama koruması

## Kaynak kanıtı (15.10.2026, V6.2.7 cihaz raporu)
Raporun kalıcı olay günlüğünde iki gerçek BIST taraması üst üste binmiş: biri 17:57:59–17:58:42, diğeri 17:58:08–17:58:49. Bu kanıt eşzamanlı çalışma olduğunu gösterir; veri bütünlüğünün kesin bozulduğunu tek başına kanıtlamaz. Otomatik tarama sayacında görünen iki eski başarısız çalışmanın sebebi de bu rapordan kesinleştirilemez.

## Düzeltmeler
- `BistScanGate`: manuel, otomatik ve fırsat taramalarının ortak BIST motoru için tek süreçlik AtomicReference lease kilidi. Aynı anda ikinci BIST taraması başlatılmaz.
- `BistScanner`: lease öncesi yeni tarama isteğini ağ çağrısı veya geçmiş yazımı yapmadan `SKIPPED / SCAN_ALREADY_RUNNING` olarak işaretler. İptal/exception dahil `finally` ile kilit serbest bırakılır.
- `ScanPhaseTracker`: reddedilen isteği tamamlanmış/başlamış tarama gibi yazmaz, doğrudan `SCAN_ALREADY_RUNNING` olayı kaydeder.
- `AutomaticScanRunner`: `SKIPPED_BUSY` ayrı sayaçta tutulur, COMPLETE/PARTIAL veya başarısız tur sayaçlarını yapay biçimde artırmaz.
- `BistScanActivity`, `OpportunityActivity`: çakışma ikinci ekranın son güvenilir görüntüsünü veya sinyal belleğini silmez.
- `SystemReportV2`: önceden kapanmış olaylardan kesin örtüşen run aralıkları, ret sayısı ve rapor anındaki kilit durumu. Metin ve JSON aynı olay/kilit görüntüsünü kullanır.

## Sınırlamalar
Koruma aynı Android uygulama **süreci** içinde geçerlidir. Çoklu işlem/cihaz veya uzak sunucu çapraz tarama için dağıtık kilit gerekir. İlk tarama henüz scanner'ın veri tarama bölümüne gelmeden yapılan ayrı preflight çağrıları örtüşebilir; güvenlik kapısı BistScanner'ın işlem zincirini seri hâle getirir. 45 Yahoo veri eksikliği ve üretim sağlayıcısı eksikliği otomatik çözülmüş değildir. Gecikmeli veri canlı sinyale dönüşmez. Fiziksel Xiaomi cihazı / 24–72 saat stabilite / gerçek bildirim teslimi ayrıca test gerektirir.

## Test ve yükleme
`./gradlew :app:assembleDiagnostic :app:testDiagnosticUnitTest` ve `apksigner verify --print-certs` ile doğrulayın. Sürüm 6.2.8; uygulama kimliği `tr.borsatakip.v5.diagnostic.v628audit` eski V6.2.7'nin üzerine güncelleme değildir; telefonunuzda ayrı kurulacaktır.
