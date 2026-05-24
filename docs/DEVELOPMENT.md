# Development

Day-to-day commands. Skim this when you forget the exact incantation.

> All paths are relative to the repo root.

---

## One-time setup

```bash
# Install OrbStack (Docker runtime for macOS)
brew install --cask orbstack
open -a OrbStack                  # finish setup in the GUI

# Install JDK 21 (formula — no sudo required)
brew install openjdk@21

# Add JAVA_HOME to your shell so Gradle/wrapper finds it (openjdk@21 is keg-only)
cat >> ~/.zshrc <<'EOF'
export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || echo /opt/homebrew/opt/openjdk@21)"
export PATH="$JAVA_HOME/bin:$PATH"
EOF
source ~/.zshrc

# Verify
docker --version
docker compose version
java --version            # openjdk 21+
node --version            # >= 20
./gradlew --version       # downloads Gradle 8.10 on first run
```

> If `docker` isn't found in a fresh shell, OrbStack's PATH hasn't been wired in yet. Restart the terminal (or run `export PATH="$HOME/.orbstack/bin:$PATH"`).

---

## Start everything

```bash
# Terminal 1 — infra
docker compose up -d

# Terminal 2 — backend (Flyway migrations run on boot)
./gradlew :backend:bootRun

# Terminal 3 — frontend
cd frontend
npm install                               # first time only
npm run dev
```

Open http://localhost:3000.

---

## Stop everything

```bash
# Ctrl-C the backend and frontend processes, then:
docker compose down                       # keep volumes
docker compose down -v                    # wipe DB + Redis state
```

---

## Backend

All Gradle commands run from the repo root.

| Task | Command |
|---|---|
| Run dev server (hot reload via Spring DevTools, if added) | `./gradlew :backend:bootRun` |
| Build runnable jar (`backend/build/libs/*.jar`) | `./gradlew :backend:bootJar` |
| Run jar directly | `java -jar backend/build/libs/*.jar` |
| Compile only | `./gradlew :backend:classes` |
| Add a runtime dep | edit `backend/build.gradle` → `implementation '<group>:<artifact>:<version>'` |
| Add a test-only dep | edit `backend/build.gradle` → `testImplementation '...'` |
| Run all tests | `./gradlew :backend:test` |
| Run one test class | `./gradlew :backend:test --tests "com.investmentcopilot.service.QuoteServiceTest"` |
| Run one test method | `./gradlew :backend:test --tests "com.investmentcopilot.service.QuoteServiceTest.getPrice_returns_cached_value_without_hitting_yahoo"` |
| HTML test report | `open backend/build/reports/tests/test/index.html` |
| Clean build outputs | `./gradlew :backend:clean` |
| Refresh dependencies | `./gradlew :backend:build --refresh-dependencies` |

> **Linting/formatting:** none configured. Add Spotless + Checkstyle when the team grows. IntelliJ's built-in formatter on the default Java style is sufficient for now.

### Migrations (Flyway)

Migrations live in `backend/src/main/resources/db/migration/` as `V<n>__<message>.sql`. Flyway applies pending migrations on every backend boot.

| Task | How |
|---|---|
| Add a migration | Create `V2__add_foo.sql` next to `V1__initial_holdings_table.sql` |
| Apply pending | Just restart the backend (`./gradlew :backend:bootRun`) |
| Inspect history | `docker exec -it ic-postgres psql -U copilot -d copilot -c 'SELECT * FROM flyway_schema_history;'` |
| Reset DB to clean slate | `docker compose down -v && docker compose up -d && ./gradlew :backend:bootRun` |

> Flyway is **forward-only by default** — no `downgrade`. To "roll back," write a new migration that reverses the change.

### Database inspection

```bash
# psql shell into the running container
docker exec -it ic-postgres psql -U copilot -d copilot

# Quick query from the host
docker exec -i ic-postgres psql -U copilot -d copilot -c 'SELECT * FROM holdings;'

# Reset DB to a clean slate
docker compose down -v                    # nukes volumes
docker compose up -d
./gradlew :backend:bootRun                # Flyway re-runs all migrations
```

### Redis inspection

```bash
docker exec -it ic-redis redis-cli

# In the redis shell:
KEYS quote:*
GET quote:AAPL
TTL quote:AAPL
```

---

## Frontend

| Task | Command |
|---|---|
| Install deps | `npm install` (or `./gradlew :frontend:npmInstall`) |
| Add a dep | `npm install <pkg>` |
| Add a dev-only dep | `npm install -D <pkg>` |
| Dev server | `npm run dev` (or `./gradlew :frontend:dev`) |
| Production build | `npm run build` (or `./gradlew :frontend:build`) |
| Serve build locally | `npm start` |
| Lint | `npm run lint` |

---

## Quick smoke test

```bash
# Backend reachable + DB + Redis ok
curl -sS http://127.0.0.1:8000/health

# Add a holding
curl -X POST http://127.0.0.1:8000/holdings \
  -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL","quantity":"10","cost_basis":"150.50","notes":"test"}'

# List with live P&L
curl -sS http://127.0.0.1:8000/holdings | jq

# Get a quote
curl -sS http://127.0.0.1:8000/quotes/AAPL
```

---

## Project conventions

- **Java:** records for DTOs (immutable, validation-friendly), Lombok for JPA entities, `BigDecimal` for all money. Target Java 21.
- **TypeScript:** ESLint flat config. No formatting tool yet (Prettier is implicit through ESLint defaults).
- **Commits:** prefer small, focused commits.
- **Migrations:** one schema change = one new `V<n>__<msg>.sql`. Flyway is forward-only.
- **Decimal money:** never use `double`/`float` for currency on the backend. JSON wire format serializes `BigDecimal` as strings (see `JacksonConfig`).
- **Decimal display:** numbers come over the wire as JSON strings. Convert with `Number(...)` only at render time.

---

## Branching and commits (when you push to GitHub)

```bash
git config user.email                     # verify identity

git checkout -b feature/<name>
git add -p
git commit -m "feat(scope): summary"
git push -u origin feature/<name>
```

`gh pr create --fill` if you have the GitHub CLI set up and authenticated.

---

## Where to look first when something's off

- API not responding → `docker compose ps` and the `bootRun` terminal output.
- `JAVA_HOME` errors from gradlew → `echo $JAVA_HOME` and re-source `~/.zshrc`.
- Frontend blank or broken → browser DevTools Console; the `npm run dev` terminal.
- Flyway migration failing on boot → Spring will print the failing SQL and version; fix the migration file (or `flyway_schema_history` row) and restart.
- Quotes return null → Yahoo Finance might be throttling; wait 60s and retry, or try a more liquid symbol.

For a longer list of common errors and fixes, see [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md).
