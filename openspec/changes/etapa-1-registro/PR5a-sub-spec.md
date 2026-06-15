# Sub-Spec PR5a — Authentication filter (cookie → Authentication)

> Source: `company-user-auth.md` (the `auth/me` path that runs
> AFTER the filter) + ADR-0003 (JWT claims shape) + ADR-0005
> (path-scoped cookies).

## Goal

Translate the `access_token` cookie into a Spring
`Authentication` so any endpoint marked `authenticated()` in
`SecurityConfig` actually runs as that user. The cookie is path-
scoped per ADR-0005 (`/api/v1` for company), the token is
HS256-signed, and the failure modes must mirror the canonical
error envelope (`UNAUTHENTICATED` 401 / `FORBIDDEN_SCOPE` 403).

## In scope

- `JwtAuthentication` (`AbstractAuthenticationToken`): principal is
  the typed `JwtService.ParsedToken`; authorities are derived
  from claims (`ROLE_<role>`, `SCOPE_<scope>`).
- `AuthenticationFilter` (`OncePerRequestFilter`):
  - reads the `access_token` cookie from the company path scope
    (`/api/v1`) for any request to a non-permitAll path
  - calls `JwtService.parseAndVerify(cookie)`; on failure the
    filter chain proceeds with NO authentication (the security
    rule returns 401 via the existing `GlobalExceptionHandler`
    UNAUTHENTICATED path)
  - on success, sets a `JwtAuthentication` on the
    `SecurityContextHolder` so downstream code can
    `@AuthenticationPrincipal` access the typed claims
  - 403 `FORBIDDEN_SCOPE` when a PLATFORM cookie is presented to
    a company-scoped endpoint
- Wire the filter into `SecurityConfig` AFTER
  `SecurityHeadersFilter` and `RateLimitFilter` (auth must be
  resolved after the lighter filters), BEFORE
  `UsernamePasswordAuthenticationFilter`.
- `AuthenticationFilterTest` (a pure unit test that boots a
  `MockHttpServletRequest` + `MockHttpServletResponse` + a
  hand-rolled `FilterChain` to assert the principal / null paths).

## Out of scope

- A Spring-context IT (the `LoginFlowIT` Testcontainers fragility
  is documented and applies here too — gate is the 78/78
  `mvn verify` plus this new unit test).
- Refresh-rotation when the access token is expired (the
  client calls `/api/v1/auth/refresh` with the refresh cookie;
  PR4b already handles that path).
- `PlatformUserService` / `PlatformAdminRepository` (the
  PLATFORM-scope path lands in PR7; for now the filter
  rejects a PLATFORM cookie on a company path with 403
  `FORBIDDEN_SCOPE`, which is the spec behavior).
- Wiring `@PreAuthorize("hasAuthority('SCOPE_COMPANY')")` per
  endpoint (future PR5+; this commit only wires the principal
  so endpoints can read it).

## Acceptance

- `mvn verify` green: 78 prior tests + (new) 3-5
  `AuthenticationFilterTest` unit scenarios all pass.
- The filter does NOT throw; bad tokens simply leave the
  `SecurityContextHolder` empty so Spring Security's
  `anyRequest().authenticated()` produces a 401
  `UNAUTHENTICATED` via the existing exception handler.

## Files touched (~210 added lines)

| File | Status | LoC |
|------|--------|-----|
| `auth/security/JwtAuthentication.java` | NEW | ~60 |
| `auth/filter/AuthenticationFilter.java` | NEW | ~120 |
| `config/SecurityConfig.java` | EDIT (wire the new filter) | +5 |
| `test/.../auth/filter/AuthenticationFilterTest.java` | NEW | ~120 |
