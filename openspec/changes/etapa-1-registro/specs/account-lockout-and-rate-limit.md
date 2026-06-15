# Delta Spec: account-lockout-and-rate-limit

> Capability scope (from proposal): cross-cutting security pieces from
> `auth-company` / `auth-platform` — failed-attempt counter, lockout,
> and Bucket4j-based rate limiting on the registration and login
> endpoints.
>
> Source PRD: lines 901-905 (rate-limit budgets), 907-910 (lockout rule),
> 923-930 (audit log events), 1340-1346 (config knobs).

## Purpose

The system SHALL defend authentication and registration endpoints
against brute-force and abuse by enforcing per-account lockout (5
failures in 15 minutes → 30-minute lock) and per-IP rate limits (Bucket4j)
on register, login, and availability endpoints.

## ADDED Requirements

### Requirement: Failed-Attempt Counter

The system SHALL increment `company_users.failed_login_attempts` (resp.
`platform_users.failed_login_attempts`) by 1 on every failed login and
SHALL reset it to 0 on every successful login (PRD line 908, 663).

#### Scenario: Counter increments on failure

- **WHEN** a login attempt returns `INVALID_CREDENTIALS`
- **THEN** the `failed_login_attempts` of the targeted user row is
  incremented by 1 within the same transaction

#### Scenario: Counter resets on success

- **WHEN** a login attempt succeeds
- **THEN** the `failed_login_attempts` of the user row is set to 0
- **AND** `last_login_at` is set to the current timestamp

#### Scenario: Counter resets on lockout expiry

- **WHEN** `locked_until` is in the past and a new login attempt
  arrives
- **THEN** the system treats the account as not currently locked and
  evaluates the counter from the current moment (open gap: see
  `open_gaps` for whether expired failures are pruned or remain
  cumulative)

### Requirement: Account Lockout After 5 Failures in 15 Minutes

The system SHALL lock the account for 30 minutes when 5 failed login
attempts occur within a rolling 15-minute window (PRD lines 907-910,
DoD line 1492).

#### Scenario: 5th failure in 15 min triggers 30-min lockout

- **GIVEN** a user has accumulated 4 failed attempts in the last 15 min
- **WHEN** a 5th failed attempt occurs
- **THEN** `locked_until` is set to `now() + 30 minutes`
- **AND** the 6th attempt (even with valid credentials) responds with
  HTTP 403 `ACCOUNT_LOCKED`
- **AND** the 6th-attempt response includes
  `error.details.retryAfterSeconds` set to the integer seconds
  remaining until `locked_until` (within ±1 s of the
  `Retry-After` header on the same response)
- **AND** the 6th-attempt response includes the HTTP header
  `Retry-After: <seconds>` matching the same value
- **AND** an `audit_log` row with `event_type = 'ACCOUNT_LOCKED'` and
  `metadata.lockedUntil` is written (PRD line 928)

#### Scenario: Locked account cannot log in with valid credentials

- **WHEN** a client posts the correct password to a locked account
- **THEN** the system responds with HTTP 403
- **AND** `error.code` equals `ACCOUNT_LOCKED`
- **AND** the `failed_login_attempts` counter is NOT incremented
  (locked accounts do not re-arm the timer)
- **AND** the response body includes
  `error.details.retryAfterSeconds` set to the integer number of
  seconds remaining until `locked_until` (e.g. `1800` for a fresh
  30-minute lock)
- **AND** the response includes the HTTP header
  `Retry-After: <seconds>` with the same integer value as
  `details.retryAfterSeconds` (RFC 9110 §10.2.3 delta-seconds form)

#### Scenario: Lockout expires after 30 minutes

- **GIVEN** a user is locked until `T0 + 30 min`
- **WHEN** the client attempts login at `T0 + 30 min + 1 s` with the
  correct password
- **THEN** login succeeds (the lockout window has elapsed)
- **AND** `failed_login_attempts` is reset to 0

### Requirement: Platform User Lockout (Mirrored Rule)

The system SHALL apply the same 5-failures-in-15-min → 30-min lockout
rule to `platform_users` (no PRD exception; treat symmetrically).

#### Scenario: Platform user lockout

- **GIVEN** a platform user with 4 failed attempts in the last 15 min
- **WHEN** a 5th failed attempt occurs
- **THEN** `platform_users.locked_until` is set to `now() + 30 minutes`
- **AND** further attempts respond with HTTP 403 `ACCOUNT_LOCKED`
- **AND** those 403 responses include both
  `error.details.retryAfterSeconds` and the `Retry-After` HTTP header
  with the same integer value (mirroring the company-user rule above)

### Requirement: Rate Limit on Register

The system SHALL enforce a Bucket4j rate limit of 5
`POST /api/v1/auth/register` requests per source IP per hour (PRD
line 901).

#### Scenario: 6th registration from the same IP within an hour is rejected

- **GIVEN** 5 `POST /api/v1/auth/register` requests from IP `1.2.3.4`
  in the last 60 minutes
- **WHEN** a 6th request from the same IP arrives
- **THEN** the system responds with HTTP 429
- **AND** `error.code` equals `RATE_LIMIT_EXCEEDED`
- **AND** an `audit_log` row with `event_type = 'RATE_LIMIT_EXCEEDED'`
  is written (PRD line 929)

### Requirement: Rate Limit on Login (Company and Platform)

The system SHALL enforce a Bucket4j rate limit of 10
`POST /api/v1/auth/login` and `POST /api/v1/platform/auth/login`
requests per source IP per minute (PRD line 902).

#### Scenario: 11th login from the same IP within a minute is rejected

- **GIVEN** 10 `POST /api/v1/auth/login` requests from IP `1.2.3.4` in
  the last 60 seconds
- **WHEN** an 11th request from the same IP arrives
- **THEN** the system responds with HTTP 429
- **AND** `error.code` equals `RATE_LIMIT_EXCEEDED`

### Requirement: Rate Limit on Availability Endpoints

The system SHALL enforce a Bucket4j rate limit of 30 requests per
minute per source IP on
`GET /api/v1/tenants/me/slug-availability` and
`GET /api/v1/tenants/me/cuit-availability` (PRD lines 903-904).

#### Scenario: 31st availability check in a minute is rejected

- **GIVEN** 30 slug-availability requests from IP `1.2.3.4` in the last
  60 seconds
- **WHEN** a 31st request arrives
- **THEN** the system responds with HTTP 429 `RATE_LIMIT_EXCEEDED`

### Requirement: Rate Limit Storage Scope

The system SHALL use in-memory Bucket4j for v1 (PRD line 905);
distributed rate limiting via Redis is explicitly out of scope.

#### Scenario: Rate limit applies per backend instance

- **WHEN** the application runs in a single instance
- **THEN** the in-memory bucket correctly enforces the documented
  budgets
- **AND** if the application is later scaled horizontally, the
  effective budget per IP becomes (budget × N instances); this is
  documented as a known limitation of the v1 choice
