# Authentication manual checks

Member 1 completed M02.6 under the solo implementation scope. Use only synthetic
accounts and private tokens. The automated full suite runs these HTTP script checks
against a random-port server connected to the guarded disposable PostgreSQL database.
It removes the synthetic account afterwards and the runner removes the cluster.

## Run the verified checks

```powershell
powershell -NoProfile -File .\scripts\test-postgres.ps1
```

AuthenticationHttpIT executes scripts/check-auth.ps1 over real HTTP, rather than
MockMvc. The rest of the suite additionally verifies expired tokens, password hashes,
multibyte bounds, concurrent registration and migration collisions.

For a manual check of your running local backend:

```powershell
powershell -NoProfile -File .\scripts\start-local.ps1
```

In another terminal:

```powershell
powershell -NoProfile -File .\scripts\check-auth.ps1 -CreateSyntheticUser
```

The explicit switch permits **one new synthetic demo account** in the database used
by that backend. This local account remains after the script; no user-deletion API
exists. Its random password/token are kept privately in script memory and never
printed. Prefer the disposable test runner if you want automatic account cleanup.
Use -Port 8081 if startup selected that port. If you changed FRONTEND_ORIGIN,
set the same nonsecret process variable in the second terminal before the check.

Expected: 14 PASS lines and a success message. GET /me without a token gives 401,
invalid registration gives 400, valid registration gives 201, duplicate gives 409,
wrong/unknown login gives 401, correct login gives 200, valid JWT /me and protected
health give 200, tampered JWT gives 401, allowed preflight/browser requests give
200, unsupported origin gives 403, and clearing the token returns /me to 401.

## Placeholder requests for Postman or another private client

Replace placeholders privately. Do not save working tokens/passwords in shared collections.

```http
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{"username":"demo-user","email":"demo@example.invalid","password":"<private-synthetic-password>"}
```

Expected 201 with numeric id, normalized email, username and DEVELOPER role.
Repeat it for 409; use another username with the uppercase/spaced same email for 409.

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{"email":" DEMO@EXAMPLE.INVALID ","password":"<private-synthetic-password>"}
```

Expected 200 with token and safe profile fields. Wrong password and unknown email
both give the same fixed 401. Blank/invalid email or overlong password gives 400.

```http
GET http://localhost:8080/api/auth/me
Authorization: Bearer <private-token-from-login>
```

Expected 200 with exactly string id, username, email, role. Remove the header or
alter the signature for 401. A token older than its one-hour expiry gives 401;
the isolated suite uses a pre-expired synthetic token without waiting an hour.

```http
OPTIONS http://localhost:8080/api/auth/me
Origin: http://localhost:5173
Access-Control-Request-Method: GET
Access-Control-Request-Headers: Authorization
```

Expected 200 with exact Access-Control-Allow-Origin, allowed header/method and no
Allow-Credentials. Use an untrusted origin for safe JSON 403. Browser requests to
/me with the allowed Origin still need a valid JWT. Cookies/Basic cannot authenticate.

## Recorded evidence — 11 October 2026

Java 21 on Windows/PostgreSQL 18.6: the isolated authentication, real concurrent
registration, migrations and real HTTP script checks passed. Test output contains
statuses and synthetic account identity only; no working tokens/passwords/hashes
were printed. The HTTP test removed its account and the runner stopped/removed the
temporary cluster. See [testing](testing.md) for final suite totals and isolation.
