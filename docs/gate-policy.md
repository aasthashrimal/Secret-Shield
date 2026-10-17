# Security gate policy

The backend gate evaluates one authorized repository and the exact 40-character
lowercase commit SHA supplied by the caller. It selects the newest scan for that
pair, ordered by creation time and scan ID. A scan for another commit cannot satisfy
the request. A newer pending, failed, or finding-bearing scan for the same commit
supersedes an older clean scan. The response includes the repository ID, commit SHA,
selected scan ID (or `null`), `outcome`, and `reason`.

| Newest exact-commit scan | Outcome | Reason | Dashboard message |
| --- | --- | --- | --- |
| None | `BLOCK` | `NO_SCAN` | No scan result exists for this commit. Run the scanner. |
| `QUEUED` or `RUNNING` | `BLOCK` | `SCAN_PENDING` | Scanning is still in progress. Deployment is blocked. |
| `FAILED` | `BLOCK` | `SCAN_FAILED` | The scan failed. Fix the scanner and rerun it. |
| `FINDINGS_DETECTED` | `BLOCK` | `FINDINGS_DETECTED` | Secrets were detected. Rotate or revoke exposed credentials and remove them. |
| `CLEAN`, with complete validated metadata and no findings | `ALLOW` | `CLEAN` | This exact commit has a clean scan result. |
| `CLEAN`, but incomplete or malformed | `BLOCK` | `SCAN_FAILED` | The scan result cannot prove safety. Rerun the scanner. |

A clean proof needs the configured scanner version, a start and completion time in
order, no failure code, and zero stored findings. A CI result also needs its run
identity and validated payload digest. Incident `OPEN`, `RESOLVED`, or `IGNORED`
review status has no effect on this table. A later clean scan can allow the same
commit; reviewing or ignoring a finding cannot.

Call the endpoint with an interactive owner or admin JWT:

```powershell
$base = 'http://localhost:8080'
$repositoryId = 'REPLACE_WITH_REPOSITORY_ID'
$commit = '0123456789abcdef0123456789abcdef01234567'
$decision = Invoke-RestMethod -Uri "$base/api/repositories/$repositoryId/gate?commitSha=$commit" -Headers $headers
$decision | Select-Object repositoryId,commitSha,scanId,outcome,reason
```

The backend returns HTTP 200 for both `ALLOW` and `BLOCK`; malformed commit SHAs
return 400. Owners can inspect their repositories, and admins can inspect all.
The deployment workflow must pass the commit SHA it is about to deploy and proceed
only on HTTP 200 with a well-formed `ALLOW` response for that same repository and
commit. A timeout, unavailable API, authentication error, malformed response, or
other reporting outage blocks deployment. The M12 GitHub workflow gates its build
on a clean scan of the checked-out commit. A deployment job will be added in M15;
this endpoint alone cannot stop a deployment outside that workflow. See
[GitHub workflow](github-workflow.md).
