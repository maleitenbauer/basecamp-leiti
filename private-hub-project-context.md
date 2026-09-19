# Private Hub App — Project Context

## Overview

A self-hosted, private life-management application running on a personal Hetzner server. Single user (Markus, senior software architect/developer), accessed exclusively via Tailscale (no public exposure) from both phone and PC.

The app is organized into distinct **modules**, each covering a life area, plus an AI assistant module that can act across all of them.

## Goals

- Central hub to manage different life areas through dedicated modules
- Accessible from phone and PC over Tailscale (no App Store, no public internet exposure)
- Modular architecture — new life areas can be added as self-contained modules without touching the core
- AI chat assistant that can perform actions across modules via tool-calling
- Fully self-hosted, single Docker Compose deployment on the Hetzner server

## Proposed Modules & Feature Ideas

**Gaming**
- Backlog tracker (Steam/Epic API import, status: Playing/Backlog/Done)
- Wishlist price alerts (IsThereAnyDeal API)
- Playtime log / session notes

**Work**
- Task/Kanban board
- Time tracking with daily/weekly summaries
- Notes/wiki (Zettelkasten-style, linkable)
- Pomodoro timer with stats

**Leisure / Media**
- Watchlist (movies/series, TMDB API)
- Book tracker (currently reading, progress, library)
- Habit tracker with streaks

**Household / Finance**
- Subscription tracker (costs, cancellation deadlines, next charge)
- Shared shopping list
- Simple budget tracking

**Cross-module**
- Central dashboard with widgets from each module
- Global search across all modules
- Daily log/journal that auto-pulls events from modules

**Assistant (AI module)**
- Chat interface where the user can give tasks/instructions
- Acts as a cross-cutting layer, not an isolated module — can call into other modules via defined tools (e.g. create a task, add to backlog, add a subscription)
- Scheduled/autonomous checks (e.g. daily wishlist price check) via cron-style jobs
- Persisted conversation history

## Architecture Decisions

### Overall approach
- **Modular monolith**, not microservices — single user, single server, so service discovery / multi-deployment overhead isn't justified
- One codebase, cleanly separated by module package/folder, each module communicating only through defined internal APIs

### Repository structure
- **Single monorepo** for both backend and frontend (decided against separate repos)
- Two independent top-level folders/toolchains (no shared build tooling like Turborepo/Nx needed, since backend and frontend use completely different toolchains — Gradle vs. npm/pnpm — and only communicate via a generated OpenAPI client, no shared code to bundle)

```
private-hub/
├── api/                      # Spring Boot (Kotlin) backend
│   └── src/main/kotlin/com/markus/hub/
│       ├── gaming/
│       │   ├── GamingController.kt
│       │   ├── GamingService.kt
│       │   └── internal/            # not accessible from outside the module
│       ├── work/
│       ├── leisure/
│       ├── finance/
│       ├── assistant/
│       │   ├── tools/                # per-module tool definitions for the AI
│       │   └── AssistantService.kt
│       └── core/
│           ├── auth/
│           └── config/
├── web/                       # SvelteKit frontend (static/SPA)
│   └── src/modules/
│       ├── gaming/
│       ├── work/
│       ├── leisure/
│       └── finance/
├── docker-compose.yml
└── README.md
```

### Backend
- **Spring Boot with Kotlin** (chosen over Hono/Node — explicit preference for a JVM-based backend)
- **Spring Modulith** for enforcing module boundaries at compile/test time (`ApplicationModules.verify()`), plus built-in transactional application events for cross-module communication (e.g. "task completed in `work` → event to `assistant`")
- **JPA/Hibernate** for persistence (jOOQ considered as an alternative if more SQL control is wanted later; JPA is sufficient at this scale)
- **springdoc-openapi** to auto-generate the OpenAPI spec from controllers, consumed by the frontend via a generated TypeScript client (`openapi-typescript` + `openapi-fetch`)

### Database
- **PostgreSQL**, single instance
- One schema namespace per module (`gaming.*`, `work.*`, `finance.*`, `assistant.*`, etc.) to keep logical separation without needing separate databases

### Frontend
- **SvelteKit**, built as a static SPA (`adapter-static`) — no server-side logic, purely calls the Spring Boot API
- **Tailwind CSS** for styling
- **PWA** setup via `vite-plugin-pwa` — installable on phone home screen, works identically on PC in-browser; avoids needing native mobile app deployment entirely (no App Store, no cert hassle)
- **TanStack Query** for API state/caching

### Authentication
- Simple session-based auth (Tailscale is the primary network-level security layer; app auth is a secondary layer in case a device is lost)
- Passkeys (WebAuthn) considered as a passwordless option, well supported on both phone and PC

### AI Assistant integration
- **Spring AI** (official Spring project) for LLM integration — supports Anthropic/OpenAI/Ollama out of the box
- Module services expose actions to the assistant via `@Tool`-annotated methods (e.g. `WorkService.createTask()` becomes directly callable by the LLM) — no separate schema layer needed
- LLM: Claude/OpenAI API to start; self-hosted Ollama considered later if cost/privacy become a bigger concern
- Async/scheduled tasks handled via Spring's `@Scheduled` and `@Async` — no external queue system (e.g. BullMQ) needed at this scale
- Chat persistence in `assistant.*` schema (conversations, messages); context kept as a simple message-history window — no vector DB/RAG needed at single-user scale
- Frontend: chat UI with streaming responses (SSE)

### Deployment
- **Docker Compose** on the Hetzner server: `api`, `web`, `postgres` services
- **Tailscale Serve** for HTTPS (needed for PWA service worker to function correctly) — removes the need for a separate reverse proxy like Caddy in the minimal setup
- Backup strategy: scheduled `pg_dump` to host filesystem or Hetzner Storage Box

### CI/CD
- Private GitHub repository
- **Self-hosted GitHub Actions runner installed directly on the Hetzner server** (chosen over cloud-runner + SSH-deploy) — avoids SSH key management and Tailscale-OAuth-in-CI setup entirely; the runner polls from inside the network, so the server doesn't need to be reachable from GitHub
- On push to `main`: build both `api` and `web`, then `docker compose build && docker compose up -d`
- Acceptable trade-off given this is a single-person project on a private repo (main risk would be a compromised workflow dependency, considered low risk here)

## Implementation Plan

**Phase 0 — Setup & scaffolding**
- Docker + Docker Compose on Hetzner, verify Tailscale config
- Repo structure (`api/`, `web/`, `docker-compose.yml`)
- Initialize Spring Boot (Kotlin, Gradle, Spring Modulith, Spring Data JPA, Postgres driver)
- Initialize SvelteKit (adapter-static, Tailwind, vite-plugin-pwa)
- Postgres container + connectivity test
- `core` package: base auth, central config

**Phase 1 — Core infrastructure**
- Finish auth flow (session/passkey)
- Spring Modulith boundary verification test
- Base frontend layout (navigation, dashboard shell)
- springdoc-openapi setup + first generated TS client
- Local dev Docker Compose end-to-end test

**Phase 2 — First module end-to-end (template)**
- Recommended first module: **`work`** (no external API dependencies)
- Entity, repository, service, controller; module boundary via `internal` subpackage
- Frontend: Kanban/task UI wired to generated client
- Establishes the vertical-slice pattern to replicate for other modules

**Phase 3 — Remaining modules**
- `finance`, `leisure`, `gaming`, following the same pattern
- External API integrations live inside each module's own `internal` client (Steam, TMDB, IsThereAnyDeal)
- Dashboard widgets per module

**Phase 4 — Assistant module**
- Spring AI + Anthropic provider config
- `@Tool` annotations on existing module services
- Chat persistence (`assistant.*` schema)
- Chat UI with SSE streaming
- `@Scheduled` jobs for proactive checks (e.g. price alerts)

**Phase 5 — Deployment & hardening**
- Production Docker Compose (multi-stage builds)
- Tailscale Serve HTTPS configuration
- Postgres backup strategy
- Self-hosted GitHub Actions runner setup, deploy workflow

## Open Decisions / Next Steps

- Concrete data model for the first module (`work`) — entity design, endpoints
- Concrete `@Tool` example implementation for Spring AI (e.g. `work` → `create_task`)
- Final project name (candidates discussed: Nucleus, Hearth, Cockpit, Anchor, Mosaik, or simply `private-hub`)
