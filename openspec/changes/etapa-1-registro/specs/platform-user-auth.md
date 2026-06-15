# Delta Spec: platform-user-auth

> Capability scope (from proposal): `auth-platform` — platform-user login
> (email + password), refresh, logout, `auth/me`, path-scoped cookies, no
> RLS, and isolation from company endpoints.
>
> Source PRD: lines 312-341 (platform_users table), 756-803
> (platform endpoints), 879-894 (path-scoped cookies), 1178-1184
> (no platform UI in v1).

## Purpose

The system SHALL allow a platform user (a `platform_users` row) to log in
with `(email, password)`, receive access and refresh cookies scoped to
the `/api/v1/platform` path, refresh, log out, and read their own profile
— while NEVER being able to call company-scope endpoints with those
cookies.

## ADDED Requirements

### Requirement: Platform User Login Endpoint

The system SHALL expose `POST /api/v1/platform/auth/login` (no auth
required) that authenticates a platform user by `(email, password)` and
issues `access_token` and `refresh_token` cookies scoped to the platform
path prefix (PRD lines 756-790).

#### Scenario: Successful platform login

- **WHEN** a client posts `{ email, password }` with a valid combination
  where the `platform_users` row is `ACTIVE` and not locked
- **THEN** the system responds with HTTP 200
- **AND** the response body is `{ user: { id, email, firstName,
  lastName, role, scope: "PLATFORM" }, expiresIn: 900 }`
- **AND** a `Set-Cookie: access_token=...; Path=/api/v1/platform;
  HttpOnly; SameSite=Strict; Max-Age=900` header is present
- **AND** a `Set-Cookie: refresh_token=...; Path=/api/v1/platform/auth;
  HttpOnly; SameSite=Strict; Max-Age=604800` header is present

#### Scenario: Invalid platform credentials

- **WHEN** a client posts any `(email, password)` that does not match an
  `ACTIVE` platform user
- **THEN** the system responds with HTTP 401
- **AND** `error.code` equals `INVALID_CREDENTIALS`

#### Scenario: Platform account disabled

- **WHEN** a client posts a valid `(email, password)` but
  `platform_users.status = 'DISABLED'`
- **THEN** the system responds with HTTP 403
- **AND** `error.code` equals `ACCOUNT_DISABLED`

#### Scenario: Platform login side effects

- **WHEN** platform login succeeds
- **THEN** `platform_users.failed_login_attempts` is reset to 0
- **AND** `platform_users.last_login_at` is set to the current timestamp
- **AND** a `refresh_tokens` row is created with `user_scope = 'PLATFORM'`,
  `tenant_id` is NULL, and the stored `token_hash` is the BCrypt hash of
  the issued refresh UUID
- **AND** an `audit_log` row with `event_type = 'USER_LOGIN_SUCCESS'` and
  `user_scope = 'PLATFORM'` is written

### Requirement: Platform Refresh and Logout Endpoints

The system SHALL expose `POST /api/v1/platform/auth/refresh` and
`POST /api/v1/platform/auth/logout` that follow the same rotation and
revocation rules as the company equivalents, but operate on
`user_scope = 'PLATFORM'` rows (PRD lines 792-798).

#### Scenario: Successful platform refresh

- **WHEN** a client posts to `/api/v1/platform/auth/refresh` with a valid
  `refresh_token` cookie whose `user_scope = 'PLATFORM'`
- **THEN** the system rotates the refresh token and responds with HTTP 200
  + new cookies (same shape as platform login)

#### Scenario: Successful platform logout

- **WHEN** a client posts to `/api/v1/platform/auth/logout` with a valid
  refresh token
- **THEN** the system responds with HTTP 204 and clears the platform-scoped
  cookies
- **AND** the refresh token row is revoked

### Requirement: Platform Profile Endpoint

The system SHALL expose `GET /api/v1/platform/auth/me` that returns the
current platform user's profile (PRD line 800).

#### Scenario: Authenticated platform user reads own profile

- **WHEN** a client sends a request with a valid platform-scoped
  `access_token` cookie
- **THEN** the system responds with HTTP 200
- **AND** the body contains `{ id, email, firstName, lastName, role,
  scope: "PLATFORM" }` matching the JWT `sub` and `role` claims

### Requirement: Cross-Scope Cookie Rejection

The system SHALL NOT accept a `COMPANY` JWT on platform endpoints and
SHALL NOT accept a `PLATFORM` JWT on company endpoints (PRD lines 1497-1499
DoD).

#### Scenario: Platform user hits company endpoint

- **WHEN** a client sends a request to `/api/v1/auth/me` or
  `/api/v1/tenants/me` while presenting a `Path=/api/v1/platform`
  `access_token` cookie
- **THEN** the browser MUST NOT attach the platform cookie to the request
  (different `Path`), and even if the token is sent directly (e.g. curl
  with `-H "Cookie: ..."`), the server SHALL respond with HTTP 401
  `INVALID_CREDENTIALS` or 403 `FORBIDDEN_SCOPE`

#### Scenario: Company user hits platform endpoint

- **WHEN** a client sends a request to `/api/v1/platform/tenants` while
  presenting a `Path=/api/v1` `access_token` cookie whose `scope =
  "COMPANY"`
- **THEN** the system responds with HTTP 403
- **AND** `error.code` equals `FORBIDDEN_SCOPE`

### Requirement: Platform JWT Has No Tenant Claim

The system SHALL NOT include a `tid` or `slug` claim in a platform
`access_token` JWT (PRD line 876).

#### Scenario: Platform JWT has only the documented claims

- **WHEN** a successful platform login or refresh issues a new access
  token
- **THEN** decoding the JWT reveals `sub`, `role`, `scope = "PLATFORM"`,
  `iat`, `exp`, `iss`, `aud`
- **AND** the JWT MUST NOT contain `tid` or `slug` claims
