import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { normalize } from './ci-normalize-report.mjs';
import { submit } from './ci-report.mjs';

const meta = { sha: 'a'.repeat(40), branch: 'main', runId: '1234', attempt: 1, trigger: 'CI_PUSH' };
const leak = { RuleID: 'generic-api-key', File: 'config/demo.txt', StartLine: 4, Secret: 'never-include-this', Match: 'never-include-this' };
const workflow = readFileSync(new URL('../.github/workflows/security-ci.yml', import.meta.url), 'utf8');

test('pull requests keep reporting, package and cloud credentials behind trusted push conditions', () => {
  assert.match(workflow, /\n  pull_request:\s*\n    branches: \[main\]/);
  assert.doesNotMatch(workflow, /pull_request_target/);
  assert.match(workflow, /permissions:\s*\n  contents: read/);
  assert.match(workflow, /if: github\.event_name == 'push' && github\.ref == 'refs\/heads\/main' && steps\.scanner\.outputs\.status != ''/);
  assert.match(workflow, /if: github\.event_name == 'push' && github\.ref == 'refs\/heads\/main' && github\.repository == 'aasthashrimal\/Secret-Shield'.*vars\.GHCR_PUBLISH_ENABLED == 'true'/);
  assert.match(workflow, /if: github\.event_name == 'push' && github\.ref == 'refs\/heads\/main' && github\.repository == 'aasthashrimal\/Secret-Shield'.*vars\.AZURE_DEPLOY_ENABLED == 'true'/);
  assert.equal(workflow.match(/id-token: write/g)?.length, 1);
  assert.match(workflow, /environment: azure-dev/);
});

test('clean, finding and process failure remain distinct and omit raw values', () => {
  assert.equal(normalize(0, '[]', meta).status, 'CLEAN');
  const finding = normalize(1, JSON.stringify([leak]), meta);
  assert.equal(finding.status, 'FINDINGS_DETECTED');
  assert.equal(finding.findings[0].filePath, 'config/demo.txt');
  assert.equal(normalize(1, JSON.stringify([{ ...leak, File: '/repo/config/demo.txt' }]), meta).findings[0].filePath, 'config/demo.txt');
  assert.ok(!JSON.stringify(finding).includes('never-include-this'));
  assert.deepEqual(normalize(2, '', meta).findings, []);
  assert.equal(normalize(2, '', meta).failureCode, 'SCANNER_PROCESS_ERROR');
  assert.equal(normalize(0, JSON.stringify([leak]), meta).status, 'FAILED');
  assert.equal(normalize(1, 'invalid', meta).failureCode, 'SCANNER_REPORT_INVALID');
  assert.equal(normalize(1, JSON.stringify([{ ...leak, File: '../escape' }]), meta).status, 'FAILED');
});

test('reporting retries transient errors with one identical payload and accepts replay', async () => {
  const payload = JSON.stringify(normalize(1, JSON.stringify([leak]), meta));
  const bodies = [];
  const response = await submit(payload, { base: 'https://example.invalid', repositoryId: '7', credential: 'fixture', sleep: async () => {},
    fetcher: async (_url, options) => { bodies.push(options.body); return bodies.length === 1 ? { status: 503 } :
      { status: 200, json: async () => ({ commitSha: meta.sha, status: 'FINDINGS_DETECTED', repositoryId: '7' }) }; } });
  assert.equal(response.status, 'FINDINGS_DETECTED');
  assert.deepEqual(bodies, [payload, payload]);
  let calls = 0;
  await assert.rejects(submit(payload, { base: 'https://example.invalid', repositoryId: '7', credential: '',
    fetcher: async () => { calls++; return { status: 201 }; } }), /configuration/);
  assert.equal(calls, 0);
});

test('reporting rejects permanent errors and mismatched acknowledgements without retrying', async () => {
  const payload = JSON.stringify(normalize(0, '[]', meta));
  let calls = 0;
  const options = { base: 'https://example.invalid', repositoryId: '7', credential: 'fixture', sleep: async () => {} };
  await assert.rejects(submit(payload, { ...options, fetcher: async () => { calls++; return { status: 401 }; } }), /HTTP 401/);
  assert.equal(calls, 1);
  calls = 0;
  await assert.rejects(submit(payload, { ...options, fetcher: async () => {
    calls++;
    return { status: 201, json: async () => ({ commitSha: 'b'.repeat(40), status: 'CLEAN', repositoryId: '7' }) };
  } }), /acknowledgement did not match/);
  assert.equal(calls, 1);
});

test('reporting bounds repeated network failures and never sends invalid results', async () => {
  const options = { base: 'https://example.invalid', repositoryId: '7', credential: 'fixture', sleep: async () => {} };
  let calls = 0;
  await assert.rejects(submit(JSON.stringify(normalize(0, '[]', meta)), { ...options, fetcher: async () => {
    calls++;
    throw Error('synthetic network failure');
  } }), /synthetic network failure/);
  assert.equal(calls, 3);
  calls = 0;
  await assert.rejects(submit(JSON.stringify({ status: 'CLEAN', commitSha: 'bad' }), {
    ...options, fetcher: async () => { calls++; return { status: 201 }; }
  }), /Invalid sanitized result/);
  assert.equal(calls, 0);
});
