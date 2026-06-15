# Delta Spec: refresh-token-rotation

> Capability scope (from proposal): subset of `auth-company` and
> `auth-platform` — refresh token issuance, hashing, rotation,
> revocation, and scope-aware behavior. Endpoint behavior lives in
> `company-user-auth` and `platform-user-auth`; this spec is the data +
> service contract behind those endpoints.
>
> Source PRD: lines 343-366 (refresh_tokens table), 668-682 (refresh
> endpoint), 877 (opaque UUID, BCrypt-hashed).

## Purpose

The system SHALL issue opaque, single-use refresh tokens to both
company and platform users, SHALL hash them at rest with BCrypt, and
SHALL rotate them on every successful refresh — revoking the previous
token and chaining it through `replaced_by` — so that a stolen token
can only be redeemed once.

## ADDED Requirements

### Requirement: Refresh Token Issuance

The system SHALL generate a cryptographically random UUID as the
opaque refresh token value, hash it with BCrypt strength 12, and
store only the hash in `public.refresh_tokens.token_hash` (PRD line
877, 345-359).

#### Scenario: New refresh token on login

- **WHEN** a successful login (company or platform) occurs
- **THEN** a new `refresh_tokens` row is inserted with a `token_hash`
  that matches the BCrypt hash of the issued UUID
- **AND** the raw UUID is returned to the client only via the
  `Set-Cookie` header
- **AND** the system MUST NOT log the raw UUID at INFO level (audit
  logs MUST log only a truncated prefix, e.g. first 8 chars)

#### Scenario: Refresh token carries scope and tenant

- **WHEN** a refresh token row is created
- **THEN** `user_scope` is `'COMPANY'` or `'PLATFORM'` matching the
  issuing login
- **AND** `tenant_id` is non-null iff `user_scope = 'COMPANY'`
- **AND** `expires_at` is set to `now() + 7 days` (PRD line 863)

### Requirement: Refresh Token Validation

The system SHALL validate a presented refresh token by BCrypt-matching
the cookie value against stored `token_hash` rows and rejecting any
that are missing, expired, or revoked.

#### Scenario: Valid unused token accepted

- **WHEN** a client presents a refresh token cookie whose BCrypt hash
  matches a row with `revoked_at IS NULL` and `expires_at > now()`
- **THEN** validation succeeds and returns the row's `user_id`,
  `user_scope`, and (for COMPANY) `tenant_id`

#### Scenario: Expired token rejected

- **WHEN** a client presents a refresh token whose row has
  `expires_at <= now()`
- **THEN** validation fails with `REFRESH_TOKEN_EXPIRED`

#### Scenario: Revoked token rejected

- **WHEN** a client presents a refresh token whose row has
  `revoked_at IS NOT NULL`
- **THEN** validation fails with `REFRESH_TOKEN_REVOKED`

#### Scenario: Unknown token rejected

- **WHEN** a client presents a refresh token whose BCrypt hash does
  not match any row
- **THEN** validation fails with `REFRESH_TOKEN_INVALID`

### Requirement: Refresh Token Rotation

The system SHALL rotate the refresh token on every successful refresh:
the presented token is revoked (`revoked_at = now()`), a new token is
issued, and the new row's `replaced_by` column points at the old
row's id (PRD line 681).

#### Scenario: Replaced-by chain on successful rotation

- **WHEN** a client successfully refreshes using token T1
- **THEN** T1's row has `revoked_at` set to the current timestamp
- **AND** a new row T2 is inserted with a new `token_hash`,
  `user_scope` and `tenant_id` matching T1, and `replaced_by = T1.id`
- **AND** a new `Set-Cookie: refresh_token=<T2-uuid>` is returned

#### Scenario: Reusing a revoked token is rejected

- **WHEN** a client presents T1 again after T1 has been rotated to T2
- **THEN** validation fails with `REFRESH_TOKEN_REVOKED` (the row is
  already marked revoked)
- **AND** the system MUST log a `TOKEN_REUSE_DETECTED` audit event
  (cross-cutting security concern — see open_gaps for whether to
  additionally revoke T2)

### Requirement: Reuse Detection Revokes Descendant Chain

The system SHALL treat the presentation of a revoked refresh token as a
security event indicating token theft. When a refresh request presents
a token whose `refresh_tokens` row has `revoked_at IS NOT NULL` (i.e.
the token was already rotated, logged out, or otherwise invalidated),
the system SHALL walk the `replaced_by` chain from the presented row
up to the most recent row in that chain and revoke EVERY row in the
chain (including rows that were still non-revoked) by setting
`revoked_at = now()` on all of them in a single transaction (design
§10, gap 3, design §4.6 reuse-detection branch; ADR 0004 §3).

The system SHALL also write an `audit_log` row with
`event_type = 'TOKEN_REUSE_DETECTED'` and
`metadata.chainLength` set to the integer number of rows revoked
(including the presented row). The 401 response to the client SHALL
use `error.code = REFRESH_TOKEN_REVOKED` and SHALL NOT disclose the
chain length or the number of descendant tokens that were revoked.

The chain walk terminates at the row whose `replaced_by IS NULL`
(i.e. the most recent valid row that was never rotated out, if one
exists) and revokes every row encountered along the way. A
theoretically impossible case — the presented token IS the most recent
row in the chain and is already revoked (e.g. a row whose own
`replaced_by IS NULL` was later revoked by a future operation) — is
defensively handled by revoking just that one row.

#### Scenario: Reuse of the oldest revoked token revokes the entire chain

- **GIVEN** a refresh chain `T1 → T2 → T3` where T1 and T2 are
  `revoked_at IS NOT NULL` (rotated) and T3 is `revoked_at IS NULL` and
  `expires_at > now()` (the most recent valid row)
- **WHEN** a client posts to `/api/v1/auth/refresh` presenting T1
- **THEN** the system responds with HTTP 401
- **AND** `error.code` equals `REFRESH_TOKEN_REVOKED`
- **AND** after the request, T1, T2, AND T3 ALL have `revoked_at`
  set to the current timestamp (the legitimate user's valid T3 is
  force-revoked; they MUST re-login)
- **AND** an `audit_log` row with `event_type = 'TOKEN_REUSE_DETECTED'`
  and `metadata.chainLength = 3` is written

#### Scenario: Reuse of a middle token revokes the tail of the chain

- **GIVEN** the same chain `T1 → T2 → T3` as above
- **WHEN** a client posts presenting T2 (the middle, already-revoked
  row)
- **THEN** the system responds with HTTP 401 `REFRESH_TOKEN_REVOKED`
- **AND** T2 and T3 are revoked (T1 was already revoked and is left
  untouched; the chain walk starts at T2 and stops at T3 because T3's
  `replaced_by IS NULL`)
- **AND** an `audit_log` row with `event_type = 'TOKEN_REUSE_DETECTED'`
  and `metadata.chainLength = 2` is written

#### Scenario: Reuse of a most-recent revoked token revokes only that row

- **GIVEN** a chain `T1 → T2` where T1 is `revoked_at IS NOT NULL` and
  T2 (the head, with `replaced_by IS NULL`) has been independently
  revoked by logout, so the chain has no live row at the top
- **WHEN** a client posts presenting T2
- **THEN** the system responds with HTTP 401 `REFRESH_TOKEN_REVOKED`
- **AND** the chain walk finds no further rows to revoke (T2's
  `replaced_by IS NULL` is the terminator), so the UPDATE touches only
  T2's row
- **AND** an `audit_log` row with `event_type = 'TOKEN_REUSE_DETECTED'`
  and `metadata.chainLength = 1` is written (defensive handling of
  the otherwise-unreachable case)

#### Scenario: Chain revocation is atomic

- **GIVEN** the chain `T1 → T2 → T3` described above
- **WHEN** a client posts presenting T1 and the chain revocation runs
- **THEN** either ALL three rows end up revoked in a single database
  transaction, or NONE of them are revoked (the audit log row, the
  chain revoke, and the 401 response are committed together)
- **AND** no partial state is observable to a concurrent refresh
  attempt: a second presenter of T3 arriving mid-transaction sees T3
  either fully revoked or fully valid until the transaction commits

### Requirement: Logout Revokes the Current Refresh Token

The system SHALL set `revoked_at = now()` on the row matching the
presented refresh token cookie during logout (PRD line 690).

#### Scenario: Logout revokes only the presented token

- **WHEN** a client logs out while presenting refresh token T1
- **THEN** T1 is revoked
- **AND** any other still-valid refresh tokens issued to the same user
  in other sessions are NOT revoked (v1 keeps the simple behavior;
  per-session revocation is a v2 concern)

### Requirement: Scope-Aware Refresh

The system SHALL treat a refresh request as belonging to a specific
scope based on the request path (`/api/v1/auth/refresh` is COMPANY,
`/api/v1/platform/auth/refresh` is PLATFORM) and SHALL reject tokens
whose `user_scope` does not match the request path (PRD lines 668,
792).

#### Scenario: Company refresh endpoint rejects platform token

- **WHEN** a client posts to `/api/v1/auth/refresh` while presenting a
  refresh token whose `user_scope = 'PLATFORM'`
- **THEN** the system responds with HTTP 401
- **AND** `error.code` equals `REFRESH_TOKEN_INVALID` (or a more
  specific `REFRESH_TOKEN_SCOPE_MISMATCH`; see open_gaps)

#### Scenario: Platform refresh endpoint rejects company token

- **WHEN** a client posts to `/api/v1/platform/auth/refresh` while
  presenting a refresh token whose `user_scope = 'COMPANY'`
- **THEN** the system responds with HTTP 401 with the same code as the
  previous scenario
