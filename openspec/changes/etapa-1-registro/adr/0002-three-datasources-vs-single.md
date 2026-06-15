# ADR-0002: 3 DataSources with role-based bypass vs single DataSource with manual SET LOCAL

- **Status**: Accepted (etapa-1-registro)
- **Date**: 2026-06-10
- **Deciders**: Backend lead
- **Sources**: PRD lines 490-494, proposal decision D4, spec `multi-tenant-data-isolation`.

## Context

Postgres RLS relies on the **current database role** to decide which policies
apply. To enforce the design ("company users see only their tenant; platform
users see all; system ops bypass RLS"), the application must connect as
different Postgres roles for different request types.

The candidates:

1. **Single DataSource, manual `SET ROLE` per request** — connect as
   `app_admin`, then `SET LOCAL ROLE app_user` when handling company requests.
2. **Three DataSources, one per role** — `companyDataSource` (app_user),
   `systemDataSource` (app_admin, BYPASSRLS), `platformDataSource`
   (app_platform). A `DataSourceRouter` picks the right one.
3. **One DataSource per role, but a single Spring `DataSource` bean with
   `connection-init-sql` to `SET LOCAL ROLE`** — connection-level switching.

## Decision

**Three DataSources, picked by `DataSourceRoutingAspect`** (which keys on the
authenticated JWT scope or the request path prefix when no JWT is present).

- `companyDataSource` → user `app_user` (RLS enforced)
- `systemDataSource` → user `app_admin` (`BYPASSRLS`)
- `platformDataSource` → user `app_platform` (cross-tenant policies)

Routing table:

| Endpoint                                         | DataSource          |
|--------------------------------------------------|---------------------|
| `/api/v1/auth/register`                          | `systemDataSource`  |
| `/api/v1/auth/login` (credential lookup)         | `systemDataSource`  |
| `/api/v1/auth/refresh` (token validation)        | `systemDataSource`  |
| `/api/v1/tenants/me/slug-availability`           | `systemDataSource`  |
| `/api/v1/tenants/me/cuit-availability`           | `systemDataSource`  |
| `/api/v1/tenants/me/username-availability`       | `systemDataSource`  |
| `/api/v1/auth/me`, `/api/v1/auth/logout` (after JWT) | `systemDataSource` (audit) + `companyDataSource` (read) |
| `/api/v1/auth/me` (current-user read)            | `companyDataSource` |
| `/api/v1/tenants/me`                             | `companyDataSource` |
| `/api/v1/platform/**`                            | `platformDataSource` |

`RlsAspect` only fires on `companyDataSource` calls.

## Alternatives considered

- **Single DataSource + `SET ROLE`**: rejected. `SET LOCAL ROLE` is
  transaction-scoped — it would have to be re-issued on every transaction
  (Spring's `@Transactional`), and a missed `SET ROLE` would silently run as
  `app_admin` and bypass RLS. The failure mode is too quiet.
- **Connection-init-sql with `SET ROLE`**: rejected for the same reason
  (connection-level, breaks connection pooling and forces per-tenant
  connections).
- **Three DataSources keyed by Spring profile**: rejected. We need all three
  in the same JVM at runtime, not at build time.

## Consequences

- **Positive**: clean separation of concerns; the role in the JDBC URL is
  the contract, not a runtime mutation; a misuse of `companyDataSource` in
  `systemDataSource` context fails loudly at runtime (different connection,
  different role).
- **Negative**: three connection pools (Hikari) to size correctly. Total max
  pool size is the sum. v1 budgets 5+5+5 = 15 connections; PRD's reference
  `application.yml` says `maximum-pool-size: 10` — that will need to be lifted.
  Logged in `chore/bootstrap-repos` as a follow-up.
- **Operational**: three sets of credentials in the secret store. `app_migrations`
  user (with full DDL) is a fourth, only used by Flyway.

## References

- PRD lines 490-494
- Proposal decision D4
- Spec `multi-tenant-data-isolation`
- design.md §1.2
