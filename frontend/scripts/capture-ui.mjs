import { spawn } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const workspace = path.resolve(frontend, '..');
const output = path.join(workspace, 'docs', 'evidence');
const edge = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const base = 'http://localhost:5173';
const now = '2026-10-17T08:00:00Z';
const sha = '0123456789abcdef0123456789abcdef01234567';
const repository = { id: '7', ownerId: '2', githubOwner: 'demo', githubName: 'service', canonicalUrl: 'https://github.com/demo/service', defaultBranch: 'main', active: true, createdAt: now, updatedAt: now };
const scan = { id: '21', repositoryId: '7', commitSha: sha, branch: 'main', trigger: 'MANUAL', runId: null, runAttempt: null, scannerVersion: '8.30.0', status: 'FINDINGS_DETECTED', failureCode: null, startedAt: now, completedAt: now, createdAt: now, updatedAt: now };
const incident = { id: '35', repositoryId: '7', fingerprint: 'a'.repeat(64), severity: 'HIGH', status: 'OPEN', reviewReason: null, lastActorType: 'SYSTEM', lastActorReference: null, version: 0, createdAt: now, updatedAt: now };
const finding = { id: '31', scanId: '21', incidentId: '35', fingerprint: incident.fingerprint, ruleId: 'generic-api-key', severity: 'HIGH', filePath: 'config/example.txt', line: 4, commitSha: sha, sanitizedDescription: 'Potential secret detected by rule generic-api-key', createdAt: now };
const event = { id: '41', repositoryId: '7', actorType: 'SYSTEM', actorUserId: null, actorCredentialId: null, operation: 'INCIDENT_CREATED', targetType: 'INCIDENT', targetId: '35', safeChangeSummary: 'Incident opened from a scan finding', occurredAt: now };
const overview = { repositories: 1, activeRepositories: 1, scans: 2, findings: 1, incidents: 1, scanOutcomes: { QUEUED: 0, RUNNING: 0, CLEAN: 1, FINDINGS_DETECTED: 1, FAILED: 0 }, findingsBySeverity: { CRITICAL: 0, HIGH: 1, MEDIUM: 0, LOW: 0, INFO: 0 }, incidentsByStatus: { OPEN: 1, RESOLVED: 0, IGNORED: 0 }, failedNotifications: 0, recentActivity: [{ id: '41', repositoryId: '7', operation: event.operation, actorType: event.actorType, safeChangeSummary: event.safeChangeSummary, occurredAt: now }], repositorySecurity: [{ repositoryId: '7', githubOwner: 'demo', githubName: 'service', active: true, latestScanId: '21', latestCommitSha: sha, latestScanStatus: 'FINDINGS_DETECTED', gateOutcome: 'BLOCK', gateReason: 'FINDINGS_DETECTED' }] };
const pageResult = content => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0 });

async function waitForServer() {
  for (let attempt = 0; attempt < 40; attempt++) {
    try { const response = await fetch(base); if (response.ok) return; } catch { /* Starting. */ }
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error('Vite server did not start');
}

async function main() {
  await mkdir(output, { recursive: true });
  const server = spawn(process.execPath, [path.join(frontend, 'node_modules', 'vite', 'bin', 'vite.js'), '--host', 'localhost', '--port', '5173', '--strictPort'], { cwd: frontend, windowsHide: true, stdio: 'ignore' });
  let browser;
  try {
    await waitForServer();
    browser = await chromium.launch({ executablePath: edge, headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();
    const consoleErrors = [];
    page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()); });
    page.on('pageerror', error => consoleErrors.push(error.message));
    await page.route('**/api/**', route => {
      const url = new URL(route.request().url());
      const path = url.pathname;
      let body;
      if (path === '/api/auth/login') body = { token: 'synthetic-browser-test-token', username: 'demo', email: 'demo@example.invalid' };
      else if (path === '/api/auth/me') body = { id: '2', username: 'demo', email: 'demo@example.invalid', role: 'DEVELOPER' };
      else if (path === '/api/dashboard/overview') body = overview;
      else if (path === '/api/repositories') body = pageResult([repository]);
      else if (path === '/api/repositories/7') body = repository;
      else if (path === '/api/repositories/7/scans') body = pageResult([scan]);
      else if (path === '/api/repositories/7/incidents') body = pageResult([incident]);
      else if (path === '/api/repositories/7/audit-events') body = pageResult([event]);
      else if (path === '/api/repositories/7/scans/21') body = scan;
      else if (path === '/api/repositories/7/scans/21/findings') body = pageResult([finding]);
      else if (path === '/api/repositories/7/incidents/35') body = incident;
      else if (path === '/api/repositories/7/incidents/35/notifications') body = pageResult([{ id: '51', incidentId: '35', auditEventId: '41', recipientConfigReference: 'security', status: 'SENT', attemptCount: 1, safeErrorCode: null, createdAt: now, updatedAt: now }]);
      else throw new Error(`Unmocked API path: ${path}`);
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    });
    await page.goto(`${base}/login`);
    await page.screenshot({ path: path.join(output, 'm11-login.png'), fullPage: true });
    await page.getByLabel('Email').fill('demo@example.invalid');
    await page.getByLabel('Password').fill('synthetic-password');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await page.getByRole('heading', { name: 'Overview' }).waitFor();
    await page.getByText('demo/service').waitFor();
    await page.screenshot({ path: path.join(output, 'm11-overview.png'), fullPage: true });
    await page.getByRole('link', { name: 'Repositories', exact: true }).click();
    await page.getByRole('link', { name: 'demo/service' }).waitFor();
    await page.getByRole('link', { name: 'demo/service' }).click();
    await page.getByRole('heading', { name: 'Scans' }).waitFor();
    await page.screenshot({ path: path.join(output, 'm11-repository.png'), fullPage: true });
    await page.getByRole('link', { name: sha.slice(0, 12) }).click();
    await page.getByText('config/example.txt:4').waitFor();
    await page.screenshot({ path: path.join(output, 'm11-scan.png'), fullPage: true });
    await page.getByRole('link', { name: '#35' }).click();
    await page.getByLabel('Reason for this change').waitFor();
    await page.screenshot({ path: path.join(output, 'm11-incident.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole('link', { name: 'Overview' }).click();
    await page.getByText('demo/service').waitFor();
    if (await page.evaluate(() => document.body.scrollWidth > window.innerWidth)) throw new Error('Mobile page has horizontal body overflow');
    await page.screenshot({ path: path.join(output, 'm11-mobile.png'), fullPage: true });
    if (consoleErrors.length) throw new Error(`Browser console errors: ${consoleErrors.join(' | ')}`);
    if (await page.evaluate(() => localStorage.length || sessionStorage.length)) throw new Error('Session was persisted in browser storage');
    console.log('Synthetic desktop and mobile browser review passed; screenshots saved under docs/evidence.');
  } finally {
    await browser?.close();
    server.kill();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
