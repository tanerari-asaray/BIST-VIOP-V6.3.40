# Build 660 VIOP adapter

The inspected Railway function has only BORSA_GATEWAY_APP_KEY configured. No licensed VIOP upstream is connected. This change does not create a data subscription or certify live data.

Set BORSA_VIOP_UPSTREAM_URL to an authorized HTTPS service implementing this application's v1 JSON contract. Optionally set BORSA_VIOP_UPSTREAM_TOKEN as a Railway secret; it is sent only to that upstream. Do not place these credentials in Android source or an APK. Redirects are refused. The URL is administrator-controlled; restrict it to a trusted provider hostname and apply deployment egress restrictions as appropriate.

Routes: /v1/viop/contracts (items with unique F_/O_ symbols, underlying, expiry YYYY-MM, contractType, tickSize, multiplier, source, dataTimestamp in epoch ms, lastTradingAt in epoch ms); /v1/viop/quote/{symbol} (symbol, price, exchangeTimestamp, source, realtime, delaySeconds); /v1/viop/history/{symbol}?range=1y&interval=1d (symbol, interval, exchangeTimestamp, lastBarClosed, candles with timestamp/open/high/low/close/volume); /v1/health (upstream must explicitly return ok=true and viopReady=true).

The health response is an upstream declaration, not independent verification. Quote observation permits delayed values but does not promote them to trading signals. Existing strict quote consumers retain their realtime checks. History retains existing Android integrity checks; rejected history is reported and no chart is fabricated. BIST gateway routes still require their own licensed implementation. UI fixtures and gateway test data are test-only.
