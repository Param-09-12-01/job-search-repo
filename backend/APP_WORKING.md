# Job Search Copilot - How the App Works

## Architecture Overview

```
┌──────────────┐     ┌──────────────────┐     ┌─────────────┐
│   Frontend   │────▶│  Backend (API)   │────▶│   MySQL     │
│  React/Vite  │     │  Spring Boot     │     │  Database   │
│  Port 3000   │     │  Port 8080       │     │  Port 3306  │
└──────────────┘     └──────────────────┘     └─────────────┘
                             │
                      ┌──────┴──────┐
                      │             │
                ┌─────▼────┐ ┌─────▼──────┐
                │  Redis   │ │ External   │
                │ Port 6379│ │ Job APIs   │
                └──────────┘ └────────────┘
```

---

## 1. Job Fetching Pipeline

This is the core feature. Jobs flow through this pipeline automatically every 30 minutes (configurable).

### Flow

```
Scheduler (cron) / Manual trigger (POST /api/scheduler/run)
    │
    ▼
JobFetchRunner.runOnce()
    │  - Saves RUNNING status in its own TransactionTemplate flush
    │  - AtomicBoolean guard prevents overlapping runs
    │
    ▼
JobIngestionService.ingest(schedulerRunId)
    │
    ├── For each enabled JobSourceAdapter:
    │     │
    │     ├── adapter.validate() ──▶ Is enabled? Has API keys?
    │     │
    │     ├── Look up source_fetch_state:
    │     │     ├── If lastPostedAt exists ──▶ fetchJobs(since)
    │     │     └── If not ──▶ fetchJobs() (full fetch, no date filter)
    │     │
    │     ├── adapter.fetchJobs() ──▶ HTTP call to external API
    │     │     │
    │     │     ├── Greenhouse: GET https://boards-api.greenhouse.io/v1/boards/{board}/jobs
    │     │     ├── Lever: GET https://api.lever.co/v0/postings/{company}
    │     │     ├── RemoteOK: GET https://remoteok.com/api
    │     │     ├── Findwork: GET https://findwork.dev/api/jobs/ (requires API key)
    │     │     ├── Adzuna:  GET https://api.adzuna.com/v1/api/jobs/{country}/search/1?...
    │     │     └── JSearch: GET https://jsearch.p.rapidapi.com/search-v2?posted=...
    │     │
    │     ├── adapter.normalize(raw) ──▶ Map to NormalizedJob records
    │     │
    │     ├── Track maxPostedAt across normalized jobs
    │     │
    │     └── Per normalized job:
    │           ├── Compute SHA-256 fingerprint (title|company|location)
    │           ├── Skip if fingerprint exists in DB (dedup)
    │           ├── Skip if source+externalId exists in DB (dedup)
    │           ├── JobScoringService.score(job, profile) ──▶ 0-100 score
    │           ├── Save Posting to database (with schedulerRunId)
    │           └── If score >= threshold:
    │                 └── NotificationService.dispatchHighScore(...)
    │                       ├── Check "notification.email.notify-on-match" flag
    │                       ├── Check each channel's isEnabled()
    │                       ├── Send via Email / Telegram
    │                       └── Persist Notification record
    │
    ├── Update source_fetch_state with maxPostedAt (distinct daily fetch)
    ├── archiveOldJobs() ──▶ soft-delete postings older than 15 days
    │
    ▼
SchedulerLog updated (SUCCESS/FAILED + counts)
```

### Job Sources (Adapters)

| Source | API Key Required | Free? | Notes |
|--------|-----------------|-------|-------|
| **Greenhouse** | No | Yes | Public board tokens (e.g., `airbnb,stripe`) |
| **Lever** | No | Yes | Public company handles (e.g., `netflix,spotify`) |
| **RemoteOK** | No | Yes | Curated remote tech jobs, highest quality |
| **Findwork** | Yes (free API key) | Yes | Tech job aggregator, sign up at findwork.dev |
| **Adzuna** | Yes (APP_ID + APP_KEY) | Free tier available | May be blocked in some regions |
| **JSearch** | Yes (RapidAPI key) | Free tier (200 req/month) | Aggregates from many boards, uses `/search-v2` endpoint |

Each adapter's `validate()` checks:
1. Is it enabled in SettingsService (DB) or AppProperties (application.yml)?
2. Does it have the required API keys?

### Keyword-Based Filtering

Adapters filter jobs by two mechanisms:
1. **Title matching** — fuzzy match against profile titles (substring + 30% token overlap)
2. **Keyword matching** — check if job description contains any profile keyword (e.g., Java, Spring Boot)

This ensures Java/Spring Boot jobs are found even when the title doesn't explicitly say "Java Developer".

### Scoring Engine (0-100)

The `JobScoringService` scores each job against the user's profile using 7 weighted criteria:

| Criterion | Weight | How it works |
|-----------|--------|-------------|
| Title match | 25 | How closely the job title matches profile titles |
| Location match | 10 | Job location vs preferred locations |
| Remote match | 10 | Job remote status vs remote preference |
| Salary match | 15 | Job salary range vs minimum salary requirement |
| Keywords | 20 | How many profile keywords appear in job description |
| Experience | 10 | Seniority level match |
| Freshness | 10 | How recently the job was posted |

---

## 2. How Email Notifications Work (Which Email?)

### The email recipient is determined by this priority chain:

```
1. Database setting (admin panel):
   SettingsService.getValue("notification.email.to")
   │
   ├── If set in DB ──▶ USE THIS EMAIL
   │
   └── If not set in DB ──▶ Fall back to:
         │
         2. application.yml config:
            app.notification.email.to
            │
            └── Value from .env: NOTIFY_EMAIL_TO=you@example.com
```

### Code path (`EmailNotificationSender.java`):

```java
private String resolveRecipient() {
    String configured = properties.getNotification().getEmail().getTo();  // from application.yml / .env
    return settingsService.getValue("notification.email.to", configured); // DB overrides .env
}
```

### The email is configured in `.env`:

```env
NOTIFY_EMAIL_ENABLED=true           # Global on/off for email channel
NOTIFY_EMAIL_TO=you@example.com     # <-- THIS IS THE RECIPIENT
MAIL_HOST=smtp.gmail.com            # SMTP server
MAIL_PORT=587                       # SMTP port
MAIL_USERNAME=you@gmail.com         # Gmail address
MAIL_PASSWORD=your_app_password     # Gmail app password (NOT account password)
```

### Gmail App Password Setup

The `MAIL_PASSWORD` is a **Gmail App Password**, NOT the account password:
1. Go to https://myaccount.google.com/security
2. Enable 2-Step Verification
3. Go to https://myaccount.google.com/apppasswords
4. Generate an app password for "Mail"
5. Use that 16-character password in `MAIL_PASSWORD`

---

## 3. Feature Flags (Runtime Settings)

All settings can be changed at runtime via the admin panel (`PUT /api/settings`).

| Setting Key | Default | Description |
|-------------|---------|-------------|
| `scheduler.enabled` | `true` | Enable/disable the cron scheduler |
| `scheduler.cron` | `0 */30 * * * *` | Cron expression for fetch frequency |
| `notification.score-threshold` | `50` | Minimum score to trigger notification |
| `notification.email.enabled` | `false` | Enable email notifications |
| `notification.email.notify-on-match` | `false` | Send email when high-score job found |
| `notification.email.to` | (from .env) | Recipient email address |
| `notification.telegram.enabled` | `false` | Enable Telegram notifications |
| `integration.greenhouse.enabled` | `false` | Enable Greenhouse job source |
| `integration.lever.enabled` | `false` | Enable Lever job source |
| `integration.remoteok.enabled` | `false` | Enable RemoteOK job source |
| `integration.findwork.enabled` | `false` | Enable Findwork job source |
| `integration.findwork.api-key` | (empty) | Findwork API key |
| `integration.jsearch.enabled` | `false` | Enable JSearch |
| `integration.jsearch.api-key` | (empty) | JSearch RapidAPI key |
| `integration.adzuna.enabled` | `false` | Enable Adzuna job source |
| `integration.adzuna.app-id` | (empty) | Adzuna app ID |
| `integration.adzuna.app-key` | (empty) | Adzuna app key |

### Feature Flag Flow for Email Notifications

```
High-score job found (score >= threshold)
    │
    ▼
NotificationService.dispatchHighScore()
    │
    ├── Check: "notification.email.notify-on-match" == true?
    │     │
    │     ├── NO (default) ──▶ Skip email, log debug message
    │     │
    │     └── YES ──▶ Check: "notification.email.enabled" == true?
    │           │
    │           ├── NO ──▶ Skip email
    │           │
    │           └── YES ──▶ Check: recipient email configured?
    │                 │
    │                 ├── NO ──▶ Skip email
    │                 │
    │                 └── YES ──▶ Send email via SMTP
    │                       └── Persist Notification record
    │
    └── Other channels (Telegram) still checked independently
```

---

## 4. Authentication & Authorization

### JWT Flow

```
POST /api/auth/login { "username": "admin", "password": "your_password" }
    │
    ▼
Returns: { "accessToken": "eyJ...", "refreshToken": "eyJ...", "expiresInSeconds": 3600 }
    │
    ▼
Use token in header: Authorization: Bearer eyJ...
    │
    ▼
Token expires in 1 hour. Use refresh token:
POST /api/auth/refresh { "refreshToken": "eyJ..." }
```

### Authorization Levels

| Level | Endpoints |
|-------|-----------|
| **Public** | `/api/auth/login`, `/api/auth/refresh`, Swagger docs, `/actuator/health` |
| **Authenticated** | All other endpoints (any valid JWT) |
| **ADMIN only** | `POST /api/scheduler/run`, `PUT /api/settings`, `POST /api/jobs/{id}/prepare`, `/actuator/**` |

---

## 5. Application Tracking (Kanban Pipeline)

```
Matched ──▶ Saved ──▶ Applied ──▶ Viewed ──▶ Interview ──▶ Offer
              │                     │            │              │
              │                     │            │              └──▶ Rejected
              │                     │            ├──▶ Ghosted by Me
              │                     │            └──▶ Ghosted by Company (auto)
              │                     ├──▶ Ghosted by Me
              │                     └──▶ Ghosted by Company (auto)
              ├──▶ Ghosted by Me
              └──▶ Ghosted by Company (auto)
```

### Flow:
1. **Matched** - Job scored above threshold
2. **Saved** - User bookmarks a job (`POST /api/jobs/{id}/save`)
3. **Applied** - User creates an application (`POST /api/applications`)
4. **Viewed** - Employer viewed the application
5. **Interview** - Interview scheduled
6. **Offer** - Job offer received
7. **Rejected** - Application rejected
8. **Ghosted by Me** - User manually marks application as ghosted (drag to "Ghosted by Me" column)
9. **Ghosted by Company** - Automatically set by `GhostingService` when an application stays in "Applied" for 7+ days

### Ghosting Detection (Automatic)
- `GhostingService` runs once at startup in a **daemon thread** spawned by `ApplicationStartupRunner`
- Queries all applications in `APPLIED` status where `appliedDate < now - 7 days`
- Bulk-updates them to `GHOSTED_BY_COMPANY` status in a single query
- Runs on every application startup, completely unattended

---

## 6. All API Endpoints

### Auth
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/login` | Public | Login, get JWT tokens |
| POST | `/api/auth/refresh` | Public | Refresh expired access token |

### Jobs
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/jobs` | Auth | List jobs (filterable: query, minScore, remote, source, company) |
| GET | `/api/jobs/{id}` | Auth | Get single job |
| POST | `/api/jobs/{id}/save` | Auth | Bookmark a job |
| DELETE | `/api/jobs/{id}/save` | Auth | Remove bookmark |
| POST | `/api/jobs/{id}/dismiss` | Auth | Dismiss a job |
| POST | `/api/jobs/{id}/prepare` | ADMIN | Open job in browser (Playwright) |

### Applications
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/applications` | Auth | List all applications |
| GET | `/api/applications/board` | Auth | Kanban board grouped by status |
| GET | `/api/applications/{id}` | Auth | Get single application |
| POST | `/api/applications` | Auth | Create application from posting |
| POST | `/api/applications/manual` | Auth | Create manual application (creates posting + application in one call) |
| PATCH | `/api/applications/{id}` | Auth | Update status/notes |
| DELETE | `/api/applications/{id}` | Auth | Delete application |

### Other
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/dashboard` | Auth | Stats + recent activity |
| GET | `/api/notifications` | Auth | List notifications |
| GET | `/api/notifications/unread-count` | Auth | Unread count |
| PATCH | `/api/notifications/{id}/read` | Auth | Mark as read |
| GET | `/api/profile` | Auth | Get user profile |
| PUT | `/api/profile` | Auth | Update profile |
| GET | `/api/scheduler/logs` | Auth | Scheduler run history (offset paginated) |
| POST | `/api/scheduler/run` | ADMIN | Trigger manual ingestion |
| GET | `/api/scheduler/logs/{id}/jobs` | Auth | Jobs fetched in a run (cursor-paginated: `?cursor=&size=20`) |
| GET | `/api/settings` | Auth | Get all settings |
| PUT | `/api/settings` | ADMIN | Update settings |

---

## 7. Configuration Priority

Settings are resolved in this order (highest priority first):

```
1. Database (app_setting table)  ──▶ Admin panel runtime changes
2. Environment variables (.env)  ──▶ docker-compose / shell env
3. application.yml defaults      ──▶ Hardcoded fallbacks
```

This means: **DB settings always override `.env`**. If you set `ADZUNA_ENABLED=true` in `.env` but the DB has `integration.adzuna.enabled=false`, the DB value wins.

---

## 8. Key Files Reference

| File | Purpose |
|------|---------|
| `JobFetchScheduler.java` | Cron trigger |
| `JobFetchRunner.java` | Run guard + logging (TransactionTemplate for RUNNING visibility) |
| `JobIngestionService.java` | Core pipeline orchestration (per-source fetch tracking, archive) |
| `PostingService.java` | Posting queries (cursor-based pagination for run jobs) |
| `PostingSpecifications.java` | JPA specifications (notArchived filter) |
| `SourceFetchState.java` | Entity tracking per-source lastPostedAt for distinct daily fetches |
| `CursorPageResponse.java` | Generic cursor-based pagination DTO |
| `JobScoringService.java` | Score calculation |
| `NotificationService.java` | Notification dispatch + high-score gating |
| `EmailNotificationSender.java` | SMTP email delivery |
| `TelegramNotificationSender.java` | Telegram Bot API delivery |
| `SettingsService.java` | Runtime settings (DB-backed) |
| `AppProperties.java` | Static config binding |
| `SecurityConfig.java` | JWT auth + CORS + rate limiting |
| `GreenhouseAdapter.java` | Greenhouse boards integration |
| `LeverAdapter.java` | Lever postings integration |
| `RemoteOkAdapter.java` | RemoteOK public API integration |
| `FindworkAdapter.java` | Findwork API integration |
| `JSearchAdapter.java` | JSearch RapidAPI integration (supports `posted` param) |
| `AbstractHttpJobSourceAdapter.java` | Base adapter with shared helpers |
| `GmailService.java` | Gmail OAuth2 + labelIds=SENT sent email fetch |
| `GmailSyncService.java` | Gmail → Ollama → dedup → Posting/Application orchestrator |
| `OllamaClient.java` | Local LLM email classification via /api/chat |
| `SyncController.java` | Gmail sync REST endpoints |

---

## 9. Gmail + Ollama Auto-Detection

Automatically detect job applications from sent emails using the Gmail API and a local LLM (Ollama).

### Architecture

```
Dashboard "Sync Gmail" (manual trigger)
    │
    ▼
GmailSyncService.sync(hours?)
    │
    ├── GmailService.fetchSentEmails(since)
    │     └── GET /gmail/v1/users/me/messages?labelIds=SENT&q=after:{epoch}
    │
    ├── For each email:
    │     ├── OllamaClient.classify(subject, body)
    │     │     └── POST /api/chat (llama3.2) "Is this a job application?"
    │     │
    │     ├── If classification says YES (confidence >= 0.5):
    │     │     ├── Compute SHA-256 fingerprint
    │     │     ├── Skip if fingerprint exists (dedup)
    │     │     ├── Create Posting (source=GMAIL_SYNC, company, title)
    │     │     └── Create Application (status=APPLIED, method=PREPARED)
    │     │
    │     └── If classification says NO: skip
    │
    ▼
Returns: GmailSyncResponse { scanned, detected, added, skipped, errors }
```

### Flow Diagram

```
Gmail API (sent emails)
    │
    ▼
Raw email (subject + body)
    │
    ▼
Ollama /api/chat (local LLM)
    │
    ├── Prompt: "Is this email a job application? Respond JSON: {isJobApplication, company, jobTitle, confidence}"
    │
    └── Response: EmailClassification
          │
          ├── isJobApplication=true, confidence>=0.5
          │     └── Create Posting + Application (auto-added to Kanban "Applied")
          │
          └── isJobApplication=false
                └── Skip (logged)
```

### Key Implementation Details

| Aspect | Detail |
|--------|--------|
| Gmail API | Uses `labelIds=SENT` (NOT `in:sent` search — that returns 0 via API) |
| `after:` filter | Unix epoch seconds via `uriBuilder` (avoids RestClient double-encoding) |
| Dedup | SHA-256 fingerprint of email subject + body |
| Ollama | Requires `Content-Type: application/json` (RestClient defaults to text/plain) |
| Source tracking | `GMAIL_SYNC` in `JobSourceType` enum; excluded from manual filters |
| Auth | OAuth2 Web application flow with refresh token |
| Scope | `https://www.googleapis.com/auth/gmail.readonly` |

### Configuration (Settings UI)

| Setting | Key | Description |
|---------|-----|-------------|
| Ollama URL | `ai.ollama.base-url` | e.g. `http://192.168.29.24:11434` |
| Ollama Model | `ai.ollama.model` | e.g. `llama3.2` |
| Ollama Timeout | `ai.ollama.timeout` | e.g. `30s` |
| Gmail Client ID | `gmail.client-id` | Google Cloud OAuth Web client ID |
| Gmail Client Secret | `gmail.client-secret` | Google Cloud OAuth client secret |
| Gmail Redirect URI | `gmail.redirect-uri` | Must match Google Cloud console |
| Gmail Refresh Token | `gmail.refresh-token` | Auto-stored after auth callback |

### Verification

1. Dashboard → Gmail card → Authorize → sign in as your email
2. Confirm "Connected as: your@email.com" shows with message count
3. Select a time range and click "Sync Gmail"
4. Check Applications page for new Kanban cards with 📧 badge
5. Use the "Debug" button on Dashboard for detailed API diagnostics
