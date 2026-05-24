from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field


class HoldingCreate(BaseModel):
    symbol: str = Field(min_length=1, max_length=16)
    quantity: Decimal = Field(gt=0)
    cost_basis: Decimal = Field(gt=0, description="Average cost per share")
    notes: str | None = Field(default=None, max_length=512)


class HoldingUpdate(BaseModel):
    quantity: Decimal | None = Field(default=None, gt=0)
    cost_basis: Decimal | None = Field(default=None, gt=0)
    notes: str | None = Field(default=None, max_length=512)


class HoldingRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    symbol: str
    quantity: Decimal
    cost_basis: Decimal
    notes: str | None
    created_at: datetime
    updated_at: datetime


class HoldingWithQuote(HoldingRead):
    last_price: Decimal | None = None
    market_value: Decimal | None = None
    unrealized_pnl: Decimal | None = None
    unrealized_pnl_pct: float | None = None
