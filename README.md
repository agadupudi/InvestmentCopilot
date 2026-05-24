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
- [Quick start](#quick-start)
- [Project layout](#project-layout)
- [Documentation](#documentation)
- [Roadmap](#roadmap)

---

## What it does today

**Phase 1 — MVP** (live in this repo):

- ✅ **Holdings CRUD** — add/edit/delete positions (symbol, quantity, avg cost, notes)
- ✅ **Live quotes** — yfinance-sourced last price, cached in Redis (60s TTL)
- ✅ **P&L computation** — market value, unrealized P&L (\$ + %) per holding and portfolio total
- ✅ **Auto-refresh dashboard** — re-pulls every 60s via React Query
- ✅ **Health endpoint** — `GET /health` reports DB + Redis connectivity
- ✅ **Auto-generated API docs** — Swagger UI at `/docs`, ReDoc at `/redoc`
- ✅ **Migrations** — Alembic-managed schema, async-aware

See [`docs/FEATURES.md`](docs/FEATURES.md) for a full feature tour with screenshots of each endpoint.

---

## Architecture at a glance

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ Next.js 16   │     │ FastAPI          │     │ Postgres 16      │
│ (Turbopack)  │◀───▶│ Async SQLAlchemy │◀───▶│ holdings table   │
│ React Query  │HTTP │ Pydantic v2      │ asyncpg              │
│ TailwindCSS  │     │ Lifespan hooks   │     └──────────────────┘
└──────────────┘     │                  │     ┌──────────────────┐
       │             │                  │◀───▶│ Redis 7          │
       │             │                  │     │ (quote cache)    │
       │             │                  │     └──────────────────┘
       │             │                  │     ┌──────────────────┐
       │             │                  │────▶│ yfinance         │
       │             └──────────────────┘     │ (Yahoo Finance)  │
       └──────────────────────────────────────└──────────────────┘
       browser                                  external
```

Detailed diagrams + sequence flows in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Tech stack

| Layer | Tool / library | Version (today) | Purpose |
|---|---|---|---|
| **Container runtime** | OrbStack | 2.x | Lightweight Docker engine for macOS |
| **Container orchestration** | docker compose v2 | 5.x | Postgres + Redis local stack |
| **Database** | Postgres | 16-alpine | Source of truth for holdings |
| **Cache / pub-sub** | Redis | 7-alpine | Quote cache (Phase 1), event bus (Phase 2) |
| **Python toolchain** | uv | 0.9+ | Project, venv, lockfile, Python version |
| **Python runtime** | CPython | 3.12 | Modern typing, perf |
| **Web framework** | FastAPI | 0.115+ | Async REST + auto OpenAPI |
| **ASGI server** | uvicorn[standard] | 0.32+ | Production ASGI, uvloop+httptools |
| **ORM** | SQLAlchemy 2.x async | 2.0+ | Typed async ORM |
| **DB driver** | asyncpg | 0.30+ | Async Postgres |
| **Migrations** | Alembic | 1.14+ | Versioned schema |
| **Validation / config** | Pydantic v2 + pydantic-settings | 2.9+ / 2.6+ | Models & 12-factor env |
| **Redis client** | redis-py | 5.2+ (asyncio) | Async Redis |
| **HTTP client** | httpx | 0.27+ | Outbound HTTP (Finnhub later) |
| **Market data** | yfinance | 0.2.50+ | Free quotes (Phase 1) |
| **Lint / format** | ruff | 0.8+ | Fast Python linter+formatter |
| **Tests** | pytest, pytest-asyncio | 8.3+ / 0.24+ | Test runner & async support |
| **Type checking** | mypy | 1.13+ | Static types |
| **Frontend framework** | Next.js (App Router, Turbopack) | 16.2 | React + RSC + bundler |
| **UI runtime** | React | 19.x | UI |
| **Frontend lang** | TypeScript | 5.x | Type safety |
| **Styling** | TailwindCSS | 4.x | Utility CSS |
| **Data fetching** | @tanstack/react-query | 5.x | Cache + auto-refetch |
| **Linting** | ESLint + eslint-config-next | 9.x / 16.2 | Frontend lint |
| **Node runtime** | Node | 20+ | Tooling |

---

## Prerequisites

Install these once. Versions are minimums.

| Tool | Why | Install |
|---|---|---|
| **macOS / Linux** | Dev OS | — (Windows works via WSL2) |
| **Homebrew** | Package manager | `/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"` |
| **OrbStack** | Docker runtime | `brew install --cask orbstack` then open the app once |
| **uv** | Python toolchain | `curl -LsSf https://astral.sh/uv/install.sh \| sh` |
| **Node 20+** | Frontend toolchain | `brew install node` (or via `mise`/`nvm`) |
| **git** | Version control | Pre-installed on macOS, else `brew install git` |

> **Python install:** uv will auto-download Python 3.12 the first time you run `uv sync`. You don't need to install Python separately.

> **npm registry:** the project ships [`frontend/.npmrc`](frontend/.npmrc) pinned to the public npm registry. If your global `~/.npmrc` points elsewhere (e.g. an internal CodeArtifact mirror), this file overrides it for the frontend only.

Verify with:

```bash
docker --version          # Docker version 29.x or later
docker compose version    # Docker Compose v5.x or later
uv --version              # uv 0.9+
node --version            # v20+
```

---

## Quick start

### 1. Start infrastructure (Postgres + Redis)

```bash
cd ~/Private/InvestmentCopilot
docker compose up -d
docker compose ps     # both should show (healthy)
```

### 2. Backend (terminal 1)

```bash
cd backend
uv sync                            # install deps + create .venv (first run only)
uv run alembic upgrade head        # apply migrations (first run only)
uv run uvicorn app.main:app --reload
# → http://127.0.0.1:8000  (Swagger UI at /docs)
```

### 3. Frontend (terminal 2)

```bash
cd frontend
npm install                        # first run only
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
├── README.md                  ← you are here
├── docker-compose.yml         ← Postgres 16 + Redis 7
├── docs/
│   ├── ARCHITECTURE.md        ← system diagrams + decisions
│   ├── FEATURES.md            ← what works today, with examples
│   ├── ROADMAP.md             ← 5-phase build ladder
│   ├── DEVELOPMENT.md         ← command cheat sheet
│   └── TROUBLESHOOTING.md     ← common failure modes
│
├── backend/                   ← FastAPI service
│   ├── README.md              ← backend-specific guide
│   ├── pyproject.toml         ← uv-managed deps
│   ├── alembic.ini, alembic/  ← migrations
│   ├── app/
│   │   ├── main.py            ← FastAPI app + lifespan
│   │   ├── api/               ← HTTP routes
│   │   ├── core/              ← config, db, redis
│   │   ├── models/            ← SQLAlchemy models
│   │   ├── schemas/           ← Pydantic DTOs
│   │   └── services/          ← business logic (quotes, holdings)
│   └── tests/
│
└── frontend/                  ← Next.js dashboard
    ├── README.md              ← frontend-specific guide
    ├── package.json
    ├── .npmrc                 ← pin to public registry
    └── src/
        ├── app/
        │   ├── layout.tsx     ← root layout + providers
        │   ├── providers.tsx  ← React Query client
        │   ├── page.tsx       ← dashboard (form + table)
        │   └── globals.css
        └── lib/
            └── api.ts         ← typed API client
```

---

## Documentation

| Doc | What you'll find |
|---|---|
| [`docs/FEATURES.md`](docs/FEATURES.md) | Full tour of Phase 1 features with REST examples |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | System diagrams, request lifecycle, design decisions |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | The 5-phase ladder (MVP → streaming → AI → ML → cloud) |
| [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) | Common commands: build, test, lint, migrations, db inspection |
| [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md) | Common errors and fixes |
| [`backend/README.md`](backend/README.md) | Backend-specific instructions |
| [`frontend/README.md`](frontend/README.md) | Frontend-specific instructions |

---

## Roadmap

**Phase 1 (now):** MVP CRUD + live quotes + P&L
**Phase 2:** Real-time streaming, brokerage auto-sync (SnapTrade), TimescaleDB bars
**Phase 3:** RAG-backed daily AI briefings, bull/bear agent debates
**Phase 4:** Classical ML (sentiment, risk metrics, return-prediction backtests)
**Phase 5:** AWS production infra (ECS Fargate, Terraform, CI/CD, observability)

Full details in [`docs/ROADMAP.md`](docs/ROADMAP.md).
