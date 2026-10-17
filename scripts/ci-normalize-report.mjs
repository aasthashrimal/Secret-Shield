import { readFile, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const sha = process.env.SCAN_SHA;
const branch = process.env.SCAN_BRANCH;
const runId = process.env.GITHUB_RUN_ID;
const attempt = Number(process.env.GITHUB_RUN_ATTEMPT);
const trigger = process.env.SCAN_TRIGGER;
const version = '8.30.0';

function safeFinding(item) {
  const ruleId = item?.RuleID;
  let filePath = item?.File;
  const line = item?.StartLine;
  if (typeof ruleId !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$/.test(ruleId)) throw Error('Invalid rule ID');
  if (typeof filePath === 'string' && filePath.startsWith('/repo/')) filePath = filePath.slice('/repo/'.length);
  if (typeof filePath !== 'string' || filePath.length > 1000 || filePath.startsWith('/') || filePath.includes('\\') ||
      filePath.split('/').some(part => !part || part === '.' || part === '..') || /[\x00-\x1f\x7f]/.test(filePath)) throw Error('Invalid path');
  if (!Number.isInteger(line) || line < 1) throw Error('Invalid line');
  return { ruleId, filePath, line, sanitizedDescription: `Potential secret detected by rule ${ruleId}` };
}

export function normalize(code, raw, meta) {
  if (!/^[0-9a-f]{40}$/.test(meta.sha ?? '') || !/^[A-Za-z0-9._:-]{1,200}$/.test(meta.runId ?? '') ||
      !Number.isInteger(meta.attempt) || meta.attempt < 1 || !/^[A-Za-z0-9._/\-]{1,200}$/.test(meta.branch ?? '') ||
      !['CI_PUSH', 'CI_PR'].includes(meta.trigger)) throw Error('Invalid run identity');
  let status = 'FAILED';
  let failureCode = 'SCANNER_PROCESS_ERROR';
  let findings = [];
  if (code === 0 || code === 1) {
    try {
      const parsed = raw ? JSON.parse(raw) : [];
      if (!Array.isArray(parsed) || parsed.length > 1000) throw Error('Invalid report');
      findings = parsed.map(safeFinding);
      if ((code === 0 && findings.length === 0) || (code === 1 && findings.length > 0)) {
        status = code === 0 ? 'CLEAN' : 'FINDINGS_DETECTED';
        failureCode = null;
      } else { findings = []; }
    } catch { findings = []; failureCode = 'SCANNER_REPORT_INVALID'; }
  }
  return { runId: meta.runId, runAttempt: meta.attempt, commitSha: meta.sha, branch: meta.branch,
    trigger: meta.trigger, scannerVersion: version, status, failureCode, findings };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [exitCode, reportPath, outputPath] = process.argv.slice(2);
  if (!/^\d+$/.test(exitCode ?? '') || !reportPath || !outputPath) throw Error('Usage: node ci-normalize-report.mjs EXIT REPORT OUTPUT');
  let raw = '';
  try {
    if ((await stat(reportPath)).size > 750_000) throw Error('Report too large');
    const data = await readFile(reportPath);
    raw = data.toString('utf8');
  } catch (error) {
    if (error.code !== 'ENOENT') raw = 'invalid';
  }
  const result = normalize(Number(exitCode), raw, { sha, branch, runId, attempt, trigger });
  const payload = JSON.stringify(result);
  if (Buffer.byteLength(payload) > 1_000_000) throw Error('Sanitized payload too large');
  await writeFile(outputPath, payload, { mode: 0o600 });
  process.stdout.write(`${result.status}\n`);
}
