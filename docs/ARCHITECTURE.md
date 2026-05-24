# Architecture

Snapshot of how the Phase-1 system is wired and *why* it's wired that way.

## Topology

```
┌──────────────────┐
│ Browser          │
│ React 19 + RQ    │
└────────┬─────────┘
         │ HTTP (JSON)
         │ http://localhost:3000  → SSR/CSR served by Next.js
         │ http://127.0.0.1:8000  → API calls (cross-origin via CORS)
         ▼
┌──────────────────┐         ┌──────────────────┐
│ Next.js 16       │         │ FastAPI          │
│ (Turbopack)      │         │ (uvicorn)        │
│ App Router       │         │                  │
│ Client component │         │  Routes          │
│ /src/app/page.tsx│         │  /health         │
│                  │         │  /holdings       │
│ React Query      │         │  /quotes         │
└──────────────────┘         └────┬─────────┬───┘
                                  │         │
                            asyncpg│         │redis-py
                                  ▼         ▼
                       ┌──────────────┐  ┌──────────────┐
                       │ Postgres 16  │  │ Redis 7      │
                       │ holdings     │  │ quote:<SYM>  │
                       └──────────────┘  └──────────────┘
                                              ▲
                                              │ cache miss
                                              │
                                  ┌───────────┴────────────┐
                                  │ yfinance (sync,        │
                                  │ run in worker thread)  │
                                  └────────────────────────┘
```

## Request lifecycle: `GET /holdings`

```
Browser
  │
  │ 1. fetch(/holdings)  (auto every 60s via React Query)
  ▼
FastAPI route: api/holdings.py::list_holdings
  │
  │ 2. inject AsyncSession via Depends(get_session)
  ▼
services/holdings.py::list_holdings_with_quotes
  │
  ├──▶ list_holdings(session)               ← SQLA: SELECT * FROM holdings
  │
  ├──▶ for each symbol → quotes.get_price(symbol)
  │     │
  │     ├─ Redis GET quote:<SYM>            (hit → return cached)
  │     │
  │     └─ MISS:
  │          ├─ asyncio.to_thread(yfinance.Ticker(...).fast_info.last_price)
  │          ├─ Redis SET quote:<SYM> ttl=60s
  │          └─ return price
  │
  ▼
Compute market_value, unrealized_pnl, pnl_pct (Decimal)
  │
  ▼
Return list[HoldingWithQuote] → FastAPI serializes to JSON
  │
  ▼
React Query caches, page renders, sets up next refetch in 60s
```

## Why these choices

**FastAPI + async stack end-to-end.** Every external I/O (DB, Redis, yfinance) is async or `to_thread`-bridged so a single uvicorn process can handle many concurrent requests. This matters when Phase 2 streams thousands of quote updates per minute.

**SQLAlchemy 2.x async + asyncpg.** Modern typed ORM. asyncpg is the fastest Postgres driver for Python.

**Pydantic v2 everywhere.** One schema definition powers validation, OpenAPI generation, and frontend type inference (via `/openapi.json` if we add code-gen later).

**Redis as both cache and future event bus.** Phase 1 uses it only for quote caching. Phase 2 will use Redis pub/sub to fan out price updates to FastAPI WebSocket clients. Same dependency, two roles.

**Postgres for everything.** Eventually we add the `pgvector` extension (RAG embeddings, Phase 3) and `TimescaleDB` extension (price bars, Phase 2). One database instead of three is a real productivity win.

**Next.js 16 App Router.** Server Components reduce client JS; Turbopack gives sub-second HMR. App Router is the future of Next.js — Pages Router is in maintenance.

**React Query on the client.** Caching, deduplication, background refetch, retries — all things you'd otherwise hand-roll with `useEffect`.

**Tailwind v4.** Config-less, fast, well-suited to a dashboard with lots of small layout decisions.

**Decimal arithmetic on the backend.** Currency math in floating point is a recipe for "$0.01 missing" bugs.

**App in host, infra in containers.** Postgres + Redis run in `docker compose`; FastAPI and Next.js run on the host with hot reload. We containerize the apps later (Phase 5) when we deploy to AWS — this keeps the dev loop fast now.

## Data model (Phase 1)

```sql
CREATE TABLE holdings (
    id          SERIAL PRIMARY KEY,
    symbol      VARCHAR(16) NOT NULL,
    quantity    NUMERIC(20, 8) NOT NULL,
    cost_basis  NUMERIC(20, 8) NOT NULL,
    notes       VARCHAR(512),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_holdings_symbol ON holdings (symbol);
```

`NUMERIC(20, 8)` supports fractional shares (e.g. ETF dividend reinvestments) without precision loss.

There's no users/accounts table yet — single-user, local-only. Auth lands in Phase 2.

## Configuration & secrets

All settings flow through `app/core/config.py` (`Settings`):

1. **Defaults** are in code.
2. **`.env`** in `backend/` overrides defaults for local dev.
3. **OS env vars** override `.env`.

We never commit `.env` (see root `.gitignore`). `.env.example` documents the shape.

## Lifespan

`app/main.py` uses FastAPI's `lifespan` context to dispose the SQLA engine and close the Redis connection on shutdown:

```python
@asynccontextmanager
async def lifespan(app: FastAPI):
    yield
    await engine.dispose()
    await redis.aclose()
```

This is the canonical replacement for `@app.on_event("startup"|"shutdown")`.

## CORS

The backend allows `http://localhost:3000` by default (`CORS_ORIGINS` setting). Adjust if you run the frontend on a different port or host.

## Caching layer

`quote:<SYMBOL>` keys hold the latest fetched price as a string. TTL = `QUOTE_CACHE_TTL_SECONDS` (default 60). Two reasons:

1. **yfinance rate limits** — hitting it for every page render is quick way to get blocked.
2. **Aggregation efficiency** — listing N holdings makes 1 yfinance call per unique symbol per minute, not per request.

When a holding's `unrealized_pnl_pct` ticks live in the dashboard, what's actually changing is React Query re-fetching `/holdings` every 60s and the backend serving from Redis or refreshing on cache expiry.

## Migrations

Alembic is configured in async mode (`alembic/env.py`). Models are imported from `app.models` so autogenerate sees them. Versions live in `alembic/versions/<hash>_<message>.py`.

Workflow:
1. Modify a model.
2. `uv run alembic revision --autogenerate -m "msg"`.
3. **Read the generated diff** — autogenerate misses some things (enums, server defaults, complex constraints).
4. `uv run alembic upgrade head`.

## What's intentionally simple right now

- **No auth.** It's a single-user local app.
- **No background workers.** All work is request-scoped.
- **No WebSocket.** Polling every 60s is fine for Phase 1.
- **No production hardening.** No rate limiting, no CSRF, no observability beyond stdout.
- **No tests beyond a smoke test.** We grow tests with the surface area.

These are deliberate. The plan is to add each one when its phase justifies it (see `docs/ROADMAP.md`).
