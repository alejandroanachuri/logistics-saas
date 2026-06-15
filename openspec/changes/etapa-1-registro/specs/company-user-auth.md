# Delta Spec: company-user-auth

> Capability scope (from proposal): `auth-company` — login, refresh, logout,
> `auth/me` and `tenants/me` profile reads, lockout, security headers, rate
> limiting (lockout and rate limit detailed in their own specs; this spec
> covers auth endpoint behavior).
>
> Source PRD: lines 624-741 (login, refresh, logout, me), 880-884 (cookies).

## Purpose

The system SHALL allow a company user (a `company_users` row) to log in
using `(tenantSlug, username, password)`, receive scoped access and
refresh cookies, refresh the access cookie by rotating the refresh token,
log out, and read their own profile.

## ADDED Requirements

### Requirement: Company User Login Endpoint

The system SHALL expose `POST /api/v1/auth/login` (no auth required) that
authenticates a company user by `(tenantSlug, username, password)` and
issues `access_token` and `refresh_token` cookies scoped to the company
path prefix.

#### Scenario: Successful login

- **WHEN** a client posts `{ slug, username, password }` with a valid
  combination where the user is `ACTIVE` and not currently locked
- **THEN** the system responds with HTTP 200
- **AND** the response body is `{ user: { id, tenantId, tenantSlug,
  username, email, firstName, lastName, role, scope: "COMPANY" },
  expiresIn: 900 }`
- **AND** a `Set-Cookie: access_token=...; Path=/api/v1; HttpOnly;
  SameSite=Strict; Max-Age=900` header is present
- **AND** a `Set-Cookie: refresh_token=...; Path=/api/v1/auth; HttpOnly;
  SameSite=Strict; Max-Age=604800` header is present

#### Scenario: Invalid credentials (generic)

- **WHEN** a client posts any combination of `slug + username + password`
  where either the tenant does not exist, the user does not exist within
  that tenant, or the password does not match the BCrypt hash
- **THEN** the system responds with HTTP 401
- **AND** `error.code` equals `INVALID_CREDENTIALS`
- **AND** the response message MUST NOT reveal which of the three fields
  was wrong (PRD line 657, DoD line 1491)

#### Scenario: Account disabled

- **WHEN** a client posts a valid `(slug, username, password)` but the
  `company_users.status = 'DISABLED'`
- **THEN** the system responds with HTTP 403
- **AND** `error.code` equals `ACCOUNT_DISABLED`

#### Scenario: Account locked

- **WHEN** a client posts a valid `(slug, username, password)` but the
  `company_users.locked_until` is in the future
- **THEN** the system responds with HTTP 403
- **AND** `error.code` equals `ACCOUNT_LOCKED`

#### Scenario: Login side effects on success

- **WHEN** login succeeds
- **THEN** `company_users.failed_login_attempts` is set to 0
- **AND** `company_users.last_login_at` is set to the current timestamp
- **AND** a new `refresh_tokens` row is created with `user_scope =
  'COMPANY'`, the stored `token_hash` is the BCrypt hash of the issued
  refresh UUID, and `expires_at = now() + 7 days`
- **AND** an `audit_log` row with `event_type = 'USER_LOGIN_SUCCESS'` is
  written (PRD line 925)

#### Scenario: Login side effects on failure

- **WHEN** login fails (invalid credentials)
- **THEN** `company_users.failed_login_attempts` is incremented by 1
- **AND** an `audit_log` row with `event_type = 'USER_LOGIN_FAILED'` and
  `metadata.reason` describing the failure is written

#### Scenario: Rate limit on login

- **WHEN** a client exceeds 10 `POST /api/v1/auth/login` requests from the
  same IP within 60 seconds (PRD line 902)
- **THEN** the system responds with HTTP 429
- **AND** `error.code` equals `RATE_LIMIT_EXCEEDED`

### Requirement: Refresh Endpoint Rotates Tokens

The system SHALL expose `POST /api/v1/auth/refresh` (no body) that reads
the `refresh_token` cookie, validates it (exists, not expired, not revoked,
scope = COMPANY), issues a new `access_token` cookie, and rotates the
refresh token (PRD lines 668-682).

#### Scenario: Successful refresh

- **WHEN** a client posts with a valid `refresh_token` cookie
- **THEN** the system responds with HTTP 200
- **AND** a new `access_token` cookie is set
- **AND** a new `refresh_token` cookie is set
- **AND** the old refresh token row has `revoked_at` set to the current
  timestamp
- **AND** the new refresh token row has `replaced_by` referencing the old
  refresh token's id (PRD line 681)
- **AND** an `audit_log` row with `event_type = 'TOKEN_REFRESHED'` is
  written

#### Scenario: Refresh token not found

- **WHEN** a client posts with a `refresh_token` cookie value whose BCrypt
  hash does not match any row in `refresh_tokens`
- **THEN** the system responds with HTTP 401
- **AND** `error.code` equals `REFRESH_TOKEN_INVALID`

#### Scenario: Refresh token expired

- **WHEN** a client posts with a `refresh_token` whose `expires_at` is in
  the past
- **THEN** the system responds with HTTP 401
- **AND** `error.code` equals `REFRESH_TOKEN_EXPIRED`

#### Scenario: Refresh token already revoked

- **WHEN** a client posts with a `refresh_token` whose `revoked_at` is set
- **THEN** the system responds with HTTP 401
- **AND** `error.code` equals `REFRESH_TOKEN_REVOKED`

### Requirement: Logout Endpoint

The system SHALL expose `POST /api/v1/auth/logout` that revokes the
current refresh token and clears the cookies (PRD lines 683-692).

#### Scenario: Successful logout

- **WHEN** a client posts with a valid `refresh_token` cookie
- **THEN** the system responds with HTTP 204
- **AND** the response includes `Set-Cookie` headers that clear both
  `access_token` and `refresh_token` (empty value, `Max-Age=0`, matching
  the original `Path`)
- **AND** the `refresh_tokens` row has `revoked_at` set to the current
  timestamp
- **AND** an `audit_log` row with `event_type = 'USER_LOGOUT'` is written

### Requirement: Authenticated Profile Endpoint

The system SHALL expose `GET /api/v1/auth/me` that returns the current
company user's profile (PRD lines 724-742).

#### Scenario: Authenticated user reads own profile

- **WHEN** a client sends a request with a valid `access_token` cookie
  whose JWT `scope` is `COMPANY`
- **THEN** the system responds with HTTP 200
- **AND** the body contains `{ id, tenantId, tenantSlug, username, email,
  firstName, lastName, role, scope: "COMPANY", emailVerified }`

#### Scenario: PLATFORM-scope access token presented to /me is rejected

- **GIVEN** a client holds a valid JWT `access_token` whose `scope`
  claim is `"PLATFORM"` (issued by a `POST /api/v1/platform/auth/login`
  flow, not the company login flow)
- **WHEN** the client sends `GET /api/v1/auth/me` with that cookie
- **THEN** the system responds with HTTP 403
- **AND** `error.code` equals `FORBIDDEN_SCOPE` (not `UNAUTHENTICATED`
  and not `INVALID_CREDENTIALS` — the token is valid, the request is
  authenticated, but the presented scope is not authorized for this
  path prefix; see ADR 0005, design §10 gap 2)

#### Scenario: Missing or invalid access token

- **WHEN** a client sends a request without an `access_token` cookie, or
  with an expired / tampered / `scope = "PLATFORM"` token
- **THEN** the system responds with HTTP 401
- **AND** `error.code` equals `UNAUTHENTICATED` (this is the canonical
  "no / bad credentials on an authenticated endpoint" code and is
  distinct from `INVALID_CREDENTIALS`, which is reserved for the
  `POST /api/v1/auth/login` response when the slug + username +
  password combination is wrong; see `company-user-auth` login
  scenarios above and design §10 gap 2)

### Requirement: Access Token Claims

The system SHALL embed in the company `access_token` JWT the claims
defined at PRD lines 864-870 (`sub`, `tid`, `slug`, `role`, `scope =
"COMPANY"`, `iat`, `exp`, `iss`, `aud`) and SHALL sign the token with
HS256 using a 256-bit base64 secret loaded from the `JWT_SECRET`
environment variable (PRD lines 860-862, decision D3).

#### Scenario: Access token carries required claims

- **WHEN** a successful login or refresh issues a new access token
- **THEN** decoding the JWT reveals `sub` = the user id, `tid` = the
  tenant id, `slug` = the tenant slug, `role` = the role name (e.g.
  `COMPANY_ADMIN`), `scope = "COMPANY"`, `iss = "logistics-saas"`, and
  `aud` is set
- **AND** the token's `exp - iat` equals 900 seconds (15 minutes, PRD
  line 862)
