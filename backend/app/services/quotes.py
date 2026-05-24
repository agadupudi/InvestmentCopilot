"""Quote service: fetches latest prices via yfinance, caches in Redis."""
from __future__ import annotations

import asyncio
import json
from decimal import Decimal

import yfinance as yf

from app.core.config import get_settings
from app.core.redis import redis

_settings = get_settings()
_CACHE_PREFIX = "quote:"


def _fetch_price_sync(symbol: str) -> Decimal | None:
    """Blocking yfinance call. yfinance is sync, so we run it in a worker thread."""
    ticker = yf.Ticker(symbol)
    fast = ticker.fast_info
    price = getattr(fast, "last_price", None)
    if price is None:
        return None
    return Decimal(str(price))


async def get_price(symbol: str) -> Decimal | None:
    symbol = symbol.upper().strip()
    cache_key = f"{_CACHE_PREFIX}{symbol}"

    cached = await redis.get(cache_key)
    if cached is not None:
        return Decimal(cached)

    price = await asyncio.to_thread(_fetch_price_sync, symbol)
    if price is not None:
        await redis.set(cache_key, str(price), ex=_settings.quote_cache_ttl_seconds)
    return price


async def get_prices(symbols: list[str]) -> dict[str, Decimal | None]:
    unique = sorted({s.upper().strip() for s in symbols})
    results = await asyncio.gather(*(get_price(s) for s in unique))
    return dict(zip(unique, results, strict=True))


async def health_check() -> bool:
    """Round-trip Redis ping to validate the cache layer."""
    pong = await redis.ping()
    return bool(pong)


async def serialize_for_log(symbol: str) -> str:
    """Helper used in tests/debug — returns cached entry if any."""
    cached = await redis.get(f"{_CACHE_PREFIX}{symbol.upper()}")
    return json.dumps({"symbol": symbol, "cached": cached})
