import { spawn } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.resolve(frontend, '..', 'docs', 'evidence');
const edge = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const api = process.env.M11_API_BASE;
const email = 'm11-smoke@example.invalid';
const password = 'Synthetic-dashboard-smoke-password-2026';
const sha = '0123456789abcdef0123456789abcdef01234567';

async function jsonRequest(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) throw new Error(`Live API returned HTTP ${response.status} for ${new URL(url).pathname}`);
  return response.status === 204 ? null : response.json();
}

async function main() {
  if (!api?.startsWith('http://127.0.0.1:')) throw new Error('M11_API_BASE must point to the disposable loopback backend');
  const port = 5173; // Matches the backend's configured exact frontend origin.
  const base = `http://localhost:${port}`;
  await mkdir(output, { recursive: true });
  const server = spawn(process.execPath, [path.join(frontend, 'node_modules', 'vite', 'bin', 'vite.js'), '--host', 'localhost', '--port', String(port), '--strictPort'], { cwd: frontend, env: process.env, windowsHide: true, stdio: 'ignore' });
  let browser;
  try {
    let ready = false;
    for (let attempt = 0; attempt < 40; attempt++) {
      if (server.exitCode !== null) break;
      try { if ((await fetch(base)).ok) { ready = true; break; } } catch { /* Starting. */ }
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    if (!ready) throw new Error('Vite did not become ready');
    browser = await chromium.launch({ executablePath: edge, headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();
    const errors = [];
    const failedResponses = [];
    page.on('console', message => { if (message.type() === 'error') errors.push(`${message.text()} @ ${message.location().url}`); });
    page.on('pageerror', error => errors.push(error.message));
    page.on('response', response => {
      if (response.status() >= 400) {
        const url = new URL(response.url());
        failedResponses.push(`${response.status()} ${url.origin}${url.pathname}`);
      }
    });
    await page.goto(`${base}/register`);
    await page.getByLabel('Username').fill('m11-smoke');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(password);
    await page.getByRole('button', { name: 'Create account' }).click();
    await page.getByRole('heading', { name: 'Overview' }).waitFor();
    await page.getByRole('link', { name: 'Repositories', exact: true }).click();
    await page.getByRole('button', { name: 'Add repository' }).click();
    await page.getByLabel('GitHub owner').fill('fixture-owner');
    await page.getByLabel('Repository name').fill('dashboard-demo');
    await page.locator('form').getByRole('button', { name: 'Add repository' }).click();
    await page.getByRole('link', { name: 'fixture-owner/dashboard-demo' }).waitFor();

    const login = await jsonRequest(`${api}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) });
    const headers = { Authorization: `Bearer ${login.token}`, 'Content-Type': 'application/json' };
    const listed = await jsonRequest(`${api}/api/repositories`, { headers });
    const repositoryId = listed.content.find(item => item.githubOwner === 'fixture-owner').id;
    const issued = await jsonRequest(`${api}/api/repositories/${repositoryId}/ci-credentials`, { method: 'POST', headers, body: '{}' });
    await jsonRequest(`${api}/api/ci/repositories/${repositoryId}/scan-results`, {
      method: 'POST', headers: { 'X-Secret-Shield-Credential': issued.credential, 'Content-Type': 'application/json' },
      body: JSON.stringify({ runId: 'm11-synthetic-run', runAttempt: 1, commitSha: sha, branch: 'main', trigger: 'CI_PUSH', scannerVersion: '8.30.0', status: 'FINDINGS_DETECTED', findings: [{ ruleId: 'generic-api-key', filePath: 'config/demo.txt', line: 4, sanitizedDescription: 'Synthetic fixture metadata' }] }),
    });
    await page.getByRole('link', { name: 'Overview' }).click();
    await page.getByRole('link', { name: 'fixture-owner/dashboard-demo' }).waitFor();
    await page.screenshot({ path: path.join(output, 'm11-live-overview.png'), fullPage: true });
    await page.getByRole('link', { name: 'fixture-owner/dashboard-demo' }).click();
    await page.getByRole('link', { name: sha.slice(0, 12) }).click();
    await page.getByText('config/demo.txt:4').waitFor();
    const incidentLink = page.locator('table').getByRole('link', { name: /#\d+/ });
    await incidentLink.click();
    await page.getByLabel('Reason for this change').fill('Synthetic fixture removed and credential rotated.');
    await page.getByRole('button', { name: 'Resolve' }).click();
    await page.getByText('Synthetic fixture removed and credential rotated.').waitFor();
    await page.screenshot({ path: path.join(output, 'm11-live-incident.png'), fullPage: true });
    const decision = await jsonRequest(`${api}/api/repositories/${repositoryId}/gate?commitSha=${sha}`, { headers });
    if (decision.outcome !== 'BLOCK' || decision.reason !== 'FINDINGS_DETECTED') throw new Error('Incident review changed the gate');
    if (errors.some(value => value.includes(login.token) || value.includes(issued.credential))) throw new Error('Browser console contained a token');
    if (errors.length) {
      const safeErrors = errors.map(value => value.replaceAll(login.token, '[token]')
        .replaceAll(issued.credential, '[credential]'));
      throw new Error(`Browser console errors: ${safeErrors.join(' | ')}; failed responses: ${failedResponses.join(' | ')}`);
    }
    if (await page.evaluate(() => localStorage.length || sessionStorage.length)) throw new Error('Browser session storage is not empty');
    console.log('Live disposable scan-to-incident browser flow passed; sanitized screenshots saved.');
  } finally {
    await browser?.close();
    server.kill();
  }
}

main().catch(error => { console.log(error.stack ?? String(error)); process.exitCode = 1; });
