# Delta Spec: platform-tenants

> Capability scope (from proposal): `platform-tenant-admin` — list and
> detail tenant endpoints accessible to `PLATFORM_ADMIN` and
> `PLATFORM_SUPPORT`, plus the platform user creation endpoint for
> `PLATFORM_ADMIN`.
>
> Source PRD: lines 804-848 (platform tenant endpoints), 1126-1184
> (platform admin policy, no UI in v1).

## Purpose

The system SHALL allow authenticated platform users with the
`PLATFORM_ADMIN` or `PLATFORM_SUPPORT` role to list and inspect tenants
across the entire fleet, and SHALL allow `PLATFORM_ADMIN` to create new
platform users.

## ADDED Requirements

### Requirement: List Tenants Endpoint

The system SHALL expose `GET /api/v1/platform/tenants` (platform-scope
cookie required) that returns a paginated list of tenants (PRD lines
804-828).

#### Scenario: Authenticated platform admin lists tenants

- **WHEN** a client sends a `GET /api/v1/platform/tenants` request with a
  valid `access_token` cookie whose JWT `scope` is `PLATFORM` and `role`
  is `PLATFORM_ADMIN` or `PLATFORM_SUPPORT`
- **THEN** the system responds with HTTP 200
- **AND** the body matches `{ data: [ { id, slug, legalName, cuit, status,
  createdAt, userCount } ], total, page, size }`

#### Scenario: Query params applied

- **WHEN** a client sends `?page=0&size=20&status=ACTIVE&search=mvr`
- **THEN** the response `data` array contains only tenants matching the
  filters, `page` is 0, `size` is 20, and `total` reflects the filtered
  count

#### Scenario: Company scope rejected

- **WHEN** a client sends a `GET /api/v1/platform/tenants` request while
  presenting a `COMPANY` `access_token` cookie
- **THEN** the system responds with HTTP 403
- **AND** `error.code` equals `FORBIDDEN_SCOPE`

#### Scenario: Unauthenticated request rejected

- **WHEN** a client sends the request without a `access_token` cookie
- **THEN** the system responds with HTTP 401

### Requirement: Tenant Detail Endpoint

The system SHALL expose `GET /api/v1/platform/tenants/{tenantId}` that
returns the full tenant profile and the list of its `company_users`
(PRD lines 830-832).

#### Scenario: Authenticated platform user reads tenant detail

- **WHEN** a client sends a `GET /api/v1/platform/tenants/{tenantId}`
  request with a valid platform `access_token` cookie
- **THEN** the system responds with HTTP 200
- **AND** the body contains the tenant profile (id, slug, legalName,
  commercialName, cuit, taxType, contactEmail, contactPhone, address,
  status, createdAt)
- **AND** a `companyUsers` array with one entry per user of that tenant
  (each entry: id, username, email, firstName, lastName, role, status,
  emailVerified, lastLoginAt, createdAt)

#### Scenario: Tenant not found

- **WHEN** a client requests a `tenantId` that does not exist
- **THEN** the system responds with HTTP 404
- **AND** `error.code` equals `TENANT_NOT_FOUND`

### Requirement: Platform User Creation Endpoint

The system SHALL expose `POST /api/v1/platform/users` (cookie with role
`PLATFORM_ADMIN` required) that creates a new `platform_users` row
(PRD lines 834-848, 1126-1184).

#### Scenario: PLATFORM_ADMIN creates a PLATFORM_SUPPORT user

- **WHEN** a `PLATFORM_ADMIN` posts `{ email, firstName, lastName,
  password, role: "PLATFORM_SUPPORT" }` with a valid password
- **THEN** the system responds with HTTP 201
- **AND** a new `platform_users` row is inserted with `status = 'ACTIVE'`,
  password BCrypt-hashed with strength 12, and `role_id` set to the id
  of the `roles` row where `name = 'PLATFORM_SUPPORT'`
- **AND** an `audit_log` row with `event_type = 'PLATFORM_USER_CREATED'`
  is written

#### Scenario: PLATFORM_SUPPORT cannot create users

- **WHEN** a `PLATFORM_SUPPORT` posts to `/api/v1/platform/users`
- **THEN** the system responds with HTTP 403
- **AND** `error.code` equals `FORBIDDEN_ROLE`

#### Scenario: Invalid role for platform user

- **WHEN** a `PLATFORM_ADMIN` posts a body with `role = "COMPANY_ADMIN"`
  (a COMPANY-scope role)
- **THEN** the system responds with HTTP 400 `VALIDATION_ERROR`
- **AND** `details.role` describes that only PLATFORM-scope roles are
  accepted

#### Scenario: Duplicate email rejected

- **WHEN** a `PLATFORM_ADMIN` posts a body with an `email` already
  present in `platform_users`
- **THEN** the system responds with HTTP 409
- **AND** `error.code` equals `EMAIL_ALREADY_TAKEN`
