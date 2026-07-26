# Gmail Sync + Ollama Setup Guide

## Overview

The Gmail Sync feature automatically detects job applications from your sent emails. When you send a job application email, the system:

1. Fetches your sent emails via the Gmail API
2. Sends each email to a local Ollama LLM for classification
3. If classified as a job application → creates a Kanban entry (Posting + Application)

---

## Prerequisites

- A **Google Cloud Project** with the Gmail API enabled
- **Ollama** running on a local machine (can be a separate laptop on your network)
- An Ollama model capable of JSON classification (e.g., `llama3.2`, `mistral`, `qwen2.5`)

---

## Step 1: Get Gmail API Credentials

### Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Go to **APIs & Services > Library**
4. Search for **Gmail API** and click **Enable**

### Configure OAuth Consent Screen

1. Go to **APIs & Services > OAuth consent screen**
2. Choose **External** user type
3. Fill in:
   - App name: `Job Search Copilot` (or any name)
   - User support email: your email
   - Developer contact: your email
4. **Scopes**: Add `.../auth/gmail.readonly`
5. **Test users**: Add your email address
6. Skip the "Publishing" step (keep in Testing mode)

### Create OAuth 2.0 Credentials

1. Go to **APIs & Services > Credentials**
2. Click **+ Create Credentials > OAuth client ID**
3. Application type: **Web application**
4. Name: `Job Search Copilot`
5. **Authorized redirect URIs**: Add:
   ```
   http://localhost:8080/api/sync/gmail/callback
   ```
6. Click **Create**
7. Note down the **Client ID** and **Client Secret**

> **Important:** The redirect URI MUST match exactly what's in the app settings.

---

## Step 2: Set Up Ollama

### Install Ollama

```bash
# On the machine that will run Ollama (e.g., a laptop on your local network):
curl -fsSL https://ollama.com/install.sh | sh
```

### Pull a Model

```bash
ollama pull llama3.2
# Or use a smaller model for faster classification:
# ollama pull qwen2.5:3b
# ollama pull mistral
```

### Verify Ollama is Running

```bash
curl http://localhost:11434/api/chat -d '{
  "model": "llama3.2",
  "messages": [{"role": "user", "content": "Say hello"}],
  "stream": false
}'
```

### Find Ollama's IP on Your Network

```bash
# On the Ollama machine:
hostname -I | awk '{print $1}'
# Example output: 192.168.29.24
```

---

## Step 3: Configure the App

### In the Settings UI (recommended)

Navigate to **Settings > AI & Gmail** and fill in:

| Field | Value |
|-------|-------|
| **Ollama Base URL** | `http://192.168.29.24:11434` (replace with your Ollama machine's IP) |
| **Ollama Model** | `llama3.2` (or whatever model you pulled) |
| **Gmail Client ID** | From Google Cloud Console |
| **Gmail Client Secret** | From Google Cloud Console |
| **Gmail Redirect URI** | `http://localhost:8080/api/sync/gmail/callback` |

> The Ollama timeout defaults to 30 seconds. Some models on slower machines may need more time.

### Or Set in application.yml (optional)

```yaml
app:
  ai:
    ollama:
      base-url: http://192.168.29.24:11434
      model: llama3.2
      timeout: 30s
```

---

## Step 4: Authorize Gmail

1. Go to **Dashboard** → **Gmail Integration** card
2. Click **Authorize Gmail**
3. A new tab opens with Google's consent screen
4. Sign in with the Gmail account you use for job applications
5. Click **Continue** (you'll see "Redirect URI mismatch" page — that's expected)
6. **Copy the authorization code** from the URL bar:
   - Look for `?code=4/...` in the address bar
   - Copy the entire code parameter value
7. Paste it into the **Authorization Code** field on the Dashboard
8. Click **Submit**

You should see "Authorization successful!" The refresh token is stored automatically.

---

## Step 5: Run a Sync

1. On the Dashboard, select a time range:
   - **Since last sync** — emails since the last sync (use for subsequent syncs)
   - **Last 1/6/24/72 hours** — for the initial sync
   - **Custom** — specify exact hours
2. Click **Sync Gmail**
3. Wait for results. The button shows a spinner while syncing.

---

## How the Sync Works (Technical)

### Gmail API Call

```
GET /gmail/v1/users/me/messages?labelIds=SENT&maxResults=20&q=after:{epoch_seconds}
```

- `labelIds=SENT` — filters to sent messages (works correctly)
- `after:{epoch}` — only emails sent after the given Unix timestamp
- Pagination: up to 50 emails are fetched per sync

### Email Classification (Ollama)

Each email sends a prompt to Ollama:

```
You are a job application detector. Analyze this email and respond with ONLY valid JSON:
{
  "isJobApplication": true/false,
  "company": "company name or null",
  "jobTitle": "job title or null",
  "confidence": 0.0-1.0
}
```

### Deduplication

- A SHA-256 fingerprint is computed from the email's subject + body
- If the fingerprint already exists in the database, the email is skipped
- This prevents duplicate entries from repeated syncs

### Database Entries

When an application is detected:
1. **Posting** — created with `source=GMAIL_SYNC`, `company`, `jobTitle`
2. **Application** — created with `status=APPLIED`, `method=PREPARED`

Applications from Gmail sync appear on the Kanban board with a 📧 badge.

---

## Troubleshooting

### "0 emails scanned" / Empty results

1. Click **Debug** on the Dashboard Gmail card
2. Verify `labelIds=SENT` shows a non-zero result
3. If all queries with `q=` return 0, it's likely a URL encoding issue — ensure `uriBuilder` is used (not raw string URIs)
4. Check the connected email is correct (should show in "Connected as:")

### "Not authorized" / "Connection failed"

1. Go to Dashboard → Disconnect Gmail
2. Re-authorize with the correct Google account
3. Ensure the account has sent emails

### Ollama not responding

1. Verify Ollama is running: `curl http://{ollama-ip}:11434/api/tags`
2. Check the base URL in Settings (include `http://` prefix)
3. Ensure the model name is correct
4. Try increasing the timeout in Settings

### "No refresh token in response"

1. The Google OAuth consent screen needs `prompt=consent` and `access_type=offline`
2. Revoke access at https://myaccount.google.com/permissions
3. Re-authorize from scratch

### Auth callback page shows error

The callback endpoint (`/api/sync/gmail/callback`) is a public endpoint that returns an HTML page with the authorization code. You must copy the code from the URL and paste it manually into the Dashboard input field.
