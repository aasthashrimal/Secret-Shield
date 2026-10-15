# Finding presentation contract

M07 returns one immutable, sanitized occurrence per scan and fingerprint. UI code
must display only the fields in `FindingDto`; it must never attempt to reconstruct,
request, or show a raw matched secret or source snippet.

## Severity badges

| Severity | Badge label | Suggested accessible meaning |
| --- | --- | --- |
| `CRITICAL` | Critical | Highest-priority credential exposure |
| `HIGH` | High | High-priority credential exposure |
| `MEDIUM` | Medium | Medium-priority credential exposure |
| `LOW` | Low | Lower-priority finding; still blocks deployment |
| `INFO` | Info | Informational finding; still blocks deployment |

Severity is assigned by Secret Shield's documented rule mapping. Gitleaks does not
provide this project's severity policy. Unknown rule IDs use `HIGH`.

## Findings table

Use columns `Severity`, `Rule`, `File`, `Line`, `Commit`, and `Detected`. The detail
view may also show the fingerprint and the fixed sanitized description. An absent
`incidentId` means incident linkage has not been created yet; M08 supplies that workflow.

Safe synthetic fixture:

```json
{
  "id": "301",
  "scanId": "201",
  "incidentId": null,
  "fingerprint": "1a20b5fc9d5e2a9da4a536ff39e12af2910231fed3b7d54978308f3b9a18b01a",
  "ruleId": "synthetic-low",
  "severity": "LOW",
  "filePath": "config/demo.txt",
  "line": 2,
  "commitSha": "0123456789abcdef0123456789abcdef01234567",
  "sanitizedDescription": "Potential secret detected by rule synthetic-low",
  "createdAt": "2026-10-15T08:30:00Z"
}
```

For an empty page, show `No findings were detected for this scan.` For a failed
request, show the API's fixed error message and a retry action. Do not equate an
empty finding page with a clean scan unless the scan status itself is `CLEAN`.
