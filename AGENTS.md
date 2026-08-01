# AGENTS.md — AI Context File

This file gives AI assistants working on this codebase the context they need to be productive. Read this first.

---

## Project Overview

**Job Search Copilot** — a single-user platform that:
- Aggregates job postings from multiple providers (Greenhouse, Lever, RemoteOK, Findwork, Adzuna, JSearch)
- Scores jobs 0–100 against the user's profile
- Tracks applications through a Kanban pipeline (Matched → Saved → Applied → Viewed → Interview → Offer → Rejected → Ghosted)
- Detects job applications from **Gmail sent emails** using a **local Ollama LLM**
- Notifies of high matches via Email + Telegram

**Safety principle:** The system prepares applications but NEVER auto-submits — the user always clicks Apply manually.

---

## Tech Stack

| Layer | Tech |
|-------|------|
| Backend | Java 17, Spring Boot 3.3, Spring Security, Spring Data JPA/Hibernate, MySQL 8, Flyway, Maven, Lombok, MapStruct, springdoc/OpenAPI, Redis (optional) |
| Frontend | React 19, TypeScript, Vite, TailwindCSS, TanStack React Query, React Router, Axios, shadcn/ui-style components, Framer Motion |
| AI | Ollama (local LLM) via `/api/chat` for email classification |
| Automation | Node/Playwright helper for "Prepare Application" |
| Infra | Docker Compose (MySQL, Redis, backend, frontend) |

---

## Commands

```bash
# Backend — build
cd backend && ./mvnw compile

# Backend — run (http://localhost:8080, Swagger at /swagger-ui.html)
cd backend && ./mvnw spring-boot:run

# Backend — tests
cd backend && ./mvnw test

# Frontend — dev server (http://localhost:5173, proxies /api → :8080)
cd frontend && npm install && npm run dev

# Frontend — build/typecheck
cd frontend && npm run build

# Full stack
docker compose up -d --build
```

**Login:** `admin` with a password set via `ADMIN_PASSWORD` env var (see `application.yml`).

**MySQL local:** `localhost:3306/jobcopilot`, credentials in `application.yml` (which is gitignored — never commit them). No local `mysql` CLI client installed; use the Settings API or write SQL via a tool.

---

## Architecture

```
Frontend (React, src/)
├── pages/          Route screens (Dashboard, Jobs, Applications, Settings…)
├── components/     Reusable (+ ui/ primitives)
├── services/       Axios client + typed API modules (index.ts has all service objects)
├── contexts/       Auth, Theme, Toast
├── types/          Shared TypeScript types
├── layouts/        App shell

Backend (com.jobcopilot)
├── controller/     REST endpoints
├── service/        Business logic (JobIngestionService, SettingsService, GmailSyncService…)
│   └── scoring/    Weighted scoring engine
├── repository/     Spring Data JPA
├── entity/         JPA entities + enums (JobSourceType includes GMAIL_SYNC)
├── dto/            Records (per feature)
├── config/         SecurityConfig, AppProperties, ApplicationStartupRunner
├── integration/    JobSourceAdapter contract + provider adapters (adapter/)
├── security/       JWT provider/filter, rate limiting
├── scheduler/      JobFetchScheduler, JobFetchRunner (cron + manual trigger)
├── gmail/          GmailService, GmailEmail
├── ai/             OllamaClient, AiProperties, EmailClassification
├── notification/   Email/Telegram channel strategy
└── util/           HashUtil (SHA-256 fingerprinting)
```

### Connector Pattern
Every job provider implements `JobSourceAdapter` (`validate()` → `fetchJobs()` → `doNormalize()`). Add a new provider = add one adapter class; `JobIngestionService` auto-discovers all adapters via bean injection.

### Ingestion Pipeline (every 30 min or manual)
```
JobFetchRunner.runOnce() (MDC schedulerRunId set for per-run logs)
  → JobIngestionService.ingest(runId)
    → for each adapter: validate() → fetchJobs() → normalize()
      → fingerprint dedup (SHA-256 of title|company|location)
      → score (0-100) → save Posting → notify if >= threshold
    → update source_fetch_state (lastPostedAt)
    → archiveOldJobs() (soft-delete > 15 days)
```

---

## Critical Configuration

### Settings Priority (IMPORTANT)
```
1. Database (app_setting table)  ──▶ wins over everything (Settings UI writes here)
2. Environment variables
3. application.yml
4. application.yml.example
```

**DB settings always override YAML.** Adapters call `settingsService.getBooleanValue("integration.X.enabled", config.isEnabled())` — the DB row (if it exists and is non-blank) wins. To enable providers, use the Settings UI or `PUT /api/settings` — editing `application.yml` alone may have no effect if DB rows already exist.

### Files
- `backend/src/main/resources/application.yml` — **gitignored, contains secrets** (MySQL password, API keys). Never commit. Edit locally.
- `backend/src/main/resources/application.yml.example` — tracked template. Update this when adding config.
- `backend/src/main/resources/db/migration/` — Flyway migrations (V1–V8). New schema changes go here as V9+, V10+…
- `backend/APP_WORKING.md` — how the app works (architecture deep-dive)
- `backend/GMAIL_SETUP.md` — Gmail + Ollama setup guide (keys, steps, troubleshooting)

---

## Known Gotchas (Learned the Hard Way)

1. **Gmail API: `labelIds=SENT` works; `in:sent` search operator returns 0.** Never use `in:sent` in the `q` param.
2. **Gmail `q` parameter + RestClient double-encoding:** Passing a pre-URL-encoded string (`/messages?q=after%3A...`) to `restClient.get().uri(String)` silently returns 0 results (double-encodes `%`). ALWAYS use the lambda form: `.uri(ub -> ub.path("/messages").queryParam("q", "after:" + epoch).build())`.
3. **Ollama requires `Content-Type: application/json`** — RestClient defaults to `text/plain` for String bodies and Ollama silently rejects (returns 400/empty).
4. **MySQL index key limit (3072 bytes, utf8mb4):** `VARCHAR(1024)` + utf8mb4 = 4096 bytes. For indexed columns > 255 chars use prefix index `UNIQUE (col1, col2(255))`. See `V8__increase_external_id_length.sql`.
5. **Hibernate session corruption on per-adapter failure:** One adapter throwing a DataIntegrityViolation inside `@Transactional` poisons the session — subsequent adapters fail with "null id in Posting entry". Fix: `entityManager.clear()` in the catch block (`JobIngestionService`).
6. **Flyway failed migration:** If a migration fails mid-execution, the app won't start until repaired. Run `./mvnw flyway:repair -Dflyway.url=... -Dflyway.user=... -Dflyway.password=...` then re-run the app. Sometimes repair alone isn't enough — you may also need to fix the migration file itself.
7. **Redis not running locally:** `/actuator/health` shows DOWN because Redis health check fails. Expected. The app works fine without it.
8. **Git push can be blocked** (port 22 refused). If `git push` fails with "Connection refused", it's a network issue — commit locally and retry later. GitHub SSH config is `git@github.com:Param-09-12-01/job-search-repo.git`.
9. **Gmail OAuth:** Web application client type; redirect URI `http://localhost:8080/api/sync/gmail/callback` must be registered in Google Cloud Console. Callback endpoint is public (`PUBLIC_ENDPOINTS` in SecurityConfig). After Google's redirect, the code must be copy-pasted into the Dashboard (no auto-redirect).
10. **Backend runs `com.jobcopilot` at DEBUG level** (`logging.level.com.jobcopilot: DEBUG` in application.yml) — useful for debugging ingestion/sync issues.

---

## Logging Setup (logback-spring.xml)

| Log | Location | Rotation |
|-----|----------|----------|
| General | `backend/logs/application.%d{yyyy-MM-dd}.log` | Daily, 30-day retention, 1GB cap |
| Scheduler per-run | `backend/logs/scheduler/run-{runId}.log` | New file per scheduler run (MDC `schedulerRunId` sifting) |

`JobFetchRunner.runOnce()` sets `MDC.put("schedulerRunId", runId)` at start and removes it in `finally`.

---

## Current Feature: Gmail + Ollama Sync

- **Flow:** `SyncController (POST /api/sync/gmail)` → `GmailSyncService.sync(hours?)` → `GmailService.fetchSentEmails(since)` → `OllamaClient.classify(subject, body)` → dedup (SHA-256 of subject+body via HashUtil) → create `Posting` (source=GMAIL_SYNC) + `Application` (status=APPLIED, method=PREPARED).
- **Endpoints:** `/api/sync/gmail` (POST), `/status`, `/auth-url`, `/auth-callback`, `/disconnect`, `/profile`, `/callback` (public), `/diagnostic` (debug).
- **Ollama model:** default `llama3.2`, configurable via `ai.ollama.*` (Settings UI).
- **Manual trigger only** — no background Gmail sync. Scheduler only fetches job postings.
- Badge: Kanban cards show 📧 for GMAIL_SYNC-sourced postings.
- `JobSourceType` enum: `MANUAL`, `GMAIL_SYNC`, `GREENHOUSE`, `LEVER`, `REMOTEOK`, `FINDWORK`, `ADZUNA`, `JSEARCH`.

---

## Branches

- `master` — stable/main
- `feat/gmail-ollama-sync` — active feature branch (Gmail + Ollama sync, logging config, provider enablement fixes). **Current work lives here.**

---

## Secrets

Never commit secrets. Credentials (MySQL password, API keys, Gmail OAuth client secret) live only in the gitignored `application.yml` and the `app_setting` DB table (masked in Settings UI).
