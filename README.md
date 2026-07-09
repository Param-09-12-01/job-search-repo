# Job Search Copilot

A production-ready, single-user platform that aggregates job postings from multiple providers,
scores them against your preferences, helps you **prepare** applications (it never auto-submits),
tracks them through a Kanban pipeline, and notifies you of high matches via Email and Telegram.

> **Safety first:** The system fills application fields and **stops**. The final **Apply** button
> is always clicked manually by you. It never logs in on your behalf, never bypasses
> authentication, and never stores third-party passwords.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Folder Structure](#folder-structure)
- [Prerequisites](#prerequisites)
- [Quick Start (Docker)](#quick-start-docker)
- [Local Development](#local-development)
- [Configuration & Environment Variables](#configuration--environment-variables)
- [Obtaining API Keys](#obtaining-api-keys)
- [Job Scoring](#job-scoring)
- [Prepare Application (Playwright)](#prepare-application-playwright)
- [API Overview](#api-overview)
- [Testing](#testing)
- [Deployment Guide](#deployment-guide)
- [Adding a New Job Provider](#adding-a-new-job-provider)

---

## Features

- **Multi-source aggregation** via a pluggable connector architecture (Adzuna, JSearch, Greenhouse, Lever).
- **Weighted 0–100 scoring** across title, location, remote, salary, keywords, experience, freshness, with a company blacklist.
- **Background scheduler** that fetches, de-duplicates, scores, stores, and notifies on a configurable cron.
- **Dashboard** with statistic cards, recent notifications, and recent scheduler runs.
- **Job list** with search, filtering, sorting, pagination, save, dismiss, prepare, and track.
- **Application tracker** — a drag-and-drop Kanban board (Matched → Saved → Applied → Viewed → Interview → Offer → Rejected).
- **Prepare Application** — Playwright fills known fields in your own browser profile, then stops.
- **Notifications** — Email + Telegram with a configurable score threshold.
- **Admin settings** — API keys, scheduler frequency, notification config, score threshold, resume & browser paths.
- **Security** — JWT auth, BCrypt password hashing, input validation, global exception handling, rate limiting, audit logging.
- **Modern UI** — responsive, dark mode, animations (Framer Motion), loading skeletons, toasts, error boundaries, confirmation dialogs.

---

## Tech Stack

**Backend:** Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA/Hibernate, MySQL, Flyway,
Maven, Lombok, MapStruct, Bean Validation, springdoc OpenAPI/Swagger, Redis cache, JUnit 5, Mockito.

**Frontend:** React 19, TypeScript, Vite, TailwindCSS, TanStack React Query, React Router, Axios,
React Hook Form, Zod, shadcn/ui-style components, Framer Motion.

**Infrastructure:** Docker & Docker Compose, MySQL, Redis, scheduler, email service, Telegram
notifications, Playwright (Node helper).

---

## Architecture

Clean layering with clear boundaries and dependency inversion at the integration edge:

```
Backend (com.jobcopilot)
├── controller      REST endpoints (thin, validated, Swagger-documented)
├── service         Business logic (scoring, ingestion, applications, notifications…)
│   └── scoring     Weighted scoring engine
├── repository      Spring Data JPA repositories
├── entity          JPA entities + enums
├── dto             Request/response records (per feature)
├── mapper          MapStruct + hand-written mappers
├── config          Security, OpenAPI, persistence, properties, bootstrap
├── security        JWT provider/filter, rate limiting, user details
├── scheduler       Cron trigger + logged runner
├── notification    Channel strategy (Email, Telegram) + sender interface
├── integration     JobSourceAdapter contract + provider adapters
├── exception       Custom exceptions + global handler
└── util            JSON list + hashing helpers

Frontend (src)
├── pages           Route-level screens
├── components      Reusable components (+ ui/ primitives)
├── hooks           (React Query hooks live alongside services)
├── services        Axios client + typed API modules
├── contexts        Auth, Theme, Toast
├── layouts         App shell (sidebar/topbar)
├── types           Shared TypeScript types
├── utils           Helpers (cn)
├── constants       App-wide constants
└── assets          Static assets
```

The **connector architecture** is the extensibility seam: every provider implements
`JobSourceAdapter` (`validate()` → `fetchJobs()` → `normalize()`). The ingestion pipeline injects
**all** adapters, so adding a provider requires only a new adapter class.

---

## Folder Structure

```
AI-APP/
├── backend/                 Spring Boot application
│   ├── src/main/java/com/jobcopilot/...
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/    Flyway migrations (V1, V2)
│   ├── src/test/java/...     Unit / repository / controller / integration tests
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                React 19 + Vite application
│   ├── src/...
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── automation/              Playwright prepare-application helper
│   ├── prepare-application.mjs
│   └── package.json
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## Prerequisites

- **Docker** & **Docker Compose** (recommended path), or
- **Local tooling:** JDK 21, Maven 3.9+, Node 20+, a MySQL 8 instance, and Redis 7.
- **For Prepare Application:** Node 20+ and Playwright installed in `automation/` (`npm install`),
  run on the host that owns your browser profile.

---

## Quick Start (Docker)

```bash
# 1. Clone and enter the project
cd AI-APP

# 2. Create your environment file
cp .env.example .env
#    Edit .env — at minimum set a strong JWT_SECRET and ADMIN_PASSWORD.

# 3. Build and start the full stack (MySQL, Redis, backend, frontend)
docker compose up -d --build

# 4. Open the app
#    Frontend:  http://localhost:3000
#    Swagger:   http://localhost:8080/swagger-ui.html
#    Login with ADMIN_USERNAME / ADMIN_PASSWORD from your .env
```

Flyway creates the schema and seeds default settings/sources on first backend boot. A bootstrap
admin account is created from `ADMIN_*` variables.

To stop: `docker compose down` (add `-v` to also drop the MySQL/Redis volumes).

---

## Local Development

**Backend**
```bash
cd backend
# Point to a running MySQL + Redis (see application.yml env vars)
mvn spring-boot:run
# API: http://localhost:8080  ·  Swagger: http://localhost:8080/swagger-ui.html
```

**Frontend**
```bash
cd frontend
npm install
npm run dev
# Vite dev server: http://localhost:5173 (proxies /api to http://localhost:8080)
```

**Automation helper**
```bash
cd automation
npm install          # installs Playwright + Chromium
```

---

## Configuration & Environment Variables

All variables have safe defaults for local use; override them in `.env` (Docker) or your shell.

| Variable | Description | Default |
|---|---|---|
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | MySQL database & credentials | `jobcopilot` |
| `DB_ROOT_PASSWORD` | MySQL root password (compose only) | `rootpassword` |
| `DB_PORT` / `REDIS_PORT` | Exposed DB / Redis ports | `3306` / `6379` |
| `BACKEND_PORT` / `FRONTEND_PORT` | Exposed app ports | `8080` / `3000` |
| `JWT_SECRET` | HMAC secret, **≥ 32 bytes** | _override me_ |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `ADMIN_EMAIL` | Bootstrap admin login | `admin` / `admin123!` |
| `CORS_ORIGINS` | Comma-separated allowed origins | `http://localhost:3000,http://localhost:5173` |
| `SCHEDULER_ENABLED` | Enable background ingestion | `true` |
| `SCHEDULER_CRON` | Spring cron (6 fields) | `0 */30 * * * *` |
| `ADZUNA_ENABLED` / `ADZUNA_APP_ID` / `ADZUNA_APP_KEY` | Adzuna provider | `false` |
| `JSEARCH_ENABLED` / `JSEARCH_API_KEY` | JSearch (RapidAPI) provider | `false` |
| `GREENHOUSE_ENABLED` / `GREENHOUSE_BOARDS` | Greenhouse boards (CSV) | `false` |
| `LEVER_ENABLED` / `LEVER_COMPANIES` | Lever companies (CSV) | `false` |
| `NOTIFY_EMAIL_ENABLED` / `MAIL_*` / `NOTIFY_EMAIL_TO` | Email notifications (SMTP) | `false` |
| `NOTIFY_TELEGRAM_ENABLED` / `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` | Telegram notifications | `false` |
| `BROWSER_PATH` / `BROWSER_PROFILE_PATH` | Playwright browser & profile paths | _empty_ |

Most of these are also editable at runtime via **Settings** in the UI (secrets are masked).

---

## Obtaining API Keys

- **Adzuna** — register at <https://developer.adzuna.com/>. You receive an `app_id` and `app_key`.
- **JSearch** — subscribe on RapidAPI: <https://rapidapi.com/letscrape-6bRBa3QguO5/api/jsearch>. Use the `X-RapidAPI-Key`.
- **Greenhouse** — no key needed. Use public board tokens (the slug in `boards.greenhouse.io/<token>`), e.g. `airbnb`.
- **Lever** — no key needed. Use public company handles (the slug in `jobs.lever.co/<company>`), e.g. `netflix`.
- **Telegram** — create a bot with [@BotFather](https://t.me/BotFather) to get a bot token; get your
  `chat_id` (e.g. via [@userinfobot](https://t.me/userinfobot)).

Enable a provider by setting its `*_ENABLED=true` and supplying its credentials/handles.

---

## Job Scoring

Each posting receives a **0–100** score. Criteria contribute a fraction of their configured weight
(defaults sum to 100):

| Criterion | Default weight |
|---|---|
| Title match | 25 |
| Required keywords | 20 |
| Salary match | 15 |
| Location match | 10 |
| Remote match | 10 |
| Experience match | 10 |
| Freshness | 10 |

A **blacklisted company** collapses the score to **0**. Weights are configurable under
`app.scoring.weights.*`.

---

## Prepare Application (Playwright)

The **Prepare** action calls `POST /api/jobs/{id}/prepare`, which launches the Node Playwright
helper (`automation/prepare-application.mjs`). The helper:

1. Opens your browser using your existing profile (so you stay logged in as *yourself*).
2. Navigates to the posting's application page.
3. Fills name, email, phone, resume, LinkedIn, GitHub, and portfolio by best-effort selectors.
4. **Stops.** It never clicks Apply/Submit, never types into password fields, never logs in.

Set `BROWSER_PATH` / `BROWSER_PROFILE_PATH` (or the Settings equivalents) and your resume path.
Because it drives a local browser, run the backend on your own machine for this feature (not inside
the container) — or configure the container with access to a browser and profile volume.

---

## API Overview

Interactive docs: **`/swagger-ui.html`**. All routes require a Bearer JWT except `/api/auth/*`.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `POST /api/auth/refresh` |
| Profile | `GET/PUT /api/profile` |
| Jobs | `GET /api/jobs`, `GET /api/jobs/{id}`, `POST /api/jobs/{id}/save`, `DELETE /api/jobs/{id}/save`, `POST /api/jobs/{id}/dismiss`, `POST /api/jobs/{id}/prepare` |
| Applications | `GET /api/applications`, `GET /api/applications/board`, `POST`, `PATCH /{id}`, `DELETE /{id}` |
| Notifications | `GET /api/notifications`, `GET /api/notifications/unread-count`, `PATCH /{id}/read` |
| Scheduler | `GET /api/scheduler/logs`, `POST /api/scheduler/run` |
| Settings | `GET/PUT /api/settings` |
| Dashboard | `GET /api/dashboard` |

---

## Testing

```bash
cd backend
mvn test
```

Coverage spans layers:
- **Unit:** scoring engine, JWT provider, utilities, adapter normalization.
- **Service:** application lifecycle (Mockito).
- **Repository:** de-duplication predicates (`@DataJpaTest`, H2).
- **Controller:** auth contract & validation (`@WebMvcTest`).
- **Integration:** context load smoke test with the `test` profile.

---

## Deployment Guide

1. **Provision** a host with Docker & Docker Compose.
2. **Secrets:** set a strong `JWT_SECRET` (≥ 32 bytes) and `ADMIN_PASSWORD`; supply provider keys and
   notification credentials in `.env`. Never commit `.env`.
3. **Build & run:** `docker compose up -d --build`.
4. **Reverse proxy / TLS:** front the `frontend` (port 80) with a TLS-terminating proxy (nginx,
   Caddy, Traefik). Update `CORS_ORIGINS` to your public origin.
5. **Persistence:** MySQL and Redis use named volumes (`mysql-data`, `redis-data`). Back them up.
6. **Migrations:** Flyway runs automatically on backend startup.
7. **Scheduler:** tune `SCHEDULER_CRON` and score threshold to control fetch cadence and alert volume.
8. **Scaling:** the backend is stateless (JWT) aside from the DB/Redis; run multiple replicas behind
   a load balancer if needed. Ensure only one scheduler is active (or externalize scheduling) to
   avoid duplicate runs.

---

## Adding a New Job Provider

1. Add a value to `JobSourceType`.
2. Implement `JobSourceAdapter` (extend `AbstractHttpJobSourceAdapter` for HTTP providers):
   implement `type()`, `validate()`, `fetchJobs()`, and `doNormalize()`.
3. Add its configuration under `app.integration.<provider>` in `application.yml` (+ env vars).
4. That's it — the ingestion pipeline auto-discovers the new adapter bean; no other code changes.

---

**License:** Proprietary. Built for personal, single-user job searching. Respect each provider's
API terms of use, and remember: you always click **Apply** yourself.
