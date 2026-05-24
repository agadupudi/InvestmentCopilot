# Features (Phase 1)

A tour of everything the app does today — from the user's view and from the API's view.

## 1. Holdings management

**What you do:** add stocks/ETFs you own, with the quantity and your average cost.

**What you see:** an instantly-updating row in the dashboard with live market value and unrealized P&L.

### From the UI

Open http://localhost:3000. The "Add holding" form takes:

| Field | Example | Notes |
|---|---|---|
| Symbol | `AAPL` | Auto-uppercased; Yahoo Finance ticker |
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
  "quantity": "10",
  "cost_basis": "150.50",
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

> PATCH semantics: any field omitted (or sent as `null`) is left unchanged. To clear `notes`, send a non-null new value.

Delete:
```bash
curl -X DELETE http://127.0.0.1:8000/holdings/1
# 204 No Content
```

## 2. Live quotes (cached)

**What it does:** fetches the latest price for any ticker Yahoo Finance returns. Caches in Redis for 60s so repeated calls don't hit Yahoo.

```bash
curl http://127.0.0.1:8000/quotes/AAPL
# {"symbol":"AAPL","price":"293.32"}

curl 'http://127.0.0.1:8000/quotes?symbols=AAPL,TSLA,MSFT'
# {"AAPL":"293.32","TSLA":"184.50","MSFT":"425.10"}
```

Behind the scenes:
- 1st request → `RestTemplate` GET to `https://query1.finance.yahoo.com/v8/finance/chart/<SYM>` → read `chart.result[0].meta.regularMarketPrice` → write to `quote:<SYM>` in Redis with `EX 60`.
- 2nd–Nth requests within 60s → served from Redis.
- After 60s → cache expires → next call refreshes.

## 3. Live P&L on the dashboard

The `GET /holdings` endpoint returns each row enriched with current pricing:

```json
{
  "id": 1,
  "symbol": "AAPL",
  "quantity": "10",
  "cost_basis": "150.50",
  "notes": "Roth IRA",
  "created_at": "...",
  "updated_at": "...",
  "last_price": "293.32",
  "market_value": "2933.20",
  "unrealized_pnl": "1428.20",
  "unrealized_pnl_pct": 94.90
}
```

Computed as:

```
market_value     = last_price × quantity                  (HALF_UP, scale 2)
cost_total       = cost_basis × quantity                  (HALF_UP, scale 2)
unrealized_pnl   = market_value − cost_total              (HALF_UP, scale 2)
unrealized_pnl_pct = (unrealized_pnl / cost_total) × 100  (double)
```

All currency math uses Java `BigDecimal` with explicit `RoundingMode.HALF_UP` to avoid float drift.

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

Implementation (`controller/HealthController.java`):
- `JdbcTemplate.queryForObject("SELECT 1", Integer.class)` — pings Postgres
- `StringRedisTemplate.opsForValue().get("__health__")` — pings Redis

Both flags are `false` on exception, so the endpoint always returns `200 OK` with the current liveness state. Use for:
- Smoke-testing the local stack after `docker compose up`.
- ECS/Fargate target-group health checks in production (see `docs/DEPLOYMENT.md`).

Spring Boot Actuator's richer `/actuator/health` is also exposed (`management.endpoints.web.exposure.include: health, info` in `application.yml`) if you want deeper signals (disk space, individual component status, etc.).

## 6. Database migrations (Flyway)

Schema changes are versioned by Flyway. Every migration is a SQL file in `backend/src/main/resources/db/migration/` named `V<n>__<message>.sql`.

Flyway runs on every backend boot — there are no manual migration commands.

| Task | How |
|---|---|
| Add a migration | drop `V2__add_foo.sql` next to V1 and restart the backend |
| Inspect history | `docker exec -it ic-postgres psql -U copilot -d copilot -c 'SELECT * FROM flyway_schema_history;'` |
| Clean rebuild | `docker compose down -v && docker compose up -d && ./gradlew :backend:bootRun` |

The initial migration (`V1__initial_holdings_table.sql`) creates the `holdings` table and its symbol index.

## 7. Type-safe end to end

- Backend JPA entity (`Holding`) → DTO records (`HoldingReadDto`, `HoldingWithQuoteDto`) → Jackson serializes to snake_case JSON with `BigDecimal` as strings.
- Frontend has matching `Holding` / `HoldingCreate` types in `frontend/src/lib/api.ts`.
- Field names match exactly (`cost_basis`, `last_price`, `unrealized_pnl_pct`), enforced by `spring.jackson.property-naming-strategy: SNAKE_CASE` + `JacksonConfig.bigDecimalAsStringCustomizer()`.

## What you can't do yet (Phase 2+)

Tracked here so you know where the rough edges are:

- ❌ Per-ticker historical chart (Phase 2)
- ❌ Brokerage auto-sync (Phase 2 — SnapTrade)
- ❌ WebSocket streaming prices (Phase 2)
- ❌ News & sentiment (Phase 3)
- ❌ AI daily briefing (Phase 3)
- ❌ Risk metrics, backtesting (Phase 4)
- ❌ CI/CD automation (Phase 5 — manual deploy via `docs/DEPLOYMENT.md` for now)
- ❌ Multi-user / auth (Phase 2 minimal, hardened in Phase 5)

See [`ROADMAP.md`](ROADMAP.md) for the full plan.
