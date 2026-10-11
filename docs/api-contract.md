# Secret Shield API contract

This document currently defines the error contract implemented in M01.3.
The entity, pagination, filter, and frontend DTO contracts in M01.1 remain pending.

## Error responses

Controller errors and security-filter errors use `Content-Type: application/json`
and the same four-field body. The HTTP status matches the `status` field.

```json
{
  "success": false,
  "status": 400,
  "code": "BAD_REQUEST",
  "message": "Invalid request"
}
```

| HTTP status | Code | Message | Trigger |
| --- | --- | --- | --- |
| 400 | `BAD_REQUEST` | Invalid request | Invalid DTO fields, malformed/missing JSON, invalid request parameters, or constraint validation |
| 401 | `UNAUTHORIZED` | Authentication required or credentials invalid | Invalid login credentials; missing/invalid/unknown-user bearer credentials on protected routes; JWT parsing failures |
| 403 | `FORBIDDEN` | Access denied | Authenticated request rejected by authorization, including security-filter access denial |
| 404 | `NOT_FOUND` | Resource not found | Missing authenticated route/static resource, or `ResourceNotFoundException` |
| 409 | `CONFLICT` | Resource already exists or conflicts with current state | Duplicate registration, `ConflictException`, `DuplicateKeyException`, or a wrapped PostgreSQL unique violation (`23505`) |
| 500 | `INTERNAL_SERVER_ERROR` | An unexpected error occurred | Unexpected application failure or a database constraint/infrastructure failure that is not a duplicate |

Other framework errors retain their HTTP status and protocol headers: for example,
405 uses `METHOD_NOT_ALLOWED` / `Method Not Allowed` and retains `Allow`; 415 uses
`UNSUPPORTED_MEDIA_TYPE` / `Unsupported Media Type`. They use the same JSON shape.
Framework 5xx responses use the generic internal-error code and message.

Messages are fixed public text. Responses omit exception messages, stack traces,
SQL details, rejected input values, passwords, JWTs, and source snippets. Validation
responses deliberately omit field-specific details. Clients should use `status`
and `code` for decisions rather than parsing `message`.

Security authentication failures include `WWW-Authenticate: Bearer` and do not
redirect to a login page. Security runs before controller routing/validation:
an unauthenticated request to a protected or nonexistent route receives 401 first.
Registration and login remain public. M01.4 also makes the exact
`GET /api/health/live` route public; see [runtime configuration](runtime-configuration.md).
An authentication database outage returns
the safe 500 body rather than being reported as invalid credentials.

Successful registration/login responses retain their existing DTO shapes;
they are not wrapped in this error envelope. The older `ApiResponse` DTO is
not the error contract.

## Implementation and verification

`ApiError` owns the public body. `GlobalExceptionHandler` handles application and
Spring MVC exceptions. `JsonSecurityErrorHandler` implements the Spring Security
[authentication entry point](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/web/AuthenticationEntryPoint.html)
and [access-denied handler configuration](https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/config/annotation/web/configurers/ExceptionHandlingConfigurer.html).
The JWT filter also uses this writer for authentication parsing/infrastructure errors.

Verified on 11 October 2026 with the checked-in wrapper and unchanged dependencies:

```powershell
.\mvnw.cmd -o -DskipTests compile
.\mvnw.cmd -o '-Dtest=ApiErrorHandlingTests,AuthServiceConflictTests' test
```

All 23 focused tests passed without a database or private environment values.
Coverage includes the six required statuses, malformed/missing JSON, DTO validation,
duplicate prechecks and wrapped database unique violations, MVC and filter-level
authorization failures, JWT parsing and authentication database failures, authenticated
missing routes, preserved framework statuses/headers, unchanged success responses,
and absence of synthetic sensitive exception/input text in error bodies.

Tests used Java 26.0.2 with Maven compiling for the existing Java 21 release target.
The original database-backed `SecretShieldApplicationTests` context test was not run
as part of this step; isolated database test setup remains M01.5. JWT expiry/tampering
and real database race verification remain part of later authentication testing.
