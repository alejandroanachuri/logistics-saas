# Delta Spec: role-catalog

> Capability scope (from proposal): implicit in `tenant-onboarding` and
> `auth-company` / `auth-platform` — the `roles` table, its seed, and
> lookup by name + scope.
>
> Source PRD: lines 245-271 (roles table + 6-row seed), 261 (seed file
> `V7__seed_roles.sql`), 1488 (success criterion: 6 roles seeded).

## Purpose

The system SHALL persist a `roles` catalog that distinguishes
`COMPANY`-scope roles from `PLATFORM`-scope roles, SHALL seed the
catalog with the six v1 roles on application start, and SHALL resolve
role names to role ids deterministically when assigning roles to
`company_users` and `platform_users`.

## ADDED Requirements

### Requirement: Roles Table and Seed

The system SHALL persist the `public.roles` table with columns `(id
UUID PK, name VARCHAR(50) UNIQUE, scope VARCHAR(20), description
VARCHAR(255), created_at)` and SHALL seed it with the six rows from
PRD lines 264-270 by the end of the Flyway V7 migration:

- `COMPANY_ADMIN`, `COMPANY_OPERATOR`, `COMPANY_DRIVER`,
  `COMPANY_VIEWER` (scope `COMPANY`)
- `PLATFORM_ADMIN`, `PLATFORM_SUPPORT` (scope `PLATFORM`)

#### Scenario: Seed runs and is idempotent

- **WHEN** Flyway applies V7 on an empty database
- **THEN** exactly 6 rows exist in `public.roles`
- **AND** the role names are exactly the six listed above
- **AND** running the migration again (e.g. on a populated DB) does not
  violate the unique constraint on `name`

### Requirement: Scope Constraint

The system SHALL enforce at the database level that `roles.scope` is
either `'COMPANY'` or `'PLATFORM'` (PRD line 257 CHECK constraint).

#### Scenario: Invalid scope rejected by DB

- **WHEN** the application attempts to insert a row with `scope =
  'GLOBAL'`
- **THEN** the database rejects the insert with a constraint violation
- **AND** the application maps this to a domain error rather than
  silently swallowing it

### Requirement: Role Lookup by Name and Scope

The system SHALL provide a deterministic lookup that resolves
`(name, scope)` to a role id, used during registration, platform user
creation, and JWT issuance.

#### Scenario: Lookup COMPANY_ADMIN returns the seeded id

- **WHEN** the registration flow requests the id of the role
  `name = 'COMPANY_ADMIN'`, `scope = 'COMPANY'`
- **THEN** the lookup returns a non-null UUID
- **AND** querying `public.roles` with that id returns the row with
  `name = 'COMPANY_ADMIN'`, `scope = 'COMPANY'`

#### Scenario: Lookup of a non-existent role

- **WHEN** any code path requests a role by an unknown name
- **THEN** the lookup returns `Optional.empty()` (or throws a typed
  `RoleNotFoundException`); it MUST NOT silently substitute a default

### Requirement: First Admin User is Assigned COMPANY_ADMIN

The system SHALL assign the `COMPANY_ADMIN` role (resolved from the
`roles` table by `name` + `scope = 'COMPANY'`) to the first
`company_user` created during tenant registration.

#### Scenario: First user of a new tenant

- **WHEN** `POST /api/v1/auth/register` succeeds
- **THEN** the resulting `company_users.role_id` is the id of the
  `COMPANY_ADMIN` role
- **AND** this assignment is the only role assignment the registration
  flow performs (no additional roles are added in v1)

### Requirement: Roles Are Read-Only at Runtime

The system SHALL NOT expose a CRUD endpoint for roles in v1. The roles
table is a seed-managed catalog.

#### Scenario: No roles endpoint exists

- **WHEN** a client sends any HTTP request to a path matching
  `/api/v1/roles*` or `/api/v1/platform/roles*`
- **THEN** the system responds with HTTP 404 (no such controller
  mapping)
- **AND** the OpenAPI spec MUST NOT list any role CRUD endpoints
