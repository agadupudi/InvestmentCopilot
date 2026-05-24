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
│ Next.js 16       │         │ Spring Boot 3.3  │
│ (Turbopack)      │         │ (embedded Tomcat)│
│ App Router       │         │                  │
│ Client component │         │  Controllers     │
│ /src/app/page.tsx│         │  /health         │
│                  │         │  /holdings       │
│ React Query      │         │  /quotes         │
└──────────────────┘         └────┬─────────┬───┘
                                  │         │
                                JDBC│         │Lettuce
                                  ▼         ▼
                       ┌──────────────┐  ┌──────────────┐
                       │ Postgres 16  │  │ Redis 7      │
                       │ holdings     │  │ quote:<SYM>  │
                       └──────────────┘  └──────────────┘
                                              ▲
                                              │ cache miss
                                              │
                                  ┌───────────┴────────────┐
                                  │ Yahoo Finance chart v8 │
                                  │ (RestTemplate GET)     │
                                  └────────────────────────┘
```

## Request lifecycle: `GET /holdings`

```
Browser
  │
  │ 1. fetch(/holdings)  (auto every 60s via React Query)
  ▼
HoldingController.list()
  │
  │ 2. Spring injects HoldingService
  ▼
HoldingService.listWithQuotes()  (@Transactional readOnly)
  │
  ├──▶ HoldingRepository.findAllByOrderBySymbolAsc()  ← Hibernate: SELECT * FROM holdings
  │
  ├──▶ QuoteService.getPrices(symbols)
  │     │
  │     └─ for each symbol → getPrice(symbol)
  │          ├─ Redis GET quote:<SYM>           (hit → return cached)
  │          │
  │          └─ MISS:
  │               ├─ RestTemplate.getForEntity("https://query1.finance.yahoo.com/v8/finance/chart/{symbol}", ...)
  │               ├─ extract chart.result[0].meta.regularMarketPrice
  │               ├─ Redis SET quote:<SYM> ttl=60s
  │               └─ return BigDecimal
  │
  ▼
Compute market_value, unrealized_pnl, pnl_pct (BigDecimal, HALF_UP)
  │
  ▼
Return List<HoldingWithQuoteDto> → Jackson serializes (snake_case + BigDecimal→string)
  │
  ▼
React Query caches, page renders, sets up next refetch in 60s
```

## Why these choices

**Spring Boot 3.3 on Java 21.** Mature, opinionated framework with first-class auto-configuration for everything in this stack (JPA, Redis, validation, JSON). Java 21 LTS gives us records (used in DTOs), pattern matching, and virtual threads when we need them in Phase 2.

**Spring Data JPA + Hibernate 6.** Interface-only repositories (`HoldingRepository extends JpaRepository<…>`) — Spring generates the implementation. Method names like `findAllByOrderBySymbolAsc()` become SQL automatically. No boilerplate DAO classes.

**HikariCP connection pool** (Spring Boot default). Battle-tested, low-latency, sane defaults.

**Java records for DTOs.** Immutable by construction, zero boilerplate, work cleanly with Jackson and Bean Validation.

**Lombok on the entity.** `@Getter @Setter @NoArgsConstructor` keeps the JPA entity (`Holding`) compact. Records can't be JPA entities (immutable / no no-arg constructor), so we use Lombok there.

**Redis as both cache and future event bus.** Phase 1 uses it only for quote caching (manual `StringRedisTemplate` ops with TTL). Phase 2 will use Redis pub/sub via Spring Data Redis. Same dependency, two roles.

**Postgres for everything.** Eventually we add `pgvector` (RAG, Phase 3) and TimescaleDB (price bars, Phase 2). One database instead of three.

**Next.js 16 App Router.** Server Components reduce client JS; Turbopack gives sub-second HMR. App Router is the future of Next.js.

**React Query on the client.** Caching, deduplication, background refetch, retries — all things you'd otherwise hand-roll with `useEffect`.

**Tailwind v4.** Config-less, fast, well-suited to a dashboard with lots of small layout decisions.

**`BigDecimal` arithmetic on the backend.** Currency math in `double`/`float` is a recipe for "$0.01 missing" bugs. All money lives in `NUMERIC(20,8)` and round-trips through `BigDecimal` with explicit `RoundingMode.HALF_UP`.

**App in host, infra in containers (locally).** Postgres + Redis run in `docker compose`; Spring Boot and Next.js run on the host with hot reload (`bootRun` / `next dev`). The backend container (`backend/Dockerfile`) is only used for deployment — see `docs/DEPLOYMENT.md`.

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

Defined in `backend/src/main/resources/db/migration/V1__initial_holdings_table.sql`. Hibernate's `CamelCaseToUnderscoresNamingStrategy` maps Java field `costBasis` → column `cost_basis` automatically.

There's no users/accounts table yet — single-user, local-only. Auth lands in Phase 2.

## Configuration & secrets

All settings flow through Spring Boot's externalized config:

1. **Defaults** are in `backend/src/main/resources/application.yml`.
2. **OS env vars** override (each YAML key has a `${ENV_VAR:default}` placeholder, e.g. `SPRING_DATASOURCE_URL`, `REDIS_URL`, `CORS_ORIGIN`).
3. **`backend/.env.example`** documents the variables you'd typically override locally or in production.

We never commit real env files (see root `.gitignore`).

App-specific properties (CORS origins, cache TTL) bind into the `AppProperties` record (`config/AppProperties.java`) via `@ConfigurationProperties(prefix = "app")`.

## Lifecycle

Spring Boot manages the lifecycle for us: HikariCP pool, Lettuce Redis client, and embedded Tomcat are all started and stopped through the standard `ApplicationContext`. No custom `@PreDestroy` needed for Phase 1.

## CORS

`config/WebConfig.java` implements `WebMvcConfigurer` and allows the origins listed under `app.cors-origins` (defaults to `http://localhost:3000`). Adjust via `CORS_ORIGIN` env var.

## JSON serialization

`config/JacksonConfig.java` registers a `Jackson2ObjectMapperBuilderCustomizer` that serializes every `BigDecimal` as a JSON **string** (via `toPlainString()`). Together with the YAML setting `spring.jackson.property-naming-strategy: SNAKE_CASE`, the wire format matches the TypeScript types declared in `frontend/src/lib/api.ts` (`cost_basis`, `last_price`, etc., all as strings).

## Caching layer

`quote:<SYMBOL>` keys hold the latest fetched price as a plain string. TTL = `QUOTE_CACHE_TTL_SECONDS` (default 60). Two reasons:

1. **Yahoo Finance rate limits** — hitting them for every page render is a quick way to get throttled.
2. **Aggregation efficiency** — listing N holdings makes 1 Yahoo call per unique symbol per minute, not per request.

When a holding's `unrealized_pnl_pct` ticks live in the dashboard, what's actually changing is React Query re-fetching `/holdings` every 60s and the backend serving from Redis or refreshing on cache expiry.

## Migrations

Flyway is enabled in `application.yml` (`spring.flyway.enabled: true`). On every backend boot, Flyway:

1. Creates `flyway_schema_history` if missing.
2. Scans `classpath:db/migration/` for `V<n>__<name>.sql` files.
3. Applies any not yet recorded.
4. Hibernate then runs in `ddl-auto: validate` mode — it confirms the JPA entity matches the live schema and fails fast if not.

Workflow to add a migration:
1. Add `backend/src/main/resources/db/migration/V2__<message>.sql` (raw SQL — Postgres dialect).
2. Update the matching JPA entity (`Holding`) if needed.
3. Restart the backend — Flyway applies it automatically.

## What's intentionally simple right now

- **No auth.** It's a single-user local app.
- **No background workers.** All work is request-scoped.
- **No WebSocket.** Polling every 60s is fine for Phase 1.
- **No production hardening.** No rate limiting, no CSRF, no observability beyond stdout + `/health` + Actuator `/actuator/health`.
- **Repository tests are not yet integration-tested.** Service and controller layers are covered by mocked unit tests; full DB integration via Testcontainers is a Phase-2 follow-up.

These are deliberate. The plan is to add each one when its phase justifies it (see `docs/ROADMAP.md`).
