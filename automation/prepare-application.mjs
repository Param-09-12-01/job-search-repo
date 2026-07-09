#!/usr/bin/env node
/**
 * Prepare-Application helper for Job Search Copilot.
 *
 * Reads a JSON payload file (path passed as argv[2]) describing a posting URL and the profile
 * fields to fill, launches a browser via Playwright using the user's own browser profile, navigates
 * to the application page, and fills known fields by best-effort selector matching.
 *
 * SAFETY CONTRACT (do not remove):
 *   - This script NEVER clicks an Apply / Submit / Send button.
 *   - This script NEVER logs in and NEVER enters credentials.
 *   - This script NEVER bypasses authentication or CAPTCHAs.
 *   - It fills fields and STOPS, leaving the browser open for the user to review and submit.
 *
 * Usage: node prepare-application.mjs <payload.json>
 */

import { readFileSync } from 'node:fs';
import { chromium } from 'playwright';

/** Words that must never be clicked. Guards the fill-only contract. */
const FORBIDDEN_CLICK_WORDS = ['apply', 'submit', 'send', 'login', 'sign in', 'log in'];

function log(msg) {
  process.stdout.write(`[prepare-application] ${msg}\n`);
}

async function main() {
  const payloadPath = process.argv[2];
  if (!payloadPath) {
    log('ERROR: no payload file provided');
    process.exit(2);
  }

  const payload = JSON.parse(readFileSync(payloadPath, 'utf-8'));
  const { url, fields, headless = false, browserExecutablePath, browserProfilePath } = payload;

  if (!url) {
    log('ERROR: payload missing url');
    process.exit(2);
  }

  log(`Launching browser (headless=${headless}) for ${url}`);

  // Use a persistent context so the user's existing browser profile/session is reused.
  // We never create or submit login forms ourselves.
  const launchOptions = { headless };
  if (browserExecutablePath) {
    launchOptions.executablePath = browserExecutablePath;
  }

  const context = browserProfilePath
    ? await chromium.launchPersistentContext(browserProfilePath, launchOptions)
    : await (await chromium.launch(launchOptions)).newContext();

  const page = context.pages().length ? context.pages()[0] : await context.newPage();

  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });
    log('Page loaded. Filling fields (fill-only, no submit).');

    await fillField(page, fields.fullName, [
      'input[name*="name" i]', 'input[id*="name" i]',
      'input[autocomplete="name"]', 'input[placeholder*="name" i]',
    ]);
    await fillField(page, fields.email, [
      'input[type="email"]', 'input[name*="email" i]',
      'input[id*="email" i]', 'input[placeholder*="email" i]',
    ]);
    await fillField(page, fields.phone, [
      'input[type="tel"]', 'input[name*="phone" i]',
      'input[id*="phone" i]', 'input[placeholder*="phone" i]',
    ]);
    await fillField(page, fields.linkedIn, [
      'input[name*="linkedin" i]', 'input[id*="linkedin" i]',
      'input[placeholder*="linkedin" i]',
    ]);
    await fillField(page, fields.github, [
      'input[name*="github" i]', 'input[id*="github" i]',
      'input[placeholder*="github" i]',
    ]);
    await fillField(page, fields.portfolio, [
      'input[name*="portfolio" i]', 'input[name*="website" i]',
      'input[id*="portfolio" i]', 'input[placeholder*="portfolio" i]',
    ]);

    if (fields.resumePath) {
      await attachResume(page, fields.resumePath);
    }

    log('DONE: fields filled. Browser left open for manual review and submission.');
    log('SAFETY: no Apply/Submit button was clicked; no login was performed.');
    // Intentionally do NOT close the context — the user finishes manually.
  } catch (err) {
    log(`ERROR during fill: ${err.message}`);
    process.exitCode = 1;
  }
}

async function fillField(page, value, selectors) {
  if (!value) return;
  for (const selector of selectors) {
    try {
      const locator = page.locator(selector).first();
      if (await locator.count() === 0) continue;
      // Guard: never type into a password field.
      const type = await locator.getAttribute('type');
      if (type === 'password') continue;
      if (await locator.isVisible()) {
        await locator.fill(String(value), { timeout: 3000 });
        log(`Filled ${selector}`);
        return;
      }
    } catch {
      // Try the next selector silently.
    }
  }
}

async function attachResume(page, resumePath) {
  const fileSelectors = ['input[type="file"]'];
  for (const selector of fileSelectors) {
    try {
      const locator = page.locator(selector).first();
      if (await locator.count() === 0) continue;
      await locator.setInputFiles(resumePath, { timeout: 5000 });
      log('Attached resume file');
      return;
    } catch (err) {
      log(`Could not attach resume: ${err.message}`);
    }
  }
}

/**
 * Assertion guard used by tests: verify a label is not a forbidden click target.
 * Exported for unit testing of the safety contract.
 */
export function isForbiddenClickTarget(label) {
  if (!label) return false;
  const lower = label.toLowerCase();
  return FORBIDDEN_CLICK_WORDS.some((w) => lower.includes(w));
}

main();
