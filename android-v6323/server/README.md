# V6.2.1 — lisanslı VİOP ara servis iskeleti

Bu dizin **çalışan bir canlı VİOP veri aboneliği içermez.** Matriks/dxFeed/iDeal özel API şeması veya kimlik bilgisi tahmin edilmemiştir. `LicensedViopAdapter` protokolünü, veri sağlayıcısının yetkili **resmî şema/SDK'sıyla** ayrıca doldurmak gerekir. `app.state.adapter=None` iken servis, fiyat/kontrat üretmek yerine 503 döner.

Uygulamaya `/v1/health`, `/v1/viop/contracts`, `/v1/viop/quote/{symbol}`, `/v1/viop/history/{symbol}` ve isteğe bağlı WSS `/v1/viop/live` protokolü sunar. Bu yollar sağlayıcının URL'si **değildir**. Testlerdeki yapay fiyatlar yalnız `tests/` içinde kalır.

`BORSA_GATEWAY_APP_KEY` güçlü gizli anahtar olarak işletim ortamında tutulmalı; gerçek veri sağlayıcı token'ı ayrı sunucu sırrı olmalıdır. Gateway internete açıkken HTTPS/WSS ters proxy, erişim kısıtları, güvenli yönetilen sırlar ve sağlayıcının kişisel kullanım/lisans izinleri zorunludur. Mevcut Android istemcisinin Ayarlar'ına **gateway HTTPS adresi ve gateway özel anahtarı** girilir; sağlayıcı anahtarı telefona yazılmaz.

Çalıştırma ancak lisanslı adaptör sağlandıktan ve `create_app(adapter=adapter, private_key=...)` üzerinden uygulamaya bağlandıktan sonra mümkündür. `uvicorn viop_gateway:app` tek başına asla veri sağlamaz.

> Android'deki eski BIST `/v1/preflight` yolu bu iskeletin parçası değildir. Yeni `checkViopQuote()` doğrudan gerçek VİOP API yollarını test eder; BIST readiness kendi eski servisinden bağımsız kalır. Yeni WSS endpoint'i için Android tarafında üretim yaşam döngüsü aboneliği ayrıca entegre edilmelidir; mevcut sürüm REST ile fiyat yeniler.
