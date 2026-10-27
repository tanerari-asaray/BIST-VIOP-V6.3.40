# BUILD 670 focus and recreation correction — 27 October 2026

- Await two consecutive ready-window observations after Activity launch/recreation and navigation; keep Espresso touch/visibility assertions intact. A timeout remains a test failure and captures its window state.
- Keep the CI emulator awake and dismiss its test-device keyguard before instrumentation.
- Collect logcat, windows, activities, power state, screenshot and app-private evidence in an EXIT trap, even when instrumentation fails. Use one build670-screens path.
- Report failed instrumentation explicitly in result.json without confusing normal runner termination with test success.
- Preserve the displayed fullscreen candle series across Activity recreation, alongside the existing overlay/viewport state. Restored data remains marked recorded, never certified live.
- Extend touch regression assertions for S/D visibility/enabled state, embedded recreation, fullscreen entry/return and retained candles.

Local validation: 67 gateway/news/proxy tests passed; shell syntax checked. Android compilation, lint and device results are supplied by the current Actions run; no pass is assumed here.
