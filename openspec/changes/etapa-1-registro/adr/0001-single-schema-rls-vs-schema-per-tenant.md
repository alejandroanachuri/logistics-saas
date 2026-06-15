# ADR-0001: Single schema + Row-Level Security for multi-tenancy

- **Status**: Accepted (etapa-1-registro)
- **Date**: 2026-06-10
- **Deciders**: Backend lead, Architecture
- **Sources**: PRD §Arquitectura (lines 60-100), §RLS (lines 417-494), proposal decision D9/D10.

## Context

We need to isolate per-tenant data (tenants, company_users, refresh_tokens) in a
single PostgreSQL database. The candidates are:

1. **Schema-per-tenant** — each tenant gets its own Postgres `SCHEMA`. All
   tables are duplicated per tenant. Queries need schema switching.
2. **Database-per-tenant** — each tenant gets its own logical database. Highest
   isolation, highest operational cost.
3. **Single schema + Row-Level Security (RLS)** — all tenants share tables; a
   `app.current_tenant` session variable drives RLS policies.

## Decision

**Single schema + RLS** for v1.

A `TenantContext` ThreadLocal (resolved from JWT `tid` claim) drives an aspect
that emits `SET LOCAL app.current_tenant = '<uuid>'` on every transaction that
runs through the `companyDataSource` (role `app_user` with RLS enforced). A
separate `systemDataSource` (role `app_admin`, `BYPASSRLS`) handles
registration, login lookups, and refresh-token validation. A third
`platformDataSource` (role `app_platform`) handles cross-tenant endpoints.

## Alternatives considered

- **Schema-per-tenant**: rejected for v1. Pros: trivial `current_schema` switch,
  clearer migrations per tenant. Cons: a single Postgres instance can only hold
  ~10k schemas comfortably; migration fan-out is exponential; cross-tenant
  platform queries require dynamic SQL; connection-pool fragmentation.
- **Database-per-tenant**: rejected. Pros: max isolation. Cons: cost, no
  cross-tenant analytics, dev/prod provisioning is heavy, RLS is already enough
  for v1 risk profile.

## Consequences

- **Positive**: one migration set, one connection pool, one test schema. RLS
  provides defense in depth at the DB layer — a buggy query in `companyDataSource`
  cannot leak cross-tenant data even if the aspect fails.
- **Negative**: every query that touches a tenant-scoped table MUST go through
  `companyDataSource`. Code review must enforce this. The `RlsIntegrationIT`
  test is the gate; future developers who add a new query path must extend it.
- **Risk**: misnamed DataSource bean → bypass RLS silently. Mitigated by the
  `DataSourceRoutingAspect` (which keys on request path / JWT scope) and a
  future `DataSourceUsageInspector` test that asserts every `@Repository` is
  bound to a single DataSource.

## References

- PRD lines 88-89 (`TenantContext`, `Rls Aspect: SET LOCAL`)
- PRD lines 432-494 (RLS strategy, V8 SQL, 3 DataSources)
- PRD line 1461 (the gate test)
- Proposal decision D4 (SET LOCAL per request)
