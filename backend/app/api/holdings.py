from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.db import get_session
from app.schemas.holding import HoldingCreate, HoldingRead, HoldingUpdate, HoldingWithQuote
from app.services import holdings as svc

router = APIRouter(prefix="/holdings", tags=["holdings"])


@router.get("", response_model=list[HoldingWithQuote])
async def list_holdings(session: AsyncSession = Depends(get_session)) -> list[HoldingWithQuote]:
    return await svc.list_holdings_with_quotes(session)


@router.post("", response_model=HoldingRead, status_code=status.HTTP_201_CREATED)
async def create_holding(
    payload: HoldingCreate, session: AsyncSession = Depends(get_session)
) -> HoldingRead:
    holding = await svc.create_holding(session, payload)
    return HoldingRead.model_validate(holding)


@router.patch("/{holding_id}", response_model=HoldingRead)
async def update_holding(
    holding_id: int,
    payload: HoldingUpdate,
    session: AsyncSession = Depends(get_session),
) -> HoldingRead:
    holding = await svc.update_holding(session, holding_id, payload)
    if holding is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Holding not found")
    return HoldingRead.model_validate(holding)


@router.delete("/{holding_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_holding(
    holding_id: int, session: AsyncSession = Depends(get_session)
) -> None:
    deleted = await svc.delete_holding(session, holding_id)
    if not deleted:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Holding not found")
