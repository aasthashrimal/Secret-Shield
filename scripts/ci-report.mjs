import { readFile } from 'node:fs/promises';

export async function submit(payload, { base, repositoryId, credential, fetcher = fetch, sleep = ms => new Promise(resolve => setTimeout(resolve, ms)) }) {
  if (!base || !/^https:\/\//.test(base) || !/^[1-9]\d*$/.test(repositoryId ?? '') || !credential) {
    throw Error('Trusted reporting configuration is missing or invalid');
  }
  const url = new URL(`/api/ci/repositories/${repositoryId}/scan-results`, base);
  const data = JSON.parse(payload);
  if (!['CLEAN', 'FINDINGS_DETECTED', 'FAILED'].includes(data.status) || !/^[0-9a-f]{40}$/.test(data.commitSha ?? '')) {
    throw Error('Invalid sanitized result');
  }
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const response = await fetcher(url, { method: 'POST', headers: { 'Content-Type': 'application/json',
        'X-Secret-Shield-Credential': credential }, body: payload, signal: AbortSignal.timeout(15_000) });
      if (response.status === 200 || response.status === 201) {
        const accepted = await response.json();
        if (accepted.commitSha !== data.commitSha || accepted.status !== data.status || String(accepted.repositoryId) !== repositoryId) {
          throw Error('Backend acknowledgement did not match scan');
        }
        return accepted;
      }
      if (![429, 500, 502, 503, 504].includes(response.status)) throw Error(`Backend rejected result with HTTP ${response.status}`);
      if (attempt === 3) throw Error(`Backend unavailable after 3 attempts (HTTP ${response.status})`);
    } catch (error) {
      if (attempt === 3 || error.message?.startsWith('Backend rejected') || error.message?.startsWith('Backend acknowledgement')) throw error;
    }
    await sleep(1000 * attempt);
  }
}

if (process.argv[1] && process.argv[1].endsWith('ci-report.mjs')) {
  try {
    const payload = await readFile(process.argv[2], 'utf8');
    await submit(payload, { base: process.env.SECRET_SHIELD_API_BASE,
      repositoryId: process.env.SECRET_SHIELD_REPOSITORY_ID, credential: process.env.SECRET_SHIELD_CI_CREDENTIAL });
    process.stdout.write('Sanitized scan result acknowledged by backend.\n');
  } catch (error) {
    process.stderr.write(`Reporting failed: ${error.message}\n`);
    process.exitCode = 1;
  }
}
