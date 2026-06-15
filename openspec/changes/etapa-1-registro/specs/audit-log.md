# Delta Spec: audit-log

> Capability scope (from proposal): subset of cross-cutting security —
> writes to the `audit_log` table for the security events listed in PRD
> lines 921-930. (Retention, query endpoints, and alerting are out of
> scope for v1.)
>
> Source PRD: lines 368-389 (audit_log table), 921-930 (event types).

## Purpose

The system SHALL record security-relevant events to `public.audit_log`
so that authentication, registration, and abuse signals are
reconstructable from the database.

## ADDED Requirements

### Requirement: Audit Log Schema

The system SHALL persist `public.audit_log` with columns `(id UUID PK,
user_id UUID NULL, user_scope VARCHAR(20) NULL, tenant_id UUID NULL,
event_type VARCHAR(50) NOT NULL, ip_address VARCHAR(45) NULL,
user_agent VARCHAR(500) NULL, metadata JSONB NULL, created_at
TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW())` and the indexes from
PRD lines 386-388.

#### Scenario: Table exists with documented columns

- **WHEN** Flyway applies the V6 migration on an empty database
- **THEN** the `audit_log` table exists with all columns above
- **AND** the three indexes from PRD lines 386-388 are present

### Requirement: Audit Log Writers Emit the Eight v1 Event Types

The system SHALL write one `audit_log` row per occurrence of the
following event types, sourced from PRD lines 921-930:

- `TENANT_REGISTERED`
- `USER_LOGIN_SUCCESS`
- `USER_LOGIN_FAILED` (with `metadata.reason`)
- `USER_LOGOUT`
- `TOKEN_REFRESHED`
- `ACCOUNT_LOCKED` (with `metadata.lockedUntil`)
- `RATE_LIMIT_EXCEEDED` (with `metadata.endpoint`)
- `PLATFORM_USER_CREATED` (emitted by the platform user creation flow;
  see `platform-tenants` spec)

#### Scenario: TENANT_REGISTERED is recorded on successful registration

- **WHEN** `POST /api/v1/auth/register` succeeds
- **THEN** an `audit_log` row is inserted with
  `event_type = 'TENANT_REGISTERED'`, `user_scope = 'COMPANY'`,
  `tenant_id` = the new tenant id, `user_id` = the new admin user id,
  and `metadata` containing at least the tenant slug and the admin
  email

#### Scenario: USER_LOGIN_FAILED records the failure reason

- **WHEN** a login attempt returns `INVALID_CREDENTIALS`
- **THEN** an `audit_log` row is inserted with
  `event_type = 'USER_LOGIN_FAILED'`, `user_scope` matching the attempt
  (COMPANY or PLATFORM), `user_id` set when the user is identifiable
  (e.g. a valid slug + username that exists), and `metadata.reason`
  describing the failure class (e.g. `BAD_PASSWORD`,
  `USER_NOT_FOUND`, `TENANT_NOT_FOUND`)

#### Scenario: USER_LOGIN_SUCCESS records the user

- **WHEN** a login succeeds
- **THEN** an `audit_log` row is inserted with
  `event_type = 'USER_LOGIN_SUCCESS'`, `user_scope` matching the
  attempt, `user_id` and (for COMPANY) `tenant_id` set

#### Scenario: TOKEN_REFRESHED records the rotation

- **WHEN** a refresh succeeds
- **THEN** an `audit_log` row is inserted with
  `event_type = 'TOKEN_REFRESHED'`, `user_scope` matching the request
  path, `user_id` set, and `metadata` containing the truncated token
  prefix (NOT the raw token)

#### Scenario: RATE_LIMIT_EXCEEDED records the endpoint

- **WHEN** a rate-limit Bucket4j filter rejects a request
- **THEN** an `audit_log` row is inserted with
  `event_type = 'RATE_LIMIT_EXCEEDED'`, `ip_address` set, and
  `metadata.endpoint` set to the rejected path

### Requirement: No PII Beyond Documented Fields

The system SHALL NOT write raw passwords, raw refresh tokens, raw
JWTs, or other secret material to `metadata` or any other column
(PRD line 857: "Nunca loggear, nunca retornar al cliente, nunca
almacenar en claro").

#### Scenario: No secret material in audit metadata

- **WHEN** any of the eight events is recorded
- **THEN** the inserted `metadata` JSONB does not contain any of:
  raw `password`, raw refresh token UUID, raw JWT, raw cookie value,
  or BCrypt hash
