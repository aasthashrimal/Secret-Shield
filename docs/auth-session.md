# Authentication and browser session policy

Module 02 is implemented by Member 1, including the documentation originally
assigned to Member 2. The React client itself remains Module 11.

## Requests and validation

POST /api/auth/register accepts username, email and password and returns the
existing numeric-id registration DTO with role DEVELOPER. Username is trimmed,
case-sensitive, 3–50 ASCII letters/digits/underscore/dot/hyphen. Email is stripped
and lowercased with Locale.ROOT before validation, duplicate checks and lookup;
maximum length is 255. Passwords are never trimmed or normalized. Registration
requires 8–72 UTF-16 code units and at most 72 UTF-8 bytes (BCrypt's input limit).
Login permits existing shorter passwords but still rejects blank/overlong input.
Validation gives the fixed JSON 400 body; duplicates give fixed JSON 409.

V3 normalizes existing stored email addresses and adds a normalized unique index
and canonical-value check. It refuses collisions rather than merging identities.
Password hashes, IDs and timestamps remain unchanged. See [migrations](database-migrations.md).
Registration flushes inside the repository transaction so a competing unique
constraint violation is caught as DuplicateUserException after transaction rollback.
Other integrity failures retain safe 500 behavior. Real parallel requests are tested.

POST /api/auth/login returns the existing token/username/email/role DTO. Wrong or
unknown credentials have the same safe JSON 401. A generated JWT lasts one hour.
GET /api/auth/me requires a valid bearer JWT and returns only id (decimal string),
username, email and role. Password/hash/token/timestamps are absent. The filter
loads the user on each request and uses the database role, not a role claimed in
the JWT; removed/unknown users are rejected. Missing, malformed, tampered and expired
tokens on protected routes return JSON 401 and WWW-Authenticate: Bearer.

## Client token lifecycle

The future browser client keeps the JWT in memory only, attaches
Authorization: Bearer to API requests, and uses fetch credentials: omit.
No localStorage/sessionStorage persistence, authentication cookie, refresh token,
or server session is introduced. Reloading/closing the page requires sign-in.
After login the client loads /me as the authoritative profile before opening
protected pages. Decoding exp may schedule the expiry message, but does not prove
authentication; the server verifies the token on every protected request.

Logout clears the in-memory JWT/profile/timers and navigates to sign-in. No backend
logout endpoint exists. A copied token remains usable until expiry (or user deletion
or signing-key rotation); logout does not revoke it. On expiry or an API 401, clear
the session once, show “Your session expired. Sign in again.”, and return to sign-in.
Do not automatically replay writes after reauthentication. A 403 shows access denied
without clearing a valid session. A 500 reports a temporary failure without treating
it as invalid credentials. Never display/log JWTs or store them in URLs.

## CORS, CSRF and authentication paths

The default allowed frontend origin is exactly http://localhost:5173, configurable
as FRONTEND_ORIGIN (one HTTP(S) origin, no wildcard/path/trailing slash). localhost
and 127.0.0.1 are different origins. Allowed methods are GET/POST/PATCH/DELETE/OPTIONS;
request headers are Authorization/Content-Type, and WWW-Authenticate is exposed.
Preflight is processed before JWT authentication. Unsupported origins/methods/headers
receive the same safe JSON 403 envelope. Requests without Origin (CLI/server tools)
continue to use the usual authentication rules; CORS is not resource authorization.
No Access-Control-Allow-Credentials or session cookie is emitted.
This order follows [Spring Security CORS guidance](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html).

CSRF remains disabled because the application authenticates exclusively through
explicit bearer headers, without browser-attached cookies, HTTP Basic or form login.
Statelessness alone is not justification. Switching to cookies/Basic requires a new
CSRF policy before enabling those paths; see [Spring Security CSRF guidance](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
The default servlet UserDetailsService auto-configuration is excluded; HTTP Basic,
form login and Spring's session logout filter are explicitly disabled. A custom
SecurityFilterChain alone did not remove the default in-memory user. This matches
[Spring Boot's security configuration distinction](https://docs.spring.io/spring-boot/reference/web/spring-security.html).

JWT_SECRET must be nonblank and at least 32 UTF-8 bytes; missing/invalid settings
fail startup with a fixed message that omits the supplied value. Generate it privately
as described in [local setup](local-setup.md). The template remains empty. There is
no shared fallback production key. Tests use pinned synthetic keys only.
