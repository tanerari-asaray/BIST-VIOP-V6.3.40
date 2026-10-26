Build 665 / 6.3.35 — source changes; validation is recorded in the corresponding CI evidence

Source baseline: user-supplied Build 663 ZIP, SHA-256 bcb36946d3bf70c8ff6cea7e6966ed20a5d04e83d719c4c141c56bea571e45a7.
Preserved later changes: GitHub parent 143cb2ee8f0768437b759a5fdf1f8be2797e9d7c (Build 664 price ordering).
Branch: fix/build665-integrity-review. No changes to main.

Changes implemented in production source:

- B1: display quote price/time/daily reference separate from the immutable closed-bar decision; persisted across restart.
- B2: lifecycle-bound stock-detail freshness expiry and explicit quote-refresh failure; historical decisions do not become current just because a display quote changes.
- B3: single-contract VIOP analysis through ViopScanner, with the same risk/MTF/current-entry checks; chart state no longer permanently downgrades the signal.
- B4: forming bars allowed for chart display; strict decision histories exclude the forming bar and retain identity/OHLC/closure checks. Optional sessionClosedAt supports a provider-declared session close.
- B5: configurable HTTPS BIST/preflight/history/quote/batch, news and attested scanner proxy; WSS subscription bridge. Missing services remain explicit errors. No subscription, provider connection or deployment is fabricated.
- B6: Yahoo meta quote paired with regularMarketTime, fallback uses a complete candle price/time pair; one-day quote request and daily reference separation.
- B7: sector filtering uses validated KAP XUBNK/XUSIN/XUTEK membership when available; unverified membership is never replaced by a guessed list.
- B8: separate endeks/üyelik and stock quote refresh; bounded parallel requests, progress, duplicate suppression, explicit partial failures, decision snapshot preserved.
- Runtime layout regression: keep refresh actions in one header row and make the empty-state content scrollable, so filter-reset/scan actions remain reachable on small viewports. Espresso retains normal visibility and touch constraints.
- B9: detail orientation enters full-screen chart; timeframe/view state can be saved/restored, including indicators and viewport.
- B10: per-source error categories retained in reference details. Adapter updates use DiffUtil and retain expanded company names.
- Local BIST publication: separate 100-point daily evidence assessment, >=75 points, >=3 independent categories, prior 20-day average turnover >=TRY30m excluding T, RR>=1.5, 1.5 ATR stop opt-in, volume/gap/RSI/market gates. Missing evidence is blocked, not estimated. Core V5 engine scores remain separately identifiable.

CI must run unit tests, instrumented tests, Node gateway tests, and an actual local WebSocket handshake test before the delivery APK step. Evidence is generated from that run, not copied from Build 663/664.

External limits: real licensed BIST/VIOP/news/scanner upstreams, attestation keys and a physical phone are not supplied by this change. Credentials remain server-side/user settings. This is a diagnostic debug build; it is not a release-signed production build. Source adapter tests and local WebSocket tests do not prove licensed upstream availability.

Bugün ne öğrendin? A quote refresh, chart refresh and trade-signal reevaluation are distinct operations; each needs its own evidence and expiry.
