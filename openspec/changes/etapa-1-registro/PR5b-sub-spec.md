# Sub-Spec PR5b — `GET /api/v1/auth/me` (first authenticated endpoint)

> Source: `company-user-auth.md` (the `/me` scenario is the
> canonical "echo the principal" test for any auth system).

## Goal

Add the first endpoint that requires the cookie-based
`Authentication` (anything under `.authenticated()` in
`SecurityConfig`). The endpoint returns the typed
`ParsedToken` claims as JSON so the client can render a
profile / refresh local state from a single round-trip.

## In scope

- `MeResponse` DTO (record): mirrors `LoginResponse.User`
  but is the canonical answer for "who is the current user
  from the JWT" — same shape, different endpoint semantics.
- `AuthController.me(Authentication)` (no request body, no
  path variable, no query): reads the principal from
  Spring's `@AuthenticationPrincipal` resolution path,
  returns 200 with the JSON projection.
- `AuthenticatedFlowIT` (raw-JDBC + `MockMvc`): four
  scenarios exercising the full chain — the
  `AuthenticationFilter` + the new controller + the
  `GlobalExceptionHandler`. Coverage:
  1. no cookie → 401 UNAUTHENTICATED
  2. valid COMPANY cookie → 200 with the user projection
  3. valid PLATFORM cookie → 403 FORBIDDEN_SCOPE
  4. malformed cookie → 401 UNAUTHENTICATED

## Out of scope

- A refresh-cookie re-issuance on `/me` (the cookie is
  read-only here; the client uses `/refresh` for that —
  PR4b).
- A more detailed "full user" projection (the `me` endpoint
  is intentionally a projection of the JWT only — we do
  NOT query the DB on this path, because the spec pins
  `me` to "echo the access_token claims". A future
  `/api/v1/company-users/me` would hit the DB.)
- A `PlatformMeResponse` mirror (the platform login path
  is PR7; the company-side `/me` is the gate the client
  needs first).

## Acceptance

- `mvn verify` green: 82 prior tests + 4 new
  `AuthenticatedFlowIT` scenarios all pass.

## Files touched (~150 added lines)

| File | Status | LoC |
|------|--------|-----|
| `auth/dto/MeResponse.java` | NEW | ~30 |
| `auth/controller/AuthController.java` | EDIT (add /me) | +20 |
| `test/.../auth/AuthenticatedFlowIT.java` | NEW | ~150 |
