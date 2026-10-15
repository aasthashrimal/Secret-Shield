# User roles and authorization

Module 03 defines two controlled roles: `DEVELOPER` and `ADMIN`. The application
stores them as a Java enum and PostgreSQL V4 restricts the `users.role` column to
those exact uppercase values. Unknown historical values stop migration for explicit
review. Public registration always creates a `DEVELOPER`; a submitted `role` field
is ignored and cannot grant administrator access.

## Access matrix

| Capability | Developer | Administrator |
| --- | --- | --- |
| Register, log in and read own `/api/auth/me` | Yes | Yes |
| Read public liveness | Yes | Yes |
| Manage a repository owned by that user | Yes | Yes |
| Read or mutate another user's repository resources | No, JSON 403 | Yes |
| Use a nested scan/finding/incident under the wrong repository URL | No, JSON 404 after parent access is verified | No, JSON 404 |
| Inspect a user through `GET /api/admin/users/{id}` | No, JSON 403 | Yes, safe four-field summary only |
| Ignore an incident | No | Yes |
| Issue/revoke repository CI credentials | Owned repositories only | Any authorized repository |
| Change repository ownership | No | No; ownership is immutable in the current contract |
| Assign a role through public APIs | No | No; bootstrap is an operational procedure |

Authentication is checked before resource existence: an anonymous protected request
gets 401. For a known parent resource, an authenticated developer accessing another
owner's data gets 403. Once access to the route repository is established, a child
whose repository ID does not match the route gets 404. This prevents nested-resource
mix-ups without exposing relationships through an unauthorized parent.

`ResourceAuthorization` centralizes `requireAdmin`, owner-or-admin, and nested parent
checks. M04 repository services call it after loading their route parent; later services
must not rely on hiding buttons. The `/api/admin/**` URL rule is a first defense and
the service checks the role again. JWT role claims are informational: every request
loads the current user and authority from PostgreSQL, so database role changes apply
without trusting a client-controlled claim.

## Administrator bootstrap

Bootstrap promotes an **existing registered user**. It cannot create a user, choose
an arbitrary role, or run unless explicitly enabled by the dedicated script. It is
idempotent and logs no email. Database credentials continue to come from private `.env`.

1. Register the intended administrator normally, so the account starts as DEVELOPER.
2. Stop the regular backend to make the operational action obvious.
3. From the repository root run:

   ```powershell
   powershell -NoProfile -File .\scripts\bootstrap-admin.ps1 -Email 'admin@example.invalid'
   ```

4. Expect `BUILD SUCCESS` and the fixed bootstrap-completed log. An unknown email
   fails startup without printing that email in the application error.
5. Start normally with `scripts/start-local.ps1`, log in again, and call the admin
   lookup endpoint to verify the role. The script restores temporary process settings;
   no bootstrap flag belongs in `.env` or source control.

For cached Maven dependencies, add `-Offline`. Anyone with the shared database owner
credential can alter data directly, including roles, so collaboration should use
individual Neon access and database roles when practical. Rotate a shared password
when a collaborator no longer needs access. There is no public promotion/demotion API.

## Safe admin endpoint

`GET /api/admin/users/{id}` returns exactly:

```json
{"id":"7","username":"synthetic-user","email":"user@example.invalid","role":"DEVELOPER"}
```

It never returns password hashes, JWTs or timestamps. Anonymous callers get 401,
developers get 403, administrators get 404 for an unknown positive ID, and a known
user yields 200. This endpoint provides a narrow, testable admin boundary without
expanding the administrator API beyond the implemented repository management boundary.

## Dashboard labels for later frontend work

| Role | Navigation label | Short description | Badge |
| --- | --- | --- | --- |
| DEVELOPER | `My repositories` | `Manage repositories, scans and incidents you own.` | `Developer` |
| ADMIN | `All repositories` | `Review and manage repository security across the project.` | `Administrator` |

Do not label developers as “standard” or imply weaker scan protection. Both roles
use the same authentication and gate policy; ADMIN broadens management scope. Hide
admin navigation for developers for usability, while backend checks remain authoritative.
