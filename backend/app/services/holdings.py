from __future__ import annotations

from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.holding import Holding
from app.schemas.holding import HoldingCreate, HoldingUpdate, HoldingWithQuote
from app.services import quotes


async def list_holdings(session: AsyncSession) -> list[Holding]:
    result = await session.execute(select(Holding).order_by(Holding.symbol))
    return list(result.scalars().all())


async def create_holding(session: AsyncSession, payload: HoldingCreate) -> Holding:
    holding = Holding(
        symbol=payload.symbol.upper().strip(),
        quantity=payload.quantity,
        cost_basis=payload.cost_basis,
        notes=payload.notes,
    )
    session.add(holding)
    await session.commit()
    await session.refresh(holding)
    return holding


async def update_holding(
    session: AsyncSession, holding_id: int, payload: HoldingUpdate
) -> Holding | None:
    holding = await session.get(Holding, holding_id)
    if holding is None:
        return None
    data = payload.model_dump(exclude_unset=True)
    for k, v in data.items():
        setattr(holding, k, v)
    await session.commit()
    await session.refresh(holding)
    return holding


async def delete_holding(session: AsyncSession, holding_id: int) -> bool:
    holding = await session.get(Holding, holding_id)
    if holding is None:
        return False
    await session.delete(holding)
    await session.commit()
    return True


async def list_holdings_with_quotes(session: AsyncSession) -> list[HoldingWithQuote]:
    holdings = await list_holdings(session)
    if not holdings:
        return []
    prices = await quotes.get_prices([h.symbol for h in holdings])

    enriched: list[HoldingWithQuote] = []
    for h in holdings:
        last_price = prices.get(h.symbol)
        market_value: Decimal | None = None
        pnl: Decimal | None = None
        pnl_pct: float | None = None
        if last_price is not None:
            market_value = (last_price * h.quantity).quantize(Decimal("0.01"))
            cost_total = (h.cost_basis * h.quantity).quantize(Decimal("0.01"))
            pnl = (market_value - cost_total).quantize(Decimal("0.01"))
            if cost_total > 0:
                pnl_pct = float((pnl / cost_total) * 100)
        enriched.append(
            HoldingWithQuote(
                id=h.id,
                symbol=h.symbol,
                quantity=h.quantity,
                cost_basis=h.cost_basis,
                notes=h.notes,
                created_at=h.created_at,
                updated_at=h.updated_at,
                last_price=last_price,
                market_value=market_value,
                unrealized_pnl=pnl,
                unrealized_pnl_pct=pnl_pct,
            )
        )
    return enriched
