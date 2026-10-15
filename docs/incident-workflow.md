# Incident review and audit history

Each new finding fingerprint opens one incident for its repository. Later scans at
the same rule, path, and line link their findings to that incident. An identical CI
result replay adds no finding, incident, or audit event. A new occurrence reopens a
resolved incident automatically; an ignored incident stays ignored and receives the
new occurrence. Incident review never changes a scan's `FINDINGS_DETECTED` result or
unblocks deployment.

The repository owner and an administrator can mark an open incident `RESOLVED`.
Only an administrator can mark it `IGNORED`. Either can reopen a resolved or ignored
incident. Every review request needs a nonblank reason (up to 1000 characters) and
the `version` from the latest incident response. A stale version or an unsupported
transition returns HTTP 409. Keep reasons free of credential values and source text.
The current manual review reason stays on the incident, including when a reviewer
reopens it; automatic recurrence clears the old resolution reason. The app records
the reviewer's user ID and update time. Resolution is a human
attestation; Secret Shield does not verify an external provider's rotation or
revocation. Before resolving a leaked credential, revoke or rotate it at its
provider, remove it from the repository, and scan again. Ignore is for accepted
review exceptions; it does not alter scan outcomes.

After logging in and storing the JWT in `$headers`, use a repository ID and an
incident ID from the first response:

```powershell
$base = 'http://localhost:8080'
$repositoryId = 'REPLACE_WITH_REPOSITORY_ID'
$incidents = Invoke-RestMethod -Uri "$base/api/repositories/$repositoryId/incidents?status=OPEN" -Headers $headers
$incident = $incidents.content[0]
$incident
```

Resolve after external remediation:

```powershell
$body = @{ status='RESOLVED'; reason='Demo credential rotated and fixture removed'; version=$incident.version } | ConvertTo-Json
$reviewed = Invoke-RestMethod -Method Patch -Uri "$base/api/repositories/$repositoryId/incidents/$($incident.id)" -Headers $headers -ContentType 'application/json' -Body $body
$reviewed | Select-Object id,status,version,updatedAt
```

To inspect recurrence, submit a new CI result for the same rule, relative path,
and line with a new run attempt. The incident becomes `OPEN`, its version increases,
and the new finding returns the same `incidentId`. Replaying the original attempt
leaves all counts unchanged. An administrator can test `IGNORED` with the current
version and a reason; a developer should receive 403 for that action.

```powershell
$history = Invoke-RestMethod -Uri "$base/api/repositories/$repositoryId/audit-events?size=100" -Headers $headers
$history.content | Select-Object occurredAt,actorType,operation,targetType,targetId,safeChangeSummary
```

Both lists support zero-based `page`, `size` 1–100, `from` inclusive and `to`
exclusive UTC timestamps. Incident filters are `status` and `severity`; audit
filters are `operation` and `actorType`. Repository owners and administrators can
read the history. Audit events are append-only and have no mutation API. Their
summaries contain only fixed, safe descriptions and status names.
