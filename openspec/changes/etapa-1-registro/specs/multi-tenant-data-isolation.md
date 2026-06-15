# Delta Spec: multi-tenant-data-isolation

> Capability scope (from proposal): `rls-foundation` — three DataSources,
> `TenantContext` ThreadLocal, RLS aspect that emits
> `SET LOCAL app.current_tenant = ?` per request, plus the integration
> test that crosses tenants (PRD line 1461).
>
> Source PRD: lines 432-494 (RLS strategy, V8 SQL, 3 DataSources),
> 1509-1510 (DoD: RLS active and cross-tenant test fails).

## Purpose

The system SHALL enforce strict per-tenant data isolation at the
database level using PostgreSQL Row-Level Security on tenant-scoped
tables, driven by a session variable (`app.current_tenant`) that the
application sets per request through a dedicated `companyDataSource`.

## ADDED Requirements

### Requirement: Three DataSource Wiring

The system SHALL configure three independent DataSources (PRD lines
490-494):

- `companyDataSource` connects as `app_user` (RLS enforced, tenant-scoped
  reads/writes only)
- `systemDataSource` connects as `app_admin` (BYPASSRLS, used for
  registration, login lookups, slug/CUIT availability, refresh-token
  validation)
- `platformDataSource` connects as `app_platform` (cross-tenant reads,
  used for `/api/v1/platform/*` endpoints)

Migrations SHALL run under a separate user with full privileges (not
`app_user` / `app_admin` / `app_platform`).

#### Scenario: All three DataSources are healthy at startup

- **WHEN** the application starts with valid credentials for all three
  DB roles
- **THEN** the actuator `/actuator/health` reports `UP` for the data
  sources group
- **AND** no Flyway step fails

### Requirement: RLS Enabled on Tenant-Scoped Tables

The system SHALL enable Row-Level Security on `public.tenants`,
`public.company_users`, and `public.refresh_tokens` via the V8
migration (PRD lines 434-437) and SHALL create the policies named in
PRD lines 446-471 so that `app_user` rows are filtered by
`app.current_tenant`.

#### Scenario: app_user sees only its own tenant row

- **WHEN** the application executes `SELECT * FROM public.tenants` using
  `companyDataSource` with `app.current_tenant = <uuid-of-tenant-A>`
  and tenant A and tenant B both exist
- **THEN** exactly one row is returned: the row for tenant A
- **AND** the row for tenant B is filtered out by the RLS policy

#### Scenario: app_user cannot write across tenants

- **WHEN** the application attempts `UPDATE public.company_users SET
  email = '...' WHERE id = <user-id-of-tenant-B>` using
  `companyDataSource` with `app.current_tenant = <uuid-of-tenant-A>`
- **THEN** the update affects 0 rows (the RLS policy hides tenant B's
  row)

### Requirement: Tenant Context Resolution and SET LOCAL

The system SHALL resolve the tenant id from the authenticated JWT
(`tid` claim) and SHALL set the Postgres session variable with
`SET LOCAL app.current_tenant = '<uuid>'` on every transaction that
runs through `companyDataSource` (PRD lines 491, decision D4).

#### Scenario: SET LOCAL applied per request

- **WHEN** a request reaches a controller mapped to `companyDataSource`
  with a valid `COMPANY` access token
- **THEN** the system opens a transaction on `companyDataSource` and
  executes `SET LOCAL app.current_tenant = '<jwt-tid>'` before any user
  query
- **AND** `SET LOCAL` is auto-discarded at transaction commit/rollback,
  so a subsequent request on the same pooled connection cannot inherit
  the previous tenant id

#### Scenario: SET LOCAL is not emitted for platform endpoints

- **WHEN** a request reaches a controller mapped to `platformDataSource`
- **THEN** the system MUST NOT execute `SET LOCAL app.current_tenant`
  on that DataSource (platform users have no tenant)

### Requirement: Cross-Tenant Access Denied

The system SHALL deny any read or write that would let a
`companyDataSource` request access another tenant's data. This is the
gate test demanded by PRD line 1461.

#### Scenario: Direct cross-tenant lookup is denied

- **WHEN** a Testcontainers IT (or production code path) opens a
  transaction on `companyDataSource` with `app.current_tenant =
  <tenant-A-uuid>` and then issues `SELECT * FROM public.company_users
  WHERE id = <user-id-of-tenant-B>`
- **THEN** the result set is empty (PRD line 1461: "el segundo intento
  falla")
- **AND** no error is raised to the application; the row is simply
  filtered

#### Scenario: Direct cross-tenant update affects 0 rows

- **WHEN** a Testcontainers IT (or production code path) opens a
  transaction on `companyDataSource` with `app.current_tenant =
  <tenant-A-uuid>` and then issues `UPDATE public.company_users SET
  email = 'attacker@example.com' WHERE id = <user-id-of-tenant-B>`
- **THEN** the update affects 0 rows
- **AND** a subsequent `SELECT email FROM public.company_users WHERE id
  = <user-id-of-tenant-B>` (using a context with `app.current_tenant =
  <tenant-B-uuid>`) shows the original email unchanged

### Requirement: System DataSource Bypasses RLS

The system SHALL ensure that `systemDataSource` (used for registration
and login) can read and write tenant data across all tenants without
RLS interference (PRD line 442 `BYPASSRLS` role).

#### Scenario: Registration can create tenant + admin user atomically

- **WHEN** the registration flow inserts a new `tenants` row and a new
  `company_users` row using `systemDataSource`
- **THEN** both inserts succeed
- **AND** no `SET LOCAL` is required (BYPASSRLS)

### Requirement: RLS Kill Switch

The system SHALL honor an `app.rls.enabled` flag (default `true`):
when set to `false`, the system MUST log a WARN and skip emitting
`SET LOCAL` so that a misconfigured policy can be bypassed temporarily
in production (proposal rollback plan, line 138).

#### Scenario: RLS disabled in production for emergency

- **WHEN** an operator sets `APP_RLS_ENABLED=false` and restarts the
  application
- **THEN** the system logs `[WARN] RLS disabled via app.rls.enabled=false`
  at startup
- **AND** no `SET LOCAL app.current_tenant` is emitted
- **AND** `companyDataSource` queries return rows from all tenants
