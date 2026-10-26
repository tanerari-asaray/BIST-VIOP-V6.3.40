# BIST/VİOP licensed feed integration

The uploaded V6.2.2 mobile APK can accept a gateway URL and gateway client key in Settings. There is NO licensed upstream connection, so it must remain NOT LIVE.

1. Request documented API trial and pricing from a Borsa İstanbul licensed data distributor. Verify automated/non-display usage, cloud processing, mobile display and redistribution permissions separately.
2. Implement LicensedViopAdapter with genuine contracts, quote, closed OHLCV and optional streaming, matching the `server/viop_gateway.py` protocol in the source archive.
3. Use HTTPS/WSS reverse proxy, server-side upstream secrets and revocable per-client gateway access key. Never commit real tokens.
4. Configure adapter factory with `BORSA_VIOP_ADAPTER_FACTORY=provider_adapter:create_adapter` and gateway secret `BORSA_GATEWAY_APP_KEY` for the included bootstrap.
5. Verify health and quote freshness before enabling signals. Stale, missing or unlicensed data must fail closed; delayed BIST underlying trends must not be advertised as live VIOP signals.
6. Android: configure gateway URL and separate gateway app token in Settings, then perform real-device connectivity, notification and background tests.

Open issues: V6.2.2 unit tests 710/720 pass; device health reports BACKEND_URL_MISSING. The service must not be advertised as production-ready without licenses and tests.
