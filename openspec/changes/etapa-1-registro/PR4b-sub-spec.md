# Sub-Spec PR4b — Refresh token rotation + logout

> Source: `refresh-token-rotation.md` and `company-user-auth.md`
> (refresh + logout scenarios only). Rate limit, security headers,
> and lockout dedicated IT land in PR4c.

## Goal

Implement the refresh + logout + reuse-detection slice behind the
company-auth login that PR4a wired. The contract surface is:

- `POST /api/v1/auth/refresh` — validates the `refresh_token`
  cookie, rotates it, and returns a new `access_token` + new
  `refresh_token` cookies. Reuse of a rotated token revokes the
  entire `replaced_by` chain and writes a `TOKEN_REUSE_DETECTED`
  audit event.
- `POST /api/v1/auth/logout` — revokes the presented refresh
  token, clears both cookies, and returns 204.
- `LoginService` now also persists a `refresh_tokens` row on
  success and the controller writes the cookie.

## In scope

- `RefreshTokenService` (system-DS scoped for the lookup-and-
  rotate path; the cross-tenant `token_hash` column is unique,
  so a single repo for both COMPANY and PLATFORM works once
  PLATFORM lands in PR7).
- `RefreshTokenAdminRepository` extends `JpaRepository<RefreshToken, UUID>`,
  bound to the systemDataSource (BYPASSRLS). Adds
  `findByTokenHash(String)`, `findByIdAndUserIdAndReplacedByIsNull(...)`,
  and a chain-walk query (`findByReplacedByOrderByCreatedAtDesc`)
  for reuse detection.
- `AuthController.refresh` and `AuthController.logout`.
- Update `LoginService.login` to call `refreshTokenService.issue`
  on success.
- Update `AuthController.login` to write the `refresh_token`
  cookie.
- `RefreshTokenSmokeIT` (raw-JDBC, NO Spring context) — covers
  the 4 critical chain scenarios from the spec using the same
  test fixture pattern as `RlsIntegrationIT`.

## Out of scope (deferred to PR4c / PR7)

- Rate limit on `/refresh` (PR4c).
- `REFRESH_TOKEN_SCOPE_MISMATCH` as a distinct error code
  (currently folded into `REFRESH_TOKEN_INVALID` — a v1
  simplification, see spec open_gaps).
- Platform refresh + logout (PR7).
- Reuse-detection walking a chain beyond the user's
  `user_id + user_scope` boundary (defensive v1 simplification:
  we always scope the chain walk to the presented row's
  `user_id + user_scope`, which is safe because a chain
  belongs to one user by construction).

## Acceptance

- `mvn verify` green: 44 unit + 16 RegistrationIT + 10
  RlsIntegrationIT + 5 DswiringIT + (new) 4+ RefreshTokenSmokeIT
  all pass.
- The cookie `refresh_token` has the right
  `Path=/api/v1/auth`, `HttpOnly`, `SameSite=Strict`, and
  `Max-Age=604800` (7 days).
- Calling `/refresh` rotates the row, and the BCrypt of the
  new cookie UUID matches the new `token_hash`.
- Calling `/refresh` with a token whose row is already
  `revoked_at IS NOT NULL` revokes the entire chain (verified
  by the IT querying `public.refresh_tokens` directly).

## Files touched (estimate ~500 added lines)

| File | Status | LoC |
|------|--------|-----|
| `auth/dto/RefreshResponse.java` (re-uses LoginResponse) | NEW | ~0 |
| `auth/dto/LogoutResponse.java` | NEW | ~5 |
| `auth/repository/system/RefreshTokenAdminRepository.java` | NEW | ~30 |
| `auth/service/RefreshTokenService.java` | NEW | ~220 |
| `auth/service/LoginService.java` | EDIT (call refreshTokenService.issue) | +15 |
| `auth/controller/AuthController.java` | EDIT (+refresh, +logout, +refresh cookie) | +60 |
| `test/.../auth/RefreshTokenSmokeIT.java` | NEW | ~250 |
