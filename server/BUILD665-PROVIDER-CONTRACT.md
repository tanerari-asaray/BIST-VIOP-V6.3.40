Build 665 provider integration

Set BORSA_BIST_UPSTREAM_URL to an authorized HTTPS provider implementing /v1/health, /v1/preflight, /v1/bist/symbols, /v1/bist/snapshot-batch, /v1/bist/quote/{symbol}, /v1/bist/history/{symbol}, /v1/bist/history-window/{symbol}, and /v1/scanner/opportunities. BORSA_BIST_UPSTREAM_TOKEN is optional server-side authorization. A URL alone does not pass readiness; the response is validated and the Android client retains its strict checks.

Set BORSA_NEWS_UPSTREAM_URL and optional BORSA_NEWS_UPSTREAM_TOKEN for /v1/news. News items retain their source publication and availability timestamps. The gateway does not create news or certify a source's statements.

For streams set BORSA_BIST_WSS_URL, BORSA_SCANNER_WSS_URL and/or BORSA_VIOP_WSS_URL to a dedicated read-only WSS market endpoint; optional matching *_WSS_TOKEN variables are upstream-only credentials. Browser Origin values must be present in BORSA_ALLOWED_ORIGINS (comma-separated); native Android clients authenticate with the app bearer token. Subscribe/unsubscribe/ping actions only. Upstream messages and attestation metadata pass unchanged; clients must validate symbol/time/session/sequence and signed scanner calculations. No market data or trading signals are synthesized by the bridge.

BIST candle volume must be units of shares; daily turnover is the sum/average of actual price*share volume over the prior 20 trading-day bars, excluding T. History metadata includes symbol, interval, exchangeTimestamp, lastBarClosed, candles. For an early daily close, sessionClosedAt must be the provider-authoritative session close in epoch milliseconds; without it the strict path remains conservative. A display-only chart can show a forming bar and never approves a trade on that basis.

Every quote pairs price with its own exchangeTimestamp, previousClose refers to the previous trading session, and missing daily change is omitted/null. Realtime/currentSessionIncluded/delaySeconds are explicit, independently checked again by Android. All timestamps use epoch milliseconds.

These are source adapters. Actual licensed-provider credentials, availability, real-device behavior and any production deployment remain separate verification tasks.
