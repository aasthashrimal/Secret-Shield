# High-risk notification checks

M10 alerts for `HIGH` and `CRITICAL` finding occurrences. `MEDIUM`, `LOW`, and
`INFO` do not queue alerts. An identical CI result replay does not create another
alert; a new scan occurrence of the same finding gets a new audit event and alert.
Incident review does not change the gate decision. If a credential leaked, rotate
or revoke it at its provider, remove it from source, and run a new scan.

## Configuration

Notifications default to disabled. In your private `.env`, set:

```dotenv
NOTIFICATION_PROVIDER=fake
NOTIFICATION_RECIPIENTS=security=your-approved-address@example.com
NOTIFICATION_PUBLIC_BASE_URL=http://localhost:8080
```

Load `.env` into the backend process environment as described in
[local setup](local-setup.md), then start the app. Recipient aliases are lowercase
letters, digits, `_`, or `-`, up to 32 characters. The alias is stored in delivery
history; its private email address is not. Separate multiple entries with commas,
for example `security=address1@example.com,oncall=address2@example.com`. Do not
commit populated `.env`, tokens, real addresses, or Azure connection strings.

For Azure mode, configure an Azure Communication Services Email resource and
verified sender/domain, then set `NOTIFICATION_PROVIDER=azure`, an HTTPS
`NOTIFICATION_PUBLIC_BASE_URL`, `ACS_CONNECTION_STRING`, and
`ACS_SENDER_ADDRESS` in the private process environment. The Java provider uses
the [ACS Email SDK send operation](https://learn.microsoft.com/en-us/java/api/overview/azure/communication-email-readme?view=azure-java-stable).
`SENT` records that the SDK operation succeeded. Actual inbox delivery needs
separate [delivery-report evidence](https://learn.microsoft.com/en-us/azure/communication-services/concepts/email/email-event-monitoring).
Do this live validation after M14; local tests do not contact Azure.

## View a local delivery

Use an approved synthetic HIGH/CRITICAL fixture and submit it through the scan
API, following [scanning guide](scanning-guide.md). With `fake` mode, the background
poller starts after 10 seconds and checks every 10 seconds. It sends only after the
scan transaction commits. Use your existing login JWT in a shell variable:

```powershell
$base = 'http://localhost:8080'
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" `
  -ContentType 'application/json' `
  -Body (@{ email='your-account@example.com'; password='your-password' } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.token)" }
$incidentId = '<incident ID from GET /api/repositories/<repository ID>/incidents>'
$repositoryId = '<your repository ID>'
Invoke-RestMethod -Uri "$base/api/repositories/$repositoryId/incidents/$incidentId/notifications" `
  -Headers $headers
```

The response has `QUEUED`, `SENDING`, `SENT`, or `FAILED`, `attemptCount`, an
optional fixed `safeErrorCode`, the recipient alias, and audit-event ID. You can
filter with `?status=FAILED&page=0&size=20`. A failed attempt retries after 30
seconds, then 120 seconds; there are at most three attempts. A permanently failed
delivery stays visible and does not erase findings or change the security gate.
The fake provider is intentionally local and does not send an email. Do not treat
its `SENT` result as Azure or inbox-delivery evidence.

## Evidence for the report

The local disposable PostgreSQL run on 15 October 2026 observed two queued
deliveries for one HIGH and one CRITICAL finding, and none for a LOW finding.
Both became `SENT` through the fake provider; an identical CI replay added no
record, while a later HIGH occurrence added one. A simulated provider failure
produced `FAILED` with `SIMULATED_FAILURE`, then `SENT` on attempt 3 after two
retries. A second delivery remained `FAILED` after attempt 3. The gate stayed
`BLOCK` with `FINDINGS_DETECTED` throughout. These are local synthetic checks,
not Azure delivery evidence.

Record the scan/incident IDs, severity, provider mode, alias, audit-event ID,
status, attempt count, and a safe error code if present. Capture the blocked gate
decision separately. Redact JWTs, CI credentials, recipient addresses, connection
strings, source secrets, and raw scanner reports. The automated PostgreSQL suite
checks the threshold, replay deduplication, post-commit sending, safe message
content, owner/admin access, bounded retries, and gate independence. A live ACS
success and a live failure/retry case remain for M10.4 after M14.
