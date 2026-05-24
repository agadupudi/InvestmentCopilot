# Troubleshooting

Common failure modes during local dev and the fastest fix for each.

## `docker: command not found` (in a fresh terminal)

OrbStack adds its bin dir to your shell's PATH on next login. Either:

```bash
# Quick fix for this shell
export PATH="$HOME/.orbstack/bin:$PATH"

# Or just open a new terminal
```

Verify with `docker --version`.

---

## `Cannot connect to the Docker daemon`

OrbStack isn't running. Start it:

```bash
open -a OrbStack
```

Wait ~5s, then retry.

---

## `docker compose up` hangs on image pull

First-time pulls download ~150MB total (`postgres:16-alpine` + `redis:7-alpine`). On a slow connection this can take minutes. The output is verbose; let it finish.

If it stalled completely, `Ctrl-C` and re-run.

---

## Backend: `connection refused` to Postgres or Redis

`docker compose ps` should show both `(healthy)`. If they're not running:

```bash
docker compose up -d
docker compose ps
```

If they're running but health checks fail, inspect the logs:

```bash
docker compose logs postgres
docker compose logs redis
```

---

## Gradle: `ERROR: JAVA_HOME is not set`

The Gradle wrapper needs a JDK. With the brew `openjdk@21` formula (keg-only), `java` is not on `PATH` by default. Add to `~/.zshrc`:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || echo /opt/homebrew/opt/openjdk@21)"
export PATH="$JAVA_HOME/bin:$PATH"
```

Then `source ~/.zshrc` and verify `java --version` prints `openjdk 21…`.

---

## Gradle: `Unsupported class file major version` / version mismatch errors

You're running an older JDK. This project requires Java 21. Verify:

```bash
java --version            # must print "openjdk 21" or later
./gradlew --version       # "JVM:" line should match
```

If `java --version` prints 17 or earlier, install JDK 21 (`brew install openjdk@21`) and update `JAVA_HOME`.

---

## Gradle: very slow first build

The wrapper downloads Gradle 8.10 (~120 MB) on first use, and then Spring Boot pulls hundreds of MB of dependencies into your Gradle cache (`~/.gradle/caches/`). Be patient on the first build; subsequent builds are fast.

If a network blip leaves the cache corrupted:

```bash
rm -rf ~/.gradle/caches/modules-2/metadata-* 
./gradlew :backend:build --refresh-dependencies
```

---

## Backend: `Could not open ServerSocket … Address already in use`

Port 8000 is occupied. Find and kill the process, or change the port:

```bash
lsof -ti:8000 | xargs kill
# or:
PORT=8001 ./gradlew :backend:bootRun
```

If you change the port, update `frontend/.env.local` → `NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8001` and restart `npm run dev`.

---

## Backend: `FlywayValidateException` or `Detected applied migration not resolved locally`

This happens when the `flyway_schema_history` table contains versions whose `.sql` files no longer exist (e.g. you deleted or renamed a migration). Options:

1. **Best fix:** restore the missing migration files.
2. **Dev nuke:** wipe the DB and start over: `docker compose down -v && docker compose up -d && ./gradlew :backend:bootRun`.
3. **Surgical fix:** delete the offending row: `docker exec -it ic-postgres psql -U copilot -d copilot -c "DELETE FROM flyway_schema_history WHERE version='2';"`.

---

## Backend: `relation "holdings" does not exist`

Flyway didn't run, or it ran against the wrong database. Check:

1. `docker compose ps` — Postgres healthy?
2. `SPRING_DATASOURCE_URL` env var (if set) — points at the local Postgres?
3. Spring Boot logs on startup — look for `Successfully applied 1 migration to schema "public"`.

If you suspect Flyway is disabled, confirm `application.yml` has `spring.flyway.enabled: true` and that `flyway-core` is on the classpath (`./gradlew :backend:dependencies | grep flyway`).

---

## Backend: HikariCP `Connection is not available, request timed out`

The Postgres pool can't reach the DB or all connections are stuck. Check:

1. `docker compose ps` — Postgres still healthy?
2. `docker compose logs postgres | tail -50` — any errors?
3. Restart the backend (the pool is rebuilt on startup).

---

## Frontend: `npm install` fails with `E401 Unable to authenticate`

Your global `~/.npmrc` is configured for a private registry (e.g. AWS CodeArtifact). The project ships its own `frontend/.npmrc` that overrides this — but only if you run npm from inside `frontend/`.

```bash
cd frontend            # must be inside this dir
cat .npmrc             # should print: registry=https://registry.npmjs.org/
npm install
```

---

## Frontend: blank page at http://localhost:3000

Most likely the dev server isn't running. Start it:

```bash
cd frontend
npm run dev
```

Watch for compile errors in that terminal. If the page is up but blank with errors, open the browser DevTools Console (`Cmd+Option+I`).

---

## Frontend: CORS errors in the browser console

The backend's `app.cors-origins` allows `http://localhost:3000` by default. If you run the frontend on a different port (or on `127.0.0.1` vs `localhost`), set the env var before starting the backend:

```bash
CORS_ORIGIN=http://localhost:3001 ./gradlew :backend:bootRun
```

To allow multiple origins, edit `backend/src/main/resources/application.yml` and add more entries under `app.cors-origins`.

---

## Frontend: "Failed to fetch" on the dashboard

The browser couldn't reach the backend. Check:

1. `curl http://127.0.0.1:8000/health` from the terminal.
2. `frontend/.env.local` → `NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8000`.
3. After editing `.env.local`, you must restart `npm run dev` (Next.js only loads it at startup).

---

## Quotes return `null` for valid tickers

Yahoo Finance is throttling, blocking the user-agent, or briefly down. The 60-second Redis cache exists exactly for this. Wait a minute and retry.

If it persists, hit Yahoo directly to confirm:

```bash
curl -A 'Mozilla/5.0' 'https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1d' | jq '.chart.result[0].meta.regularMarketPrice'
```

If that returns `null` or an error, Yahoo is the problem — wait, or switch to a real market-data provider (Finnhub is in the Phase 2 plan).

---

## Postgres data persists across `docker compose down` even when you didn't want it to

Volumes survive `down` by default. To wipe:

```bash
docker compose down -v
./gradlew :backend:bootRun       # Flyway re-applies all migrations
```

---

## Tests: `NoSuchBeanDefinitionException: RestTemplateBuilder` in `@WebMvcTest`

A `@WebMvcTest` only loads the web layer. If a controller test `@Import`s a configuration that pulls `RestTemplateBuilder` (or other infra beans), the slice can't satisfy it. Import only the configs the slice actually needs — for JSON shape verification, that's `JacksonConfig`, **not** `AppConfig`.

---

## Git: pushed commits show as work email instead of personal

The per-directory git identity uses `includeIf "gitdir:..."` in `~/.gitconfig`. Verify inside the repo:

```bash
git config user.email
# → ashwin4920@gmail.com
```

If wrong, add the `includeIf` block to `~/.gitconfig`:

```
[includeIf "gitdir:~/workspace/"]
    path = ~/.gitconfig-personal
```

---

## SSH: `Permission denied (publickey)` when pushing to GitHub

The dedicated GitHub key isn't being used. Test:

```bash
ssh -T git@github.com
# Should print: "Hi <user>! You've successfully authenticated…"
```

If not:

1. Confirm `~/.ssh/config` has the GitHub block:
   ```
   Host github.com
     HostName github.com
     User git
     IdentityFile ~/.ssh/id_ed25519_github
     IdentitiesOnly yes
   ```
2. Confirm the public key is registered at https://github.com/settings/keys.
3. If you have multiple keys, force the right one:
   ```bash
   GIT_SSH_COMMAND='ssh -i ~/.ssh/id_ed25519_github -o IdentitiesOnly=yes' git push
   ```

---

## Still stuck?

Capture the failing command + full output and diff against the docs in this folder. Two pairs of eyes catch what one missed.
