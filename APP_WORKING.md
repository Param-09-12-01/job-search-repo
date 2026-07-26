# App Working State

## Feature: Gmail + Ollama Auto-Detection of Job Applications

**Branch:** `feat/gmail-ollama-sync`

### Objective
Integrate Gmail (sent emails) + Ollama (local LLM) to automatically detect job applications and create Kanban entries, eliminating manual entry for emailed applications.

### Architecture
```
Gmail API (labelIds=SENT) → Ollama /api/chat (classify) → Dedup (SHA-256) → Posting + Application
```

### Components

| Component | File | Status |
|-----------|------|--------|
| `GmailService` | `backend/.../gmail/GmailService.java` | ✅ Working |
| `GmailSyncService` | `backend/.../service/GmailSyncService.java` | ✅ Working |
| `OllamaClient` | `backend/.../ai/OllamaClient.java` | ✅ Working |
| `AiProperties` | `backend/.../ai/AiProperties.java` | ✅ Working |
| `SyncController` | `backend/.../controller/SyncController.java` | ✅ Working |
| `GmailEmail` | `backend/.../gmail/GmailEmail.java` | ✅ Working |
| `EmailClassification` | `backend/.../ai/EmailClassification.java` | ✅ Working |
| `GmailSyncResponse` / `GmailSyncStatusResponse` | `backend/.../dto/sync/` | ✅ Working |
| Frontend sync service | `frontend/src/services/index.ts` | ✅ Working |
| Dashboard Gmail card | `frontend/src/pages/DashboardPage.tsx` | ✅ Working |
| Applications GMAIL_SYNC badge | `frontend/src/pages/ApplicationsPage.tsx` | ✅ Working |
| Settings AI & Gmail section | `frontend/src/pages/SettingsPage.tsx` | ✅ Working |

### Configuration (Settings Page)
- **Ollama Base URL** — `ai.ollama.base-url` (default: `http://192.168.29.24:11434`)
- **Ollama Model** — `ai.ollama.model` (default: `llama3.2`)
- **Ollama Timeout** — `ai.ollama.timeout` (default: `30s`)
- **Gmail Client ID / Secret** — Google Cloud OAuth 2.0 Web application credentials
- **Gmail Redirect URI** — `http://localhost:8080/api/sync/gmail/callback`
- **Gmail Refresh Token** — stored automatically after authorization

### Known Issues / Observations
- `labelIds=SENT` works correctly (returns sent emails)
- `q` parameter with `after:` + Unix epoch seconds works when encoded via `uriBuilder` (RestClient was double-encoding raw URI strings with pre-encoded `%3A`)
- `in:sent` search operator returns 0 results via the API (use `labelIds=SENT` instead)
- Ollama requires explicit `Content-Type: application/json` header (RestClient defaults to `text/plain` for String bodies)
- Redis not running locally (health check returns 503 — expected)
- Git push to `origin` blocked (port 22/443)

### Verification
1. Go to Dashboard → Gmail Integration card
2. Authorize Gmail (sign in as `makparam1909@gmail.com`)
3. Click Sync Gmail (with a time range)
4. Check Applications page for new cards with 📧 badge
5. Use Debug button on Dashboard to test API queries individually
