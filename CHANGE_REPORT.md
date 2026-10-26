# BUILD 670 — audit corrections

## Production changes
- F01: Preserve independent scan cadence in scheduler, runner, service and BIST UI. Add an editable 1..1440-minute cadence in scan settings; correct labels and describe completion-to-next-run delay.
- F02: Measure drawdown from the initial equity of 100, including the first losing trade.
- F03: Honor full/partial T1 exits before T2 in the same bar, also in explicit collision policies.
- F04: Existing stop-market positions gapping through their stop use the observed bar open as a fill reference, not an untraded stop level. Costs remain explicit; real liquidity is not guaranteed.
- F05: Walk-forward train/validation/test slices require an exit known before the split boundary, excluding open or invalid-timed outcomes.
- F06/F07: Reject malformed, stale/future or mismatched live candles; ignore finalized-bar revisions and older bars. Separate per-connection sequence maps; callbacks delivered on the main thread only if generation and connection settings still match. Bound text messages and avoid publishing raw server errors.
- F08: Add provider-reviewed point-in-time news-risk contract. Unknown/missing/unverified coverage and CRITICAL risk block trade publication. Empty headlines never mean CLEAR. New proxy endpoint delegates to NEWS upstream; provider integration is still required.
- F09: Detail decision text uses the shared verified-signal gate and expires to WATCH when stale.
- F10: General technical analyzer fails closed on malformed OHLCV, duplicate or reversed series instead of silently repairing it.
- F11: Separate unapproved test input/report artifacts from the tested diagnostic package. Packaging depends on native quality and all four Android 15 jobs, copies the same tested APK, verifies the signature and writes runtime hashes.
- F12: Preserve old evidence under audit/history, regenerate current source hashes, and replace stale APK-built claims with source-modified status.
- F13: Fullscreen and embedded S/D tests use actual Espresso click and check active/inactive tint plus chart state. Both S/D touch targets are at least 48dp high. Existing overlay pixel test retained.
- F14: Introduce a versioned daily-only profile: EMA20/50 slope, close-based 10/20 breakout, pullback/continuation models, SMA200 context, RSI 50..68 / 32..50, CLV 0.70 / 0.30, ATR up to 6%, obstacle distance and daily returns. Keep intraday policy explicitly separate. Export profile/model/RS20/SMA/returns in the assessment.

## Regression coverage
New JVM tests cover first losses, full/partial LONG/SHORT target exits, gap stops, walk-forward leakage, live candle validation, analyzer validation, temporal news coverage/veto and the daily SMA/close-breakout profile.
Gateway regressions cover explicit provider-reviewed news risk, malformed/future evidence and missing upstream.
Android matrix runs four stock interaction tests and three S/D/overlay tests at 320dp/1.0, 320dp/1.3, 411dp/1.0 and 411dp/1.5.
The previous source test requiring timeframe/cadence coupling was updated to assert the corrected independence contract.

## Limits and required external evidence
No licensed provider, KAP content classifier, real trading fills, physical device behavior or in-place signer compatibility is certified by fixture tests.
NEWS upstream must implement /v1/bist/news-risk/{symbol}?asOf=... with verified symbol/state/coverageThrough/availableAt/source. Absent evidence intentionally yields WATCH, potentially reducing published candidates to zero.
Daily-profile math is not proof of trading profitability. A dedicated daily 5+5 report UI, next-session execution confirmation, full corporate-action datasets and portfolio backtest remain separate product work.
Legacy version-specific workflows/scripts are retained for history and are not the BUILD670 acceptance pipeline.
