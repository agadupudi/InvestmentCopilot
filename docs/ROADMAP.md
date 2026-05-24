# Roadmap

Five phases. Each phase ends with something you'd actually use, deployed somewhere. The point is to add new tech only when the previous phase has been *used* for at least a week.

> Source of truth: `~/.claude/plans/brain-storm-with-me-graceful-stroustrup.md`. This doc is the public-friendly summary.

---

## Phase 1 — MVP (✅ done)

**Goal:** A page showing manually-entered holdings and live P&L.

Features in this phase (all implemented, see [`FEATURES.md`](FEATURES.md)):
- Holdings CRUD (FastAPI + Postgres + SQLAlchemy 2 async)
- Live quotes via yfinance, cached in Redis (60s TTL)
- Auto-refreshing Next.js dashboard via React Query
- Alembic migrations
- Health endpoint
- Local stack via OrbStack + docker compose

**Tech learned:** FastAPI · async SQLAlchemy · uv · Next.js 16 App Router · React Query · TailwindCSS · docker compose.

---

## Phase 2 — Real-time + brokerage sync

**Goal:** Live-updating dashboard with auto-synced holdings.

Capabilities to add:
- **SnapTrade integration** — OAuth into your brokerage, auto-import holdings.
- **WebSocket endpoint** — FastAPI `/ws/quotes` streaming price updates.
- **Background worker** — `arq` (Redis-backed) polling for prices, publishing to Redis pub/sub.
- **TimescaleDB hypertable** — store 1-minute price bars for your tickers (uses Postgres `TimescaleDB` extension).
- **Per-ticker detail page** — `/portfolio/[symbol]` with a price chart from Recharts.
- **Minimal auth** — Clerk or Auth.js so this can work for more than just you.

**Tech learned:** WebSockets · async workers · time-series DB · OAuth integration · streaming UI · Recharts.

**Cost:** ~$15/mo (still on Railway/Fly hosting).

**Done when:** SnapTrade re-sync produces zero diffs vs manual entry for a week.

---

## Phase 3 — AI daily briefing + RAG

**Goal:** Each morning at 7am you receive a personalized portfolio briefing.

Capabilities to add:
- **News + filings ingest** — nightly job pulls news per ticker, embeds with an embedding model, stores in Postgres `pgvector`.
- **Per-holding summary** — RAG-query embeddings for that ticker, summarize with Claude Haiku 4.5 (cheap).
- **Portfolio aggregate** — Claude Sonnet 4.6 turns the per-ticker summaries into a single morning briefing.
- **Bull/bear debate** — two prompts (one bull, one bear) plus a judge prompt synthesizing.
- **"Ask my portfolio" chat** — RAG over news + transactions.
- **Email/Slack delivery** — cron-triggered daily push.

**Tech learned:** embeddings · vector search · prompt engineering · multi-agent orchestration · prompt caching · scheduled jobs.

**Cost:** ~$25–45/mo (LLM tokens added to existing infra).

**Done when:** the briefing surfaces ≥80% of what you'd flag yourself, with no hallucinated tickers.

---

## Phase 4 — Classical ML

**Goal:** Quantitative signals layered on top of the LLM commentary.

Capabilities to add:
- **News sentiment scoring** — FinBERT or a fine-tuned classifier; per-ticker daily score.
- **Risk metrics** — Sharpe ratio, beta vs SPY, sector concentration.
- **Backtesting harness** — uses TimescaleDB bars; walk-forward validation.
- **Return-prediction model** — XGBoost on technical + sentiment features. *Treat as a learning exercise, not a money printer.*
- **Portfolio optimization** — PyPortfolioOpt's efficient-frontier suggestions.
- **MLflow** — track experiments, register models.

**Tech learned:** pandas · scikit-learn / XGBoost · MLflow · backtesting discipline · honest model evaluation.

**Cost:** roughly Phase 3 + maybe ~$10/mo if we use cloud notebooks.

**Done when:** the backtest shows an honest walk-forward report and risk metrics match a reference (Portfolio Visualizer) within tolerance.

---

## Phase 5 — Cloud + DevOps maturity

**Goal:** Production-grade infra suitable for a side project that might one day support a few users.

Capabilities to add:
- **Containerize everything** — backend + frontend Dockerfiles; full local stack via `docker compose`.
- **AWS migration** — Railway → ECS Fargate, RDS Postgres, ElastiCache Redis, S3 for ML artifacts.
- **Terraform** — all infra as code, dev + prod environments.
- **GitHub Actions** — CI (lint, test, build) + CD (auto-deploy on merge to main).
- **OpenTelemetry** — traces and metrics shipped to Grafana Cloud free tier.
- **Secrets** — AWS Secrets Manager.

**Tech learned:** Docker · AWS (ECS/RDS/ElastiCache) · IaC · CI/CD · observability — the full DevOps loop.

**Cost:** ~$30–60/mo on AWS for a small footprint.

**Done when:** a deliberate breaking change to dev is caught by CI; a slow request can be traced end-to-end in Grafana.

---

## Aspirations beyond Phase 5

Not committed yet, but candidates:

- **Mobile app** (React Native) for push alerts and on-the-go briefings.
- **Options analytics** — Greeks, IV, expiration calendar.
- **Multi-user SaaS** — if there's something other people would use.
- **Self-hosted LLM fallback** — for cost or privacy.

---

## Guiding principles

1. **Useful from day one.** Every phase delivers something the user actually uses. No "infra-only" milestones.
2. **Don't move on too fast.** Use the current phase for at least a week before adding the next.
3. **Honest ML.** Walk-forward validation only. Phase 4 is for learning, not get-rich-quick.
4. **One database for as long as possible.** Postgres + extensions instead of three different stores.
5. **Container infra; host apps (until Phase 5).** Fast inner-loop while still developing.
6. **Prompt-cache everything in Phase 3.** LLM costs creep fast otherwise.
