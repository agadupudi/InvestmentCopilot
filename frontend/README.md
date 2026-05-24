# Frontend — Investment Co-Pilot Dashboard

A Next.js 16 (App Router) dashboard for the Investment Co-Pilot. Phase 1 surfaces a single page: holdings table, add-holding form, and total-portfolio cards, with auto-refresh every 60s.

## Stack

| Concern | Choice |
|---|---|
| Framework | Next.js 16 (App Router, Turbopack) |
| UI runtime | React 19 |
| Language | TypeScript 5 |
| Styling | TailwindCSS v4 |
| Data fetching | @tanstack/react-query v5 |
| Linting | ESLint + eslint-config-next |
| Package manager | npm |

## Layout

```
frontend/
├── package.json
├── .npmrc                       # pin to public registry (overrides global)
├── .env.local                   # NEXT_PUBLIC_API_BASE_URL
├── next.config.ts
├── tsconfig.json, eslint.config.mjs
└── src/
    ├── app/
    │   ├── layout.tsx           # root layout, fonts, <Providers> wrapper
    │   ├── providers.tsx        # QueryClientProvider (React Query)
    │   ├── page.tsx             # dashboard: form, table, totals
    │   └── globals.css          # Tailwind base + design tokens
    └── lib/
        └── api.ts               # typed fetch client
```

## First-time setup

```bash
cd frontend
npm install
```

> If `npm install` fails with `E401 Unable to authenticate`, your global `~/.npmrc` is configured for a private registry (e.g. AWS CodeArtifact). The project ships its own `.npmrc` pinned to the public registry to override that — make sure you're running the command from inside `frontend/`.

## Run the dev server

```bash
npm run dev
# → http://localhost:3000
```

Hot reload is on. Save any file in `src/` and the page updates.

## Environment

`.env.local`:

```
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8000
```

This is the only env var. Anything prefixed `NEXT_PUBLIC_` is exposed to the browser bundle.

> If you change ports on the backend, update this file and restart `npm run dev`.

## Build & start (production-style)

```bash
npm run build       # builds with Turbopack
npm start           # serves the build on :3000
```

## Lint

```bash
npm run lint
```

ESLint config is `eslint.config.mjs` (flat config, ESLint 9).

## How data fetching works

`src/lib/api.ts` is a thin typed wrapper around `fetch`. It throws on non-2xx responses and returns `undefined` on `204 No Content`.

`src/app/page.tsx` (the dashboard) is a Client Component (`"use client"`) and uses React Query:

- `useQuery({ queryKey: ["holdings"], queryFn: api.listHoldings, refetchInterval: 60_000 })` — re-fetches every 60s.
- `useMutation({ mutationFn: api.createHolding, onSuccess: () => qc.invalidateQueries(...) })` — refreshes the list after a create.

Why React Query: caching, retry, background refetch, and request deduplication are all handled — replaces hand-rolled `useEffect` + state.

## Routing

All routing in App Router is filesystem-based:

- `src/app/page.tsx` → `/`
- `src/app/<segment>/page.tsx` → `/<segment>`
- `src/app/<segment>/layout.tsx` → wraps children of `/<segment>`

For Phase 1 we only have `/`. Phase 2 will add `/portfolio/[symbol]/page.tsx` for per-ticker detail.

## Server vs Client Components

Next.js 16 defaults pages and layouts to **Server Components**. We mark `page.tsx` and `providers.tsx` as `"use client"` because they need browser-only APIs (`useState`, event handlers, React Query). Once `"use client"` is set in a file, all its imports are part of the client bundle.

For Phase 3+, we'll move pure data-fetching out of the client into Server Components for better TTFB.

## Adding a dependency

```bash
npm install <package>
npm install -D <package>          # dev only
```

## Common pitfalls

- **First `npm install` is slow.** Subsequent runs use the npm cache.
- **CORS errors** in the browser: confirm the backend's `CORS_ORIGINS` includes `http://localhost:3000`.
- **Numbers as strings.** The backend serializes `Decimal` as JSON string (preserves precision). The frontend converts via `Number(...)` only for display math.
- **Tailwind v4 is config-less.** No `tailwind.config.js` — config lives in `globals.css` via `@theme` blocks (see Tailwind v4 docs).
