# Sub-Spec PR4a — Company login + Authentication filter

> Source: `company-user-auth.md` (login + me scenarios only; refresh and
> logout land in PR4b, rate-limit and security headers in PR4c).

## Goal

Issue `access_token` and `refresh_token` cookies on
`POST /api/v1/auth/login` after validating `(tenantSlug, username,
password)` against the system-side DataSource (BYPASSRLS). Wire the
authentication filter that translates the `access_token` cookie into a
Spring `Authentication` so any subsequent `/api/v1/auth/me`-style
endpoint is one `@AuthenticationPrincipal` away.

## In scope

- `LoginService.login(slug, username, password)` returns a
  `LoginResult` carrying the user, the tenant, the access JWT, and the
  raw refresh UUID.
- `AuthService` (alias wrapper kept for spec naming; the impl is
  `LoginService` until refresh lands in PR4b).
- `AuthenticationFilter` (`OncePerRequestFilter`) that:
  - Reads the `access_token` cookie from the company path scope
    (`/api/v1`).
  - Verifies it via `JwtService.parseAndVerify`.
  - Sets a `JwtAuthentication` on the `SecurityContext` (a
    `UsernamePasswordAuthenticationToken` with the parsed claims as
    principal and the authorities derived from
    `scope` + `role`).
  - For `scope=PLATFORM` presented to a company path, returns
    `403 FORBIDDEN_SCOPE` immediately.
  - For invalid / expired / missing tokens, leaves the context
    unauthenticated so the security chain runs the
    `anyRequest().authenticated()` rule and the controller advice
    returns `401 UNAUTHENTICATED`.
- `AuthController.login` endpoint
  (`POST /api/v1/auth/login`, public).
- `AuthController.me` endpoint
  (`GET /api/v1/auth/me`, authenticated).
- `LoginRequest` and `LoginResponse` DTOs.
- `LoginFlowIT` covering the 4 scenarios (200 happy path, 401
  invalid, 403 disabled, 403 locked).

## Out of scope (deferred)

- Refresh + logout (PR4b).
- Reuse-detection / chain revoke (PR4b).
- Rate limiting + security headers + lockout (PR4c).
- BCrypt strength unit test (PR2b already covers BCrypt via
  `PasswordEncoder`; no new unit test needed here).
- Platform login (PR7).
- Bucket4j wiring (PR4c).

## Acceptance

- `mvn verify` green: 4 new IT scenarios in `LoginFlowIT` plus all
  existing 75 tests still pass.
- `/api/v1/auth/me` returns 401 when the cookie is missing.
- `/api/v1/auth/me` returns 403 when a PLATFORM cookie is presented
  to a company path.
- `AuthController.login` issues both cookies with the right
  `Path`, `HttpOnly`, `SameSite=Strict`, and the documented
  `Max-Age` (900s access, 7d refresh).

## Files touched (estimate ~280 added lines)

| File | Status | LoC |
|------|--------|-----|
| `auth/dto/LoginRequest.java` | NEW | ~25 |
| `auth/dto/LoginResponse.java` | NEW | ~30 |
| `auth/security/JwtAuthentication.java` | NEW | ~15 |
| `auth/security/JwtAuthenticationDetails.java` | NEW | ~25 |
| `auth/service/LoginService.java` | NEW | ~120 |
| `auth/filter/AuthenticationFilter.java` | NEW | ~80 |
| `auth/controller/AuthController.java` | EDIT (+login +me) | +30 |
| `config/SecurityConfig.java` | EDIT (+filter wiring) | +5 |
| `test/.../auth/LoginFlowIT.java` | NEW | ~250 |
