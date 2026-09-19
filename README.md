# Basecamp

Private, self-hosted life-management hub (modular monolith). See
[private-hub-project-context.md](private-hub-project-context.md) for goals and architecture decisions.

- `api/` — Spring Boot (Kotlin), Spring Modulith, JPA, PostgreSQL
- `web/` — SvelteKit static SPA, Tailwind, PWA
- `docker-compose.yml` — `postgres`, `api`, `web`

## Quick start on Windows

Needs Windows 10/11 with `winget` (App Installer). In PowerShell, from the repo root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup-windows.ps1   # once: JDK 21, Node, pnpm, PostgreSQL 17, DB, Gradle wrapper, web deps
powershell -ExecutionPolicy Bypass -File scripts\run-dev.ps1         # starts API + web, waits for /api/ping, opens the browser
```

Control the services individually (each runs in its own window):

```powershell
scripts\dev.ps1 api restart     # or: web restart | all restart
scripts\dev.ps1 web stop        # start | stop | restart | status
scripts\dev.ps1 all status
```

The home page shows a **Backend** card: green "Reachable" means the browser, dev proxy, API and
Spring are all working. You can also check directly: <http://localhost:8080/api/ping> and
<http://localhost:8080/actuator/health>.

Local dev uses a native PostgreSQL service (superuser `postgres`/`postgres`, app DB and user `basecamp`/`basecamp`,
localhost only). Docker is only needed for the production deploy.

## Prerequisites (manual / Linux / macOS)

JDK 21, Node 20+ (22 recommended), pnpm, Docker.

## Local development (Docker Postgres)

```bash
cp .env.example .env
docker compose up -d postgres        # database on localhost:5432

cd api && ./gradlew bootRun          # API on :8080
cd web && pnpm install && pnpm dev   # web on :5173, proxies /api to :8080
```

Generate the typed API client (api must be running): `cd web && pnpm api:generate`.

Module boundaries are verified by `ModularityTests` (`./gradlew test`).
