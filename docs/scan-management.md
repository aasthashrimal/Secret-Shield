# Scan management and CI ingestion

M05 records manual scans without executing arbitrary code. An owner or administrator
creates `QUEUED` metadata with a lowercase 40-character commit SHA and branch, then
lists or reads scans under the authorized repository. M06 adds controlled execution.

CI uses a separate repository-scoped credential, never a user JWT. Issue one with
`POST /api/repositories/{id}/ci-credentials`; save the returned `credential` because
it is shown once. The database stores BCrypt only. Rotate by issuing a replacement
and revoking the old ID. Optional `expiresAt` is a future UTC instant.

Submit sanitized JSON to `POST /api/ci/repositories/{id}/scan-results` using header
`X-Secret-Shield-Credential`. Payloads are limited to 1 MiB and 1000 findings.
Identity is repository + runId + runAttempt + commitSha. An identical retry returns
200 and the original scan; a changed payload for that identity returns 409.

```json
{"runId":"demo-run-1","runAttempt":1,"commitSha":"0123456789abcdef0123456789abcdef01234567","branch":"main","trigger":"CI_PUSH","scannerVersion":"8.0.0","status":"FINDINGS_DETECTED","failureCode":null,"findings":[{"ruleId":"synthetic-low","filePath":"config/demo.txt","line":2,"sanitizedDescription":"Synthetic metadata only"}]}
```

`CLEAN` requires no findings/failure; `FINDINGS_DETECTED` requires findings;
`FAILED` requires an uppercase safe failure code and no findings. Paths must be
relative with `/` separators and no `..`. Raw reports, snippets, secret values,
tokens, and unknown fields are rejected. M05 validates findings and includes them
in the replay digest; M07 implements their database persistence through
`FindingResultSink` in the same transaction.
