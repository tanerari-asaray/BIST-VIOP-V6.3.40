"""Provider-neutral VİOP gateway contract. No fake/live data source is bundled.

Deploy behind HTTPS/WSS and supply an explicitly licensed adapter. This server
fails closed if there is no adapter or no private application access key.
"""
from __future__ import annotations

import hmac
import importlib
import os
import re
import time
from typing import Any, AsyncIterator, Protocol

from fastapi import FastAPI, Header, HTTPException, WebSocket, WebSocketDisconnect

SYMBOL = re.compile(r"^[A-Z0-9_.:-]{2,64}$")


class LicensedViopAdapter(Protocol):
    """The licensed provider's private API is implemented OUTSIDE this template.

    Every method must return genuine, permissioned market data with the real
    exchange timestamp in epoch milliseconds; a receipt timestamp is not enough.
    """
    async def contracts(self) -> list[dict[str, Any]]: ...
    async def quote(self, symbol: str) -> dict[str, Any]: ...
    async def history(self, symbol: str, range: str, interval: str) -> dict[str, Any]: ...
    def stream(self, symbols: list[str]) -> AsyncIterator[dict[str, Any]]: ...


def failure(code: int, reason: str) -> HTTPException:
    return HTTPException(status_code=code, detail={"code": reason})


def require_symbol(symbol: str) -> str:
    name = symbol.strip().upper()
    if not SYMBOL.fullmatch(name):
        raise failure(400, "INVALID_CONTRACT_SYMBOL")
    return name


def validate_quote(symbol: str, data: dict[str, Any]) -> dict[str, Any]:
    """Enforce server-side identity/timestamp/quote format without fabricating fields."""
    if data.get("symbol", "").strip().upper() != symbol:
        raise failure(502, "QUOTE_SYMBOL_MISMATCH")
    now = int(time.time() * 1000)
    timestamp = data.get("exchangeTimestamp")
    if not isinstance(timestamp, int) or isinstance(timestamp, bool) or timestamp < 1_000_000_000_000:
        raise failure(502, "EXCHANGE_TIMESTAMP_MUST_BE_MS")
    if timestamp > now + 15_000:
        raise failure(502, "EXCHANGE_TIMESTAMP_IN_FUTURE")
    if now - timestamp > 60_000:
        raise failure(503, "STALE_EXCHANGE_QUOTE")
    price = data.get("price")
    if not isinstance(price, (int, float)) or isinstance(price, bool) or not 0 < price < float("inf"):
        raise failure(502, "INVALID_QUOTE_PRICE")
    if data.get("realtime") is not True or data.get("currentSessionIncluded") is not True:
        raise failure(503, "REALTIME_NOT_AUTHORIZED_OR_SESSION_MISSING")
    if data.get("delaySeconds") != 0:
        raise failure(503, "DELAYED_OR_UNDECLARED_QUOTE")
    bid, ask = data.get("bid"), data.get("ask")
    if bid is not None and ask is not None and (not 0 < bid <= ask):
        raise failure(502, "CROSSED_OR_INVALID_BID_ASK")
    return data


def create_app(adapter: LicensedViopAdapter | None = None, private_key: str | None = None) -> FastAPI:
    app = FastAPI(title="BIST/VİOP V6.2.1 Private Gateway", docs_url=None, redoc_url=None)
    app.state.adapter = adapter  # must be a real, licensed adapter in deployment
    app.state.private_key = private_key if private_key is not None else os.getenv("BORSA_GATEWAY_APP_KEY", "")

    def authenticate(authorization: str | None) -> LicensedViopAdapter:
        key = app.state.private_key
        if not key:
            raise failure(503, "PRIVATE_GATEWAY_KEY_NOT_CONFIGURED")
        expected = "Bearer " + key
        if not authorization or not hmac.compare_digest(authorization, expected):
            raise failure(401, "INVALID_GATEWAY_APP_TOKEN")
        upstream = app.state.adapter
        if upstream is None:
            raise failure(503, "LICENSED_VIOP_PROVIDER_NOT_CONNECTED")
        return upstream

    @app.get("/v1/health")
    async def health(authorization: str | None = Header(default=None)) -> dict[str, Any]:
        upstream = authenticate(authorization)
        # This confirms only configured service/adapter, never quote subscription.
        return {"ok": upstream is not None, "provider": "Private VİOP gateway", "quoteReady": False,
                "message": "Sözleşme ve fiyat yetkisi /v1/viop/... üzerinden ayrı doğrulanır."}

    @app.get("/v1/viop/contracts")
    async def contracts(authorization: str | None = Header(default=None)) -> dict[str, Any]:
        upstream = authenticate(authorization)
        items = await upstream.contracts()
        if not isinstance(items, list):
            raise failure(502, "PROVIDER_CONTRACT_SCHEMA_INVALID")
        # The actual expiry, provider symbol, tick, multiplier and timestamp
        # are provided by the adapter, never synthesized here.
        for c in items:
            if not isinstance(c, dict) or not c.get("symbol") or not c.get("underlying"):
                raise failure(502, "CONTRACT_METADATA_INCOMPLETE")
        return {"items": items}

    @app.get("/v1/viop/quote/{symbol}")
    async def quote(symbol: str, authorization: str | None = Header(default=None)) -> dict[str, Any]:
        upstream = authenticate(authorization)
        safe_symbol = require_symbol(symbol)
        data = await upstream.quote(safe_symbol)
        if not isinstance(data, dict):
            raise failure(502, "QUOTE_SCHEMA_INVALID")
        return validate_quote(safe_symbol, data)

    @app.get("/v1/viop/history/{symbol}")
    async def history(symbol: str, range: str = "1y", interval: str = "1d",
                      authorization: str | None = Header(default=None)) -> dict[str, Any]:
        upstream = authenticate(authorization)
        safe_symbol = require_symbol(symbol)
        if not re.fullmatch(r"\d+[dmy]", range) or interval not in {"1m", "3m", "5m", "15m", "60m", "1d"}:
            raise failure(400, "INVALID_HISTORY_RANGE_OR_INTERVAL")
        data = await upstream.history(safe_symbol, range, interval)
        if not isinstance(data, dict) or data.get("symbol", "").upper() != safe_symbol or data.get("interval") != interval:
            raise failure(502, "HISTORY_IDENTITY_MISMATCH")
        if not isinstance(data.get("candles"), list) or data.get("lastBarClosed") is not True:
            raise failure(502, "HISTORY_NOT_CLOSED_OR_MISSING")
        return data

    @app.websocket("/v1/viop/live")
    async def viop_live(socket: WebSocket) -> None:
        try:
            upstream = authenticate(socket.headers.get("authorization"))
        except HTTPException:
            await socket.close(code=1008, reason="UNAUTHORIZED_OR_PROVIDER_NOT_CONNECTED")
            return
        await socket.accept()
        try:
            req = await socket.receive_json()
            symbols = req.get("symbols")
            if req.get("action") != "subscribe" or not isinstance(symbols, list) or not 0 < len(symbols) <= 50:
                await socket.close(code=1008, reason="INVALID_SUBSCRIPTION")
                return
            try:
                watched = [require_symbol(s) for s in symbols if isinstance(s, str)]
            except HTTPException:
                await socket.close(code=1008, reason="INVALID_SYMBOL")
                return
            if len(watched) != len(symbols):
                await socket.close(code=1008, reason="INVALID_SYMBOL")
                return
            await socket.send_json({"type": "subscribed", "market": "VIOP", "symbols": watched})
            last_sequence: dict[str, int] = {}
            async for event in upstream.stream(watched):
                if not isinstance(event, dict) or event.get("market") != "VIOP":
                    continue
                symbol = event.get("symbol", "").upper()
                if symbol not in watched:
                    continue
                seq = event.get("sequence")
                if not isinstance(seq, int) or seq < 0 or seq <= last_sequence.get(symbol, -1):
                    continue
                try:
                    data = validate_quote(symbol, {**event, "exchangeTimestamp": event.get("exchangeTimestamp")})
                except HTTPException:
                    continue
                last_sequence[symbol] = seq
                await socket.send_json({**data, "type": "tick", "market": "VIOP", "sequence": seq})
        except (WebSocketDisconnect, RuntimeError):
            return

    return app


def create_configured_app() -> FastAPI:
    """Load ONLY an explicitly installed, licensed provider adapter factory.

    The factory path has form `module:function`; the function returns an adapter
    implementing LicensedViopAdapter. Never invent credentials or fallback data.
    """
    path = os.getenv("BORSA_VIOP_ADAPTER_FACTORY", "").strip()
    adapter = None
    if path:
        module_name, separator, factory_name = path.partition(":")
        if not separator or not module_name or not factory_name or factory_name.startswith("_"):
            raise RuntimeError("INVALID_VIOP_ADAPTER_FACTORY_FORMAT")
        factory = getattr(importlib.import_module(module_name), factory_name)
        if not callable(factory):
            raise RuntimeError("VIOP_ADAPTER_FACTORY_NOT_CALLABLE")
        adapter = factory()
        if adapter is None:
            raise RuntimeError("VIOP_ADAPTER_FACTORY_RETURNED_NONE")
    return create_app(adapter=adapter)


app = create_configured_app()
