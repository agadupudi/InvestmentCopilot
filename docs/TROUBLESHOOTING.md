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

## Backend: `sqlalchemy.exc.NoSuchModuleError: Can't load plugin: sqlalchemy.dialects:postgresql.asyncpg`

Your `DATABASE_URL` is missing the `+asyncpg` suffix.

```diff
- postgresql://copilot:copilot@localhost:5432/copilot
+ postgresql+asyncpg://copilot:copilot@localhost:5432/copilot
```

---

## Backend: `relation "holdings" does not exist`

You haven't applied migrations yet:

```bash
cd backend
uv run alembic upgrade head
```

---

## Backend: `uv: command not found`

Install uv:

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
```

Then restart your shell or `source ~/.zshrc`.

---

## Backend: `uv sync` fails on first run

If it fails downloading Python 3.12, you may be behind a proxy or have flaky network. Retry once. If it still fails:

```bash
uv python install 3.12
uv sync
```

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

The backend's `CORS_ORIGINS` allows `http://localhost:3000` by default. If you run the frontend on a different port (or on `127.0.0.1` vs `localhost`), update the backend `.env`:

```
CORS_ORIGINS=["http://localhost:3000","http://127.0.0.1:3000"]
```

Restart uvicorn after editing `.env`.

---

## Frontend: "Failed to fetch" on the dashboard

The browser couldn't reach the backend. Check:

1. `curl http://127.0.0.1:8000/health` from the terminal.
2. `frontend/.env.local` → `NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8000`.
3. After editing `.env.local`, you must restart `npm run dev` (Next.js only loads it at startup).

---

## Quotes return `null` for valid tickers

yfinance is rate limiting you. The 60-second Redis cache exists exactly for this. Wait a minute and retry. If it persists:

```bash
# Test yfinance directly
cd backend
uv run python -c "import yfinance as yf; print(yf.Ticker('AAPL').fast_info.last_price)"
```

If that returns `None`, yfinance is unavailable — wait, or switch to Finnhub (Phase 2 plan).

---

## Postgres data persists across `docker compose down` even when you didn't want it to

Volumes survive `down` by default. To wipe:

```bash
docker compose down -v
```

Then re-run migrations:

```bash
cd backend && uv run alembic upgrade head
```

---

## "Port 8000 already in use" / "Port 3000 already in use"

Find and kill the process:

```bash
lsof -ti:8000 | xargs kill        # backend
lsof -ti:3000 | xargs kill        # frontend
```

Or change the port:

```bash
# backend
uv run uvicorn app.main:app --reload --port 8001

# frontend
PORT=3001 npm run dev
```

If you change ports, update CORS / `NEXT_PUBLIC_API_BASE_URL` accordingly.

---

## Migration generated nothing / empty `upgrade()` and `downgrade()`

Alembic autogenerate compares model metadata to the DB. If models match, the migration is empty. Common causes:

1. The model wasn't imported in `alembic/env.py` (we import via `from app.models import *` — make sure new models are exported in `app/models/__init__.py`).
2. You forgot to create a new model file.

Delete the empty migration file before committing.

---

## Git: pushed commits show as `ashwingd@amazon.com` (work email) instead of personal

The per-directory git identity uses `includeIf "gitdir:~/Private/"` in `~/.gitconfig`. That match is **path-prefix sensitive**.

```bash
# Inside ~/Private/InvestmentCopilot — should print personal email:
git config user.email
# → ashwin4920@gmail.com
```

If it prints `ashwingd@amazon.com`:
- You moved the repo outside `~/Private/`.
- Your `~/.gitconfig` is missing the `includeIf` block. Add:
  ```
  [includeIf "gitdir:~/Private/"]
      path = ~/.gitconfig-personal
  ```

---

## SSH: `Permission denied (publickey)` when pushing to GitHub

The dedicated GitHub key isn't being used. Test:

```bash
ssh -T git@github.com
# Should print: "Hi agadupudi! You've successfully authenticated…"
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
2. Confirm the public key (`~/.ssh/id_ed25519_github.pub`) is registered at https://github.com/settings/keys.
3. If you have multiple keys, force the right one:
   ```bash
   GIT_SSH_COMMAND='ssh -i ~/.ssh/id_ed25519_github -o IdentitiesOnly=yes' git push
   ```

---

## Still stuck?

Capture the failing command + full output and diff against the docs in this folder. Two pairs of eyes catch what one missed.
