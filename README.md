# Investment Co-Pilot

A personal investment dashboard that grows into an AI-driven research assistant.

**Phase 1 (current):** track holdings, fetch live quotes, compute unrealized P&L.
**Long-term vision:** auto-sync brokerage accounts, stream live prices, generate daily AI briefings over your portfolio with RAG over news/filings, and run bull-vs-bear LLM debates per ticker.

> ⚠️ **Disclaimer:** Personal-use software. **Not financial advice.** Any AI output is illustrative.

---

## Table of contents

- [What it does today](#what-it-does-today)
- [Architecture at a glance](#architecture-at-a-glance)
- [Tech stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Run locally](#run-locally)
- [Project layout](#project-layout)
- [Tests](#tests)
- [Documentation](#documentation)
- [Deploying to AWS](#deploying-to-aws)
- [Roadmap](#roadmap)

---

## What it does today

**Phase 1 — MVP** (live in this repo):

- ✅ **Holdings CRUD** — add/edit/delete positions (symbol, quantity, avg cost, notes)
- ✅ **Live quotes** — Yahoo Finance last price, cached in Redis (60s TTL)
- ✅ **P&L computation** — market value, unrealized P&L (\$ + %) per holding and portfolio total
- ✅ **Auto-refresh dashboard** — re-pulls every 60s via React Query
- ✅ **Health endpoint** — `GET /health` reports DB + Redis connectivity
- ✅ **Migrations** — Flyway-managed schema, auto-applied on boot

See [`docs/FEATURES.md`](docs/FEATURES.md) for a full feature tour.

---

## Architecture at a glance

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ Next.js 16   │     │ Spring Boot 3.3  │     │ Postgres 16      │
│ (Turbopack)  │◀───▶│ Spring Data JPA  │◀───▶│ holdings table   │
│ React Query  │HTTP │ Hibernate 6      │JDBC │                  │
│ TailwindCSS  │     │ HikariCP pool    │     └──────────────────┘
└──────────────┘     │ Java 21          │     ┌──────────────────┐
       │             │                  │◀───▶│ Redis 7          │
       │             │                  │     │ (quote cache)    │
       │             │                  │     └──────────────────┘
       │             │                  │     ┌──────────────────┐
       │             │                  │────▶│ Yahoo Finance    │
       │             └──────────────────┘     │ (chart v8 API)   │
       └──────────────────────────────────────└──────────────────┘
       browser                                  external
```

Detailed diagrams + sequence flows in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Tech stack

| Layer | Tool / library | Version | Purpose |
|---|---|---|---|
| **Container runtime** | OrbStack / Docker Desktop | — | Lightweight Docker engine |
| **Container orchestration** | docker compose v2 | — | Postgres + Redis local stack |
| **Database** | Postgres | 16-alpine | Source of truth for holdings |
| **Cache** | Redis | 7-alpine | Quote cache (Phase 1) |
| **JDK** | Eclipse Temurin / OpenJDK | 21 (LTS) | Backend runtime |
| **Build tool** | Gradle (wrapper) | 8.10 | Monorepo build (`backend` + `frontend`) |
| **Web framework** | Spring Boot | 3.3.5 | REST + DI + auto-config |
| **ORM** | Spring Data JPA / Hibernate | 6.x | Typed JPA repositories |
| **DB driver** | PostgreSQL JDBC | 42.x | Sync JDBC driver |
| **Connection pool** | HikariCP | bundled | Default Spring Boot pool |
| **Migrations** | Flyway | 10.x | Versioned schema, runs on boot |
| **Validation** | Jakarta Validation (Hibernate Validator) | 3.x | DTO validation |
| **Redis client** | Lettuce (via Spring Data Redis) | bundled | Redis access |
| **HTTP client** | Spring `RestTemplate` | bundled | Outbound (Yahoo Finance) |
| **JSON** | Jackson + JavaTimeModule | bundled | Serialization |
| **Boilerplate** | Lombok | 1.18.x | Getter/Setter generation |
| **Tests** | JUnit 5 + Mockito + AssertJ + MockMvc | bundled | Unit + slice tests |
| **Frontend framework** | Next.js (App Router, Turbopack) | 16.2 | React + RSC + bundler |
| **UI runtime** | React | 19.x | UI |
| **Frontend lang** | TypeScript | 5.x | Type safety |
| **Styling** | TailwindCSS | 4.x | Utility CSS |
| **Data fetching** | @tanstack/react-query | 5.x | Cache + auto-refetch |
| **Linting** | ESLint + eslint-config-next | 9.x / 16.2 | Frontend lint |
| **Node runtime** | Node | 20+ | Frontend tooling |

---

## Prerequisites

Install these once. Versions are minimums.

| Tool | Why | Install (macOS) |
|---|---|---|
| **macOS / Linux** | Dev OS | — (Windows works via WSL2) |
| **Homebrew** | Package manager | `/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"` |
| **JDK 21** | Backend runtime | `brew install openjdk@21` (formula — no sudo) **or** `brew install --cask temurin@21` (cask — requires admin password) |
| **OrbStack** | Docker runtime | `brew install --cask orbstack` then open the app once |
| **Node 20+** | Frontend toolchain | `brew install node` (or via `mise`/`nvm`) |
| **git** | Version control | Pre-installed on macOS, else `brew install git` |

> **Gradle:** you do **not** need a system Gradle install — this repo includes a wrapper (`./gradlew`) pinned to Gradle 8.10. The wrapper downloads Gradle on first run.

> **JAVA_HOME (macOS, brew formula):** the `openjdk@21` Homebrew formula is "keg-only" and isn't on your `PATH` by default. Add this to `~/.zshrc` so `gradlew` finds it:
> ```bash
> export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || echo /opt/homebrew/opt/openjdk@21)"
> export PATH="$JAVA_HOME/bin:$PATH"
> ```
> Then `source ~/.zshrc` (or open a new terminal). Verify with `java --version` (should print `openjdk 21…`).

Verify everything:

```bash
java --version            # openjdk 21+
docker --version          # 24+
docker compose version    # v2+
node --version            # v20+
./gradlew --version       # Gradle 8.10 (downloads on first run)
```

---

## Run locally

### 1. Start infrastructure (Postgres + Redis)

```bash
docker compose up -d
docker compose ps     # both should show (healthy) after ~5s
```

### 2. Backend (terminal 1)

From the repo root:

```bash
./gradlew :backend:bootRun
# → http://127.0.0.1:8000
# Flyway runs migrations automatically on boot.
```

Smoke test:
```bash
curl http://127.0.0.1:8000/health
# → {"status":"ok","db":true,"redis":true}
```

### 3. Frontend (terminal 2)

```bash
cd frontend
npm install          # first run only
npm run dev
# → http://localhost:3000
```

### 4. Use it

Open http://localhost:3000 and add a holding (e.g. `AAPL`, qty `10`, cost `150.50`). The dashboard fetches a live quote, computes market value and unrealized P&L, and auto-refreshes every 60s.

### 5. Stop everything

```bash
# Ctrl-C in each terminal, then:
docker compose down              # stop containers (volumes persist)
docker compose down -v           # also wipe DB + Redis state
```

For a full command cheat sheet, see [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).

---

## Project layout

```
InvestmentCopilot/
├── README.md                       ← you are here
├── settings.gradle                 ← monorepo root (includes backend + frontend)
├── build.gradle                    ← shared root config
├── gradlew, gradlew.bat            ← Gradle wrapper (8.10)
├── gradle/wrapper/                 ← wrapper jar + props
├── docker-compose.yml              ← Postgres 16 + Redis 7
├── amplify.yml                     ← AWS Amplify frontend build spec
├── docs/
│   ├── ARCHITECTURE.md             ← system diagrams + decisions
│   ├── FEATURES.md                 ← what works today, with examples
│   ├── ROADMAP.md                  ← 5-phase build ladder
│   ├── DEVELOPMENT.md              ← command cheat sheet
│   ├── DEPLOYMENT.md               ← AWS deployment guide
│   └── TROUBLESHOOTING.md          ← common failure modes
│
├── backend/                        ← Spring Boot service (Java 21)
│   ├── build.gradle                ← Spring Boot + JPA + Redis + Flyway deps
│   ├── Dockerfile                  ← multi-stage build (Gradle → JRE)
│   └── src/
│       ├── main/java/com/investmentcopilot/
│       │   ├── Application.java          ← @SpringBootApplication entry
│       │   ├── config/                   ← AppConfig, JacksonConfig, WebConfig, AppProperties
│       │   ├── controller/               ← HTTP endpoints (Health, Holding, Quote)
│       │   ├── service/                  ← business logic (HoldingService, QuoteService)
│       │   ├── repository/               ← Spring Data JPA interfaces
│       │   ├── model/                    ← JPA entities
│       │   └── dto/                      ← Java records for request/response
│       ├── main/resources/
│       │   ├── application.yml           ← Spring config + Flyway + Jackson
│       │   └── db/migration/             ← Flyway SQL migrations (V1__…sql)
│       └── test/java/com/investmentcopilot/
│
├── frontend/                       ← Next.js dashboard
│   ├── README.md
│   ├── build.gradle                ← Gradle tasks delegating to npm
│   ├── package.json
│   └── src/
│       ├── app/                    ← App Router pages
│       └── lib/api.ts              ← typed API client
│
└── infra/cdk/                      ← AWS CDK TypeScript infrastructure
    └── (see docs/DEPLOYMENT.md)
```

---

## Tests

Backend (JUnit 5 + Mockito + AssertJ + MockMvc):

```bash
./gradlew :backend:test                                        # all tests
./gradlew :backend:test --tests "com.investmentcopilot.service.*"   # service layer only
open backend/build/reports/tests/test/index.html               # HTML report
```

Frontend has no test framework wired up yet — see roadmap.

---

## Documentation

| Doc | What you'll find |
|---|---|
| [`docs/FEATURES.md`](docs/FEATURES.md) | Full tour of Phase 1 features with REST examples |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | System diagrams, request lifecycle, design decisions |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | The 5-phase ladder (MVP → streaming → AI → ML → cloud) |
| [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) | Common commands: build, test, lint, migrations, db inspection |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) | AWS deployment guide (CDK + Fargate + Amplify) |
| [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md) | Common errors and fixes |
| [`frontend/README.md`](frontend/README.md) | Frontend-specific instructions |

---

## Deploying to AWS

Backend runs on **ECS Fargate** behind an **ALB**, with **RDS Postgres** and **ElastiCache Redis**, all provisioned via **AWS CDK (TypeScript)** under `infra/cdk/`.
Frontend deploys to **AWS Amplify Hosting** with auto-builds from GitHub (spec in `amplify.yml`).

See [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) for the full step-by-step guide and ~$50/mo cost estimate.

---

## Roadmap

**Phase 1 (now):** MVP CRUD + live quotes + P&L
**Phase 2:** Real-time streaming, brokerage auto-sync (SnapTrade), TimescaleDB bars
**Phase 3:** RAG-backed daily AI briefings, bull/bear agent debates
**Phase 4:** Classical ML (sentiment, risk metrics, return-prediction backtests)
**Phase 5:** AWS production infra hardening (CDK + CI/CD + observability)

Full details in [`docs/ROADMAP.md`](docs/ROADMAP.md).
