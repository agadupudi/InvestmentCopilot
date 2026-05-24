from fastapi import APIRouter
from sqlalchemy import text

from app.core.db import engine
from app.core.redis import redis

router = APIRouter(tags=["health"])


@router.get("/health")
async def health() -> dict[str, str | bool]:
    db_ok = False
    redis_ok = False
    try:
        async with engine.connect() as conn:
            await conn.execute(text("SELECT 1"))
        db_ok = True
    except Exception:
        db_ok = False
    try:
        redis_ok = bool(await redis.ping())
    except Exception:
        redis_ok = False
    return {"status": "ok", "db": db_ok, "redis": redis_ok}
