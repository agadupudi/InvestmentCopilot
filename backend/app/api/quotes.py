from decimal import Decimal

from fastapi import APIRouter, HTTPException, Query, status

from app.services import quotes as svc

router = APIRouter(prefix="/quotes", tags=["quotes"])


@router.get("/{symbol}")
async def get_quote(symbol: str) -> dict[str, str | Decimal | None]:
    price = await svc.get_price(symbol)
    if price is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail=f"No quote for {symbol}"
        )
    return {"symbol": symbol.upper(), "price": price}


@router.get("")
async def get_quotes(
    symbols: list[str] = Query(default_factory=list),
) -> dict[str, Decimal | None]:
    if not symbols:
        return {}
    # Allow comma-joined or repeated query params
    flat: list[str] = []
    for s in symbols:
        flat.extend(part for part in s.split(",") if part)
    return await svc.get_prices(flat)
