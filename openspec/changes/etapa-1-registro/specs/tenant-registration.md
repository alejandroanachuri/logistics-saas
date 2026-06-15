# Delta Spec: tenant-registration

> Capability scope (from proposal): `tenant-onboarding` — self-service tenant +
> first admin user creation, slug/CUIT availability checks, validation rules,
> RLS-aware persistence.
>
> Source PRD: lines 161-243 (tenants table), 245-271 (roles), 273-310 (company_users),
> 391-415 (reserved_slugs), 511-588 (POST /auth/register), 590-622 (slug/cuit-availability).

## Purpose

The system SHALL allow a prospective customer to self-register a new company
(`tenant`) and its first administrator user in a single transactional flow,
while enforcing uniqueness, format, and reserved-name rules.

## ADDED Requirements

### Requirement: Tenant Registration Endpoint

The system SHALL expose `POST /api/v1/auth/register` (no auth required) that
creates a new `tenant` and its first `company_user` in a single atomic
operation and returns the new tenant id, slug, and the admin user's id,
username, email, name, and role.

#### Scenario: Successful registration

- **WHEN** a client posts a valid body (company block with `slug`, `legalName`,
  `cuit`, `taxType`, `contactEmail`, address; admin block with `username`,
  `email`, `firstName`, `lastName`, `password`)
- **THEN** the system responds with HTTP 201
- **AND** the response body contains `tenant.{id, slug, legalName, cuit}`,
  `user.{id, username, email, firstName, lastName, role}` (where `role` =
  `COMPANY_ADMIN`), and `emailVerificationRequired: true`
- **AND** no `Set-Cookie` header is included (no auto-login in v1; PRD line 588)

#### Scenario: Validation error on invalid body

- **WHEN** a client posts a body that fails one or more format / required
  validators (e.g. `slug` too short, `email` malformed, missing
  `legalName`)
- **THEN** the system responds with HTTP 400
- **AND** the body matches the error envelope `{ "error": { "code":
  "VALIDATION_ERROR", "message": "...", "details": { "<field>": "..." } } }`
  with a `details` entry per failing field

#### Scenario: Slug already taken

- **WHEN** a client posts a registration whose `slug` is already present in
  the `tenants` table (excluding soft-deleted rows)
- **THEN** the system responds with HTTP 409
- **AND** `error.code` equals `SLUG_ALREADY_TAKEN`

#### Scenario: CUIT already registered

- **WHEN** a client posts a registration whose `cuit` is already present in
  the `tenants` table (excluding soft-deleted rows)
- **THEN** the system responds with HTTP 409
- **AND** `error.code` equals `CUIT_ALREADY_REGISTERED`

#### Scenario: Reserved slug rejected

- **WHEN** a client posts a registration whose `slug` is in the
  `reserved_slugs` catalog (PRD lines 397-414, e.g. `admin`, `api`, `www`,
  `login`, `register`)
- **THEN** the system responds with HTTP 409
- **AND** `error.code` equals `RESERVED_SLUG`

#### Scenario: Rate limit exceeded

- **WHEN** a client exceeds 5 `POST /api/v1/auth/register` requests from the
  same IP within 60 minutes (PRD line 901)
- **THEN** the system responds with HTTP 429
- **AND** `error.code` equals `RATE_LIMIT_EXCEEDED`

### Requirement: Slug Format and Reserved-Slug Validation

The system SHALL validate `tenant.slug` against the rules from PRD line 548:
length 2-12 characters; only lowercase ASCII letters and digits; first
character MUST be a letter; the value MUST NOT be present in
`public.reserved_slugs`.

#### Scenario: Slug matches all rules

- **WHEN** a client submits `slug = "mvr"` (3 chars, starts with letter, all
  lowercase letters, not in `reserved_slugs`)
- **THEN** the system accepts it and proceeds to the uniqueness check

#### Scenario: Slug with uppercase letters rejected

- **WHEN** a client submits `slug = "MVR"`
- **THEN** the system responds with HTTP 400 `VALIDATION_ERROR`
- **AND** `details.slug` describes the lowercase requirement

#### Scenario: Slug starting with a digit rejected

- **WHEN** a client submits `slug = "1mvr"`
- **THEN** the system responds with HTTP 400 `VALIDATION_ERROR`
- **AND** `details.slug` describes the "must start with a letter" rule

#### Scenario: Slug of length 1 or 13 rejected

- **WHEN** a client submits `slug = "m"` (length 1) or `"abcdefghijklm"`
  (length 13)
- **THEN** the system responds with HTTP 400 `VALIDATION_ERROR`
- **AND** `details.slug` describes the 2-12 range

### Requirement: CUIT Format and Check-Digit Validation

The system SHALL validate `tenant.cuit` as 11 digits (with or without
hyphens) and SHALL apply the Argentine CUIT/CUIL check-digit algorithm
(modulo 11 over the first 10 digits) to confirm the verifier digit is
correct.

#### Scenario: Valid CUIT with hyphens accepted

- **WHEN** a client submits `cuit = "30-71234567-8"` and the check-digit
  calculation yields 8 (PRD example, line 521)
- **THEN** the system accepts it and proceeds to the uniqueness check

#### Scenario: Valid CUIT without hyphens accepted

- **WHEN** a client submits `cuit = "30712356780"` and the check digit is
  correct
- **THEN** the system accepts it and persists it normalized to the 11-digit
  canonical form

#### Scenario: CUIT with bad check digit rejected

- **WHEN** a client submits `cuit = "30-71234567-0"` (wrong last digit)
- **THEN** the system responds with HTTP 400 `VALIDATION_ERROR`
- **AND** `details.cuit` describes the check-digit failure

#### Scenario: CUIT with non-digit chars rejected

- **WHEN** a client submits `cuit = "30-7123ABCD-8"`
- **THEN** the system responds with HTTP 400 `VALIDATION_ERROR`

### Requirement: Password Policy Validation

The system SHALL validate `admin.password` to be at least 8 characters long
and to contain at least one uppercase letter, one lowercase letter, and one
digit (PRD line 551).

#### Scenario: Password meets policy

- **WHEN** a client submits `password = "MiPassw0rd!Seguro"` (12 chars,
  uppercase, lowercase, digit)
- **THEN** the system accepts it and hashes it with BCrypt strength 12
  before persisting

#### Scenario: Password too short rejected

- **WHEN** a client submits `password = "Aa1!aa"` (6 chars)
- **THEN** the system responds with HTTP 400 `VALIDATION_ERROR`
- **AND** `details.password` describes the 8-character minimum

#### Scenario: Password missing uppercase rejected

- **WHEN** a client submits `password = "mypassw0rd!seguro"` (no uppercase)
- **THEN** the system responds with HTTP 400 `VALIDATION_ERROR`
- **AND** `details.password` describes the missing-uppercase rule

### Requirement: Username Format and Per-Tenant Uniqueness

The system SHALL validate `admin.username` to be 3-30 characters,
alphanumeric with `_`, `.`, and `-` allowed, lowercase, and unique per
tenant (PRD line 552, line 302 unique constraint).

#### Scenario: Username meets format and is unique

- **WHEN** a client submits `username = "admin"` for a new tenant and no
  `company_users` row in that tenant has that username
- **THEN** the system accepts it and persists it

#### Scenario: Username already taken in the same tenant

- **WHEN** a client registers a second `company_user` for an existing tenant
  with a `username` already used by the first user
- **THEN** the system responds with HTTP 409
- **AND** `error.code` equals `USERNAME_ALREADY_TAKEN` (semantic code;
  implementation may include it under `VALIDATION_ERROR.details` —
  open gap: confirm exact code shape, see open_gaps in summary)

### Requirement: First Admin User is COMPANY_ADMIN

The system SHALL assign the role `COMPANY_ADMIN` (from the seeded `roles`
table) to the first `company_user` created during registration (PRD lines
1489, 1502 success criteria).

#### Scenario: First user gets COMPANY_ADMIN

- **WHEN** a successful registration completes
- **THEN** the row inserted into `public.company_users` has its `role_id`
  set to the id of the `roles` row where `name = 'COMPANY_ADMIN'` and
  `scope = 'COMPANY'`

### Requirement: Verification Token Generation on Register

The system SHALL generate a UUID `verification_token` and persist it on the
new admin user with `verification_token_expires_at = now() + 24h`
(PRD line 585, compliance prep line 1522).

#### Scenario: Token generated and persisted

- **WHEN** a successful registration completes
- **THEN** `company_users.verification_token` is non-null
- **AND** `company_users.verification_token_expires_at` is approximately
  24 hours in the future
- **AND** the system MUST NOT send email in v1 (logs a TODO at WARN with
  the email and token)

### Requirement: Slug Availability Check Endpoint

The system SHALL expose `GET /api/v1/tenants/me/slug-availability?slug=...`
returning `{ slug, available, reason? }` (PRD lines 590-609).

#### Scenario: Slug is available

- **WHEN** a client queries with a slug that does not exist in `tenants`
  and is not in `reserved_slugs`
- **THEN** the system responds with HTTP 200
- **AND** `available` is `true` and `reason` is absent

#### Scenario: Slug is taken

- **WHEN** a client queries with a slug already present in `tenants`
- **THEN** the system responds with HTTP 200
- **AND** `available` is `false` and `reason` equals `SLUG_ALREADY_TAKEN`

### Requirement: CUIT Availability Check Endpoint

The system SHALL expose `GET /api/v1/tenants/me/cuit-availability?cuit=...`
returning `{ cuit, available, valid }` (PRD lines 611-622).

#### Scenario: CUIT valid and available

- **WHEN** a client queries with a syntactically valid CUIT that is not in
  `tenants`
- **THEN** the system responds with HTTP 200
- **AND** `valid` is `true` and `available` is `true`

#### Scenario: CUIT invalid format

- **WHEN** a client queries with a CUIT that fails the check-digit algorithm
- **THEN** the system responds with HTTP 200
- **AND** `valid` is `false` and `available` is `false`

### Requirement: Username Availability Check Endpoint

The system SHALL expose
`GET /api/v1/tenants/me/username-availability?slug={slug}&username={username}`
(no auth required) that returns a JSON object of shape
`{ slug, username, available, reason? }` (design §10 gap 6, PRD line
1053). The endpoint mirrors the shape of the slug-availability and
cuit-availability endpoints and exists so the registration wizard can
debounce a per-username live check during step 1 (wizard-registration,
design §5.3).

Both `slug` and `username` query parameters are REQUIRED. The
`username` uniqueness is per-tenant, so the lookup resolves the tenant
by `slug` first; if no tenant with that slug exists, the endpoint
responds with HTTP 200 and
`{ available: false, reason: "SLUG_NOT_FOUND" }` (the wizard can then
surface a slug-side error before re-asking the username). If the slug
resolves, the username is checked against `company_users` for that
tenant (case-insensitive) and the `reserved_usernames` catalog. The
`reason` field, when present, MUST be one of:

- `SLUG_NOT_FOUND` — no tenant matches the provided `slug`
- `USERNAME_ALREADY_TAKEN` — a non-deleted `company_users` row in that
  tenant already owns the requested username
- `USERNAME_RESERVED` — the username is in the seeded
  `reserved_usernames` catalog (e.g. `admin`, `root`, `system`,
  `support`, `platform`)

The endpoint is public, does not require an `access_token`, and is
counted under the same 30-req/min/IP Bucket4j budget as the other
availability endpoints (design §9, PRD line 904).

#### Scenario: Username is available

- **GIVEN** a tenant with `slug = "mvr"` exists and has no
  `company_users` row with `username = "facu"`
- **WHEN** a client calls
  `GET /api/v1/tenants/me/username-availability?slug=mvr&username=facu`
- **THEN** the system responds with HTTP 200
- **AND** the body is
  `{ "slug": "mvr", "username": "facu", "available": true }` with no
  `reason` field

#### Scenario: Username is already taken in the tenant

- **GIVEN** a tenant with `slug = "mvr"` exists and has a
  `company_users` row with `username = "facu"` (case-insensitive match)
- **WHEN** a client calls
  `GET /api/v1/tenants/me/username-availability?slug=mvr&username=facu`
- **THEN** the system responds with HTTP 200
- **AND** the body is
  `{ "slug": "mvr", "username": "facu", "available": false,
  "reason": "USERNAME_ALREADY_TAKEN" }`

#### Scenario: Slug does not resolve

- **WHEN** a client calls the endpoint with a `slug` that does not
  match any tenant in `tenants`
- **THEN** the system responds with HTTP 200
- **AND** the body is
  `{ "slug": "<input>", "username": "<input>", "available": false,
  "reason": "SLUG_NOT_FOUND" }`
- **AND** the system does NOT 404 the request — it returns 200 with a
  machine-readable reason so the wizard can show a slug-side error
  without changing response parsers
