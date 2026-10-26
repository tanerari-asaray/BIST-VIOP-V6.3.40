"""Opt-in licensed adapter bootstrap for extracted V6.2.2 server sources.

Run from source root: uvicorn server.bootstrap:app. Never exposes synthetic prices.
"""
import importlib
import os
from server.viop_gateway import create_app


def create_configured_app():
    spec = os.getenv('BORSA_VIOP_ADAPTER_FACTORY', '').strip()
    adapter = None
    if spec:
        module, sep, factory = spec.partition(':')
        if not sep or not module or not factory or factory.startswith('_'):
            raise RuntimeError('INVALID_VIOP_ADAPTER_FACTORY_FORMAT')
        constructor = getattr(importlib.import_module(module), factory)
        if not callable(constructor):
            raise RuntimeError('VIOP_ADAPTER_FACTORY_NOT_CALLABLE')
        adapter = constructor()
        if adapter is None:
            raise RuntimeError('VIOP_ADAPTER_FACTORY_RETURNED_NONE')
    return create_app(adapter=adapter)


app = create_configured_app()
