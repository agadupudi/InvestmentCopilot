# Features (Phase 1)

A tour of everything the app does today — from the user's view and from the API's view.

## 1. Holdings management

**What you do:** add stocks/ETFs you own, with the quantity and your average cost.

**What you see:** an instantly-updating row in the dashboard with live market value and unrealized P&L.

### From the UI

Open http://localhost:3000. The "Add holding" form takes:

| Field | Example | Notes |
|---|---|---|
| Symbol | `AAPL` | Auto-uppercased; yfinance ticker |
| Quantity | `10` or `10.5` | Fractional shares supported |
| Avg cost | `150.50` | Per-share cost basis |
| Notes | `Roth IRA` | Optional, free-form |

Click **Add** and the row appears in the table below.

### From the API

```bash
curl -X POST http://127.0.0.1:8000/holdings \
  -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL","quantity":"10","cost_basis":"150.50","notes":"Roth IRA"}'
```

Response:
```json
{
  "id": 1,
  "symbol": "AAPL",
  "quantity": "10.00000000",
  "cost_basis": "150.50000000",
  "notes": "Roth IRA",
  "created_at": "2026-05-11T00:56:23.853276Z",
  "updated_at": "2026-05-11T00:56:23.853276Z"
}
```

Update partially:
```bash
curl -X PATCH http://127.0.0.1:8000/holdings/1 \
  -H "Content-Type: application/json" \
  -d '{"quantity":"15"}'
```

Delete:
```bash
curl -X DELETE http://127.0.0.1:8000/holdings/1
# 204 No Content
```

## 2. Live quotes (cached)

**What it does:** fetches the latest price for any ticker yfinance supports. Caches in Redis for 60s so repeated calls don't hit Yahoo.

```bash
curl http://127.0.0.1:8000/quotes/AAPL
# {"symbol":"AAPL","price":"293.32"}

curl 'http://127.0.0.1:8000/quotes?symbols=AAPL,TSLA,MSFT'
# {"AAPL":"293.32","TSLA":"184.50","MSFT":"425.10"}
```

Behind the scenes:
- 1st request → yfinance call → write to `quote:AAPL` in Redis with `EX 60`.
- 2nd–Nth requests within 60s → served from Redis.
- After 60s → cache expires → next call refreshes.

## 3. Live P&L on the dashboard

The `GET /holdings` endpoint returns each row enriched with current pricing:

```json
{
  "id": 1,
  "symbol": "AAPL",
  "quantity": "10.00000000",
  "cost_basis": "150.50000000",
  "notes": "Roth IRA",
  "created_at": "...",
  "updated_at": "...",
  "last_price": "293.32000732421875",
  "market_value": "2933.20",
  "unrealized_pnl": "1428.20",
  "unrealized_pnl_pct": 94.90
}
```

Computed as:

```
market_value     = last_price × quantity
cost_total       = cost_basis × quantity
unrealized_pnl   = market_value − cost_total
unrealized_pnl_pct = (unrealized_pnl / cost_total) × 100
```

All currency math uses Python `Decimal` to avoid float drift.

## 4. Auto-refreshing dashboard

The frontend uses **React Query** with `refetchInterval: 60_000`. Once a minute, the table re-pulls `/holdings`, which reads the same Redis-cached prices so it's cheap. After mutations (add/delete), React Query invalidates the cache and refetches immediately so the UI is consistent.

The top of the page shows portfolio totals:
- **Market Value** — sum of `market_value` across all holdings.
- **Cost Basis** — sum of `cost_basis × quantity`.
- **Unrealized P&L** — sum of `unrealized_pnl`. Color-coded green/red.

## 5. Health endpoint

```bash
curl http://127.0.0.1:8000/health
# {"status":"ok","db":true,"redis":true}
```

Pings Postgres (`SELECT 1`) and Redis (`PING`). Useful for:
- Smoke-testing the local stack after `docker compose up`.
- Future Phase 5 readiness probes (k8s/ECS health checks).

## 6. Auto-generated API docs

FastAPI exposes:

- **Swagger UI** — http://127.0.0.1:8000/docs — interactive, "try it out" buttons
- **ReDoc** — http://127.0.0.1:8000/redoc — read-only, nicer for sharing
- **OpenAPI JSON** — http://127.0.0.1:8000/openapi.json — for codegen

Schemas come straight from the Pydantic models in `backend/app/schemas/`. Add a field to a model and the docs update automatically.

## 7. Database migrations

Schema changes are versioned by Alembic. Every migration is a Python file in `backend/alembic/versions/` with `upgrade()` and `downgrade()` functions.

```bash
cd backend
uv run alembic upgrade head      # apply all pending migrations
uv run alembic downgrade -1      # roll back one
uv run alembic current           # show applied revision
uv run alembic history           # full history
```

The initial migration (`alembic/versions/<hash>_initial_holdings_table.py`) creates the `holdings` table and its index.

## 8. Type-safe end to end

- Backend models → Pydantic v2 schemas → JSON.
- Frontend has matching `Holding` / `HoldingCreate` types in `src/lib/api.ts`.
- TypeScript catches schema drift between frontend and backend.

## What you can't do yet (Phase 2+)

Tracked here so you know where the rough edges are:

- ❌ Per-ticker historical chart (Phase 2)
- ❌ Brokerage auto-sync (Phase 2 — SnapTrade)
- ❌ WebSocket streaming prices (Phase 2)
- ❌ News & sentiment (Phase 3)
- ❌ AI daily briefing (Phase 3)
- ❌ Risk metrics, backtesting (Phase 4)
- ❌ Production deployment, CI/CD (Phase 5)
- ❌ Multi-user / auth (Phase 2 minimal, hardened in Phase 5)

See [`ROADMAP.md`](ROADMAP.md) for the full plan.
