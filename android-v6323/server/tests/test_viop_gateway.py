from fastapi.testclient import TestClient
from server.viop_gateway import create_app
import time

SYMBOL = "F_PGSUS1026"  # test fixture, NOT a claim about a listed current contract

class FakeLicensedAdapter:
    async def contracts(self):
        return [{"symbol": SYMBOL, "underlying": "PGSUS", "expiry": "2026-10", "tickSize": 0.01,
                 "multiplier": 100.0, "lastTradableAt": int(time.time()*1000)+86400000,
                 "dataTimestamp": int(time.time()*1000), "realtime": True,
                 "delaySeconds": 0, "currentSessionIncluded": True}]
    async def quote(self, symbol):
        return {"symbol": symbol, "price": 100.0, "bid": 99.99, "ask": 100.01,
                "exchangeTimestamp": int(time.time()*1000), "realtime": True,
                "delaySeconds": 0, "currentSessionIncluded": True, "volume": 1.0, "openInterest": 1}
    async def history(self, symbol, range, interval):
        return {"symbol": symbol, "interval": interval, "exchangeTimestamp": int(time.time()*1000),
                "lastBarClosed": True, "candles": []}
    async def stream(self, symbols):
        if False:
            yield {}


def test_gateway_refuses_unconfigured_upstream():
    c = TestClient(create_app(private_key="test-secret"))
    r = c.get("/v1/health", headers={"Authorization":"Bearer test-secret"})
    assert r.status_code == 503 and r.json()["detail"]["code"] == "LICENSED_VIOP_PROVIDER_NOT_CONNECTED"


def test_no_access_key_never_grants_access():
    c = TestClient(create_app(adapter=FakeLicensedAdapter(), private_key=""))
    assert c.get("/v1/health").status_code == 503


def test_wrong_client_token():
    c = TestClient(create_app(adapter=FakeLicensedAdapter(), private_key="test-secret"))
    assert c.get("/v1/viop/contracts", headers={"Authorization":"Bearer wrong"}).status_code == 401


def test_quotes_and_contracts_under_test_injection_only():
    c = TestClient(create_app(adapter=FakeLicensedAdapter(), private_key="test-secret"))
    h = {"Authorization": "Bearer test-secret"}
    assert c.get("/v1/viop/contracts", headers=h).json()["items"][0]["symbol"] == SYMBOL
    q = c.get("/v1/viop/quote/" + SYMBOL, headers=h)
    assert q.status_code == 200 and q.json()["price"] == 100.0
    assert c.get("/v1/viop/history/" + SYMBOL, headers=h).status_code == 200
    assert c.get("/v1/viop/history/" + SYMBOL + "?interval=7m", headers=h).status_code == 400


def test_stale_quote_rejected():
    fake = FakeLicensedAdapter()
    async def old_quote(symbol):
        data = await FakeLicensedAdapter().quote(symbol)
        data["exchangeTimestamp"] -= 90000
        return data
    fake.quote = old_quote
    c = TestClient(create_app(adapter=fake, private_key="test-secret"))
    q = c.get("/v1/viop/quote/"+SYMBOL, headers={"Authorization":"Bearer test-secret"})
    assert q.status_code == 503 and q.json()["detail"]["code"] == "STALE_EXCHANGE_QUOTE"


def test_unconfigured_factory_remains_closed(monkeypatch):
    from server.viop_gateway import create_configured_app
    monkeypatch.delenv("BORSA_VIOP_ADAPTER_FACTORY", raising=False)
    monkeypatch.setenv("BORSA_GATEWAY_APP_KEY", "local-test-secret")
    c = TestClient(create_configured_app())
    assert c.get("/v1/health", headers={"Authorization": "Bearer local-test-secret"}).status_code == 503


def test_factory_must_be_explicit_and_valid(monkeypatch):
    from server.viop_gateway import create_configured_app
    import pytest
    monkeypatch.setenv("BORSA_VIOP_ADAPTER_FACTORY", "untrusted-or-missing")
    with pytest.raises(RuntimeError, match="INVALID_VIOP_ADAPTER_FACTORY_FORMAT"):
        create_configured_app()
