# Proposal: `etapa-1-registro` — Company registration + admin login + platform admin login

## Intent

Build the first deliverable slice of the Logistics SaaS product: a self-service
company onboarding flow (3-step wizard) that creates a tenant and its first
admin user, followed by a `slug + username + password` login that issues
path-scoped JWT cookies; plus the symmetric platform-user login (email +
password) that lets the internal team authenticate. This slice validates the
core architectural bets — multi-tenant RLS in a single schema, Spring Security
with cookie-based JWT, path-scoped cookie isolation between company and
platform scopes, and a strict TDD build pipeline — before any shipping, courier
or dashboard work begins.

## Scope

### In Scope
- **Backend** (`backend/`):
  - Spring Boot app with web, data-jpa, security, validation, flyway, postgresql starters
  - Flyway migrations V1–V10 per PRD lines 1255-1265
  - Three DataSources (company / system / platform) with RLS roles `app_user`, `app_admin`, `app_platform`
  - REST endpoints under `/api/v1/auth/*`, `/api/v1/tenants/*`, `/api/v1/platform/*` (full list in PRD §API, lines 511-848)
  - JWT (jjwt 0.12.x, HS256, 15 min access / 7 day refresh) in `httpOnly` cookies
  - BCrypt strength 12 hashing
  - Slug / CUIT / password / username / email validators (incl. CUIT check-digit algorithm)
  - Account lockout (5 failed in 15 min → 30 min lock) and audit log writes
  - Rate limiting via Bucket4j for register / login / availability endpoints
  - Security headers, CORS, `EmailService` interface + `NoOpEmailService` (gated by `app.email.enabled=false`)
  - CLI runner `CreatePlatformUserCli` for bootstrap of the first `PLATFORM_ADMIN`
  - OpenAPI served at `/swagger-ui.html`
  - Global exception handler with PRD error envelope `{ error: { code, message, details } }`
- **Frontend** (`frontend/`):
  - Angular 21 standalone-component app with signals + new control flow + Tailwind 4
  - Routes: `/`, `/register` (3-step wizard), `/login`, `/dashboard` (placeholder) — PRD lines 1023-1035
  - Reactive typed forms with async validators (slug availability, CUIT availability, username availability, CUIT check digit)
  - `AuthService` + `authGuard` + `authInterceptor` + `errorInterceptor`
  - Signal-based `auth.store` and `tenant.store`
  - Cookie auth (no token storage in JS) — HttpClient with `withCredentials`
  - Vitest unit tests for services / validators / signal stores
  - Tailwind 4 PostCSS setup, accessibility (labels, aria-describedby, keyboard nav)

### Out of Scope
- All 16 items from PRD §"Fuera de alcance" (lines 39-56): couriers, shipments, tracking, WhatsApp/SMS, billing, ARCA, additional user management, logo upload, custom subdomain, plan changes, real dashboard data, 2FA, OAuth social, multi-language, tenant theming, public webhooks.
- **No UI for platform admin** in v1 (PRD lines 1178-1184): platform endpoints are accessible via curl/Postman only after CLI-seeded login.
- **No email sending in v1** — `NoOpEmailService` is the active bean; the wiring exists so v2 can flip `app.email.enabled=true`.
- **No password reset / forgot-password / change-password endpoints** — stubs in OpenAPI with `x-implementation-status: planned`.
- **No deploy** to Railway / Vercel / Neon in this change (infrastructure provisioning is a separate chore).
- **No Sentry wiring** beyond dependency declaration; runtime activation is part of deploy chore.

## Capabilities

> Contract with sdd-spec. Each capability becomes a `spec.md` (new) or a delta (modified).

### New Capabilities
- `tenant-onboarding`: self-service tenant + admin user creation, slug/CUIT availability, validation rules, RLS-aware persistence.
- `auth-company`: company-user login, refresh-token rotation, logout, `auth/me` and `tenants/me` profile reads, lockout, security headers, rate limiting.
- `auth-platform`: platform-user login (email+password), refresh, logout, `auth/me`, path-scoped cookies, no RLS.
- `platform-tenant-admin`: `GET /api/v1/platform/tenants` (list), `GET /api/v1/platform/tenants/{id}` (detail) for `PLATFORM_ADMIN` and `PLATFORM_SUPPORT`.
- `platform-user-cli`: `CreatePlatformUserCli` runner that bootstraps the first `PLATFORM_ADMIN` from CLI args, gated by app-admin DataSource.
- `rls-foundation`: row-level security setup, three DataSources, `TenantContext` ThreadLocal, RLS aspect that runs `SET LOCAL app.current_tenant = ?` per request.
- `security-headers`: HTTP security headers, CORS, error envelope, audit log writes for security events.
- `frontend-register-wizard`: 3-step Angular reactive form with live async validators and post-register success screen.
- `frontend-login`: `slug + username + password` form with error mapping, lockout copy, "forgot password" disabled with tooltip.
- `frontend-auth-shell`: `AuthService`, `authGuard`, `authInterceptor`, `errorInterceptor`, `auth.store`, `tenant.store`, route protection, dashboard placeholder.

### Modified Capabilities
- None — this is the first change; `openspec/specs/` is empty.

## Approach

**Phased delivery (in order).** Each phase is a coherent commit/PR unit and keeps the test suite green at the end.

1. **Bootstrap chore (separate `chore/bootstrap-repos` change, NOT in this proposal):** git init backend, add missing starters, install Playwright, add linters, add JaCoCo, add docker-compose, pin Spring Boot version decision.
2. **Migrations V1–V10** with full SQL from PRD §Data model (lines 161-415) and §RLS (lines 432-488). Test migrations apply from zero on a clean Postgres 16 (docker-compose) — that's the first test.
3. **Domain + validators** (no Spring web): `Tenant`, `Role`, `CompanyUser`, `PlatformUser`, `RefreshToken`, `AuditLog` entities; `SlugValidator`, `CuitValidator` (check digit), `PasswordValidator`, `UsernameValidator`. Unit tests cover each validator with a matrix of valid/invalid/reserved cases.
4. **3-DataSource wiring + RLS aspect:** `DataSourceConfig` builds `companyDataSource` (user `app_user`), `systemDataSource` (user `app_admin`, BYPASSRLS), `platformDataSource` (user `app_platform`). `RlsAspect` reads `TenantContext` and emits `SET LOCAL`. **Integration test (Testcontainers) that crosses two tenants is the gate.**
5. **JWT service + auth service core:** `JwtService` (issue, parse, validate), `PasswordEncoder` (BCrypt 12), `RefreshTokenService` (UUID + hash + rotation).
6. **Auth controllers (company):** register, login, refresh, logout, `auth/me`, `tenants/me`, slug/cuit availability. Each endpoint lands with a failing test first (request shape, status code, side effect).
7. **Auth controllers (platform):** same shape, different DataSource, path-scoped cookies. Tests assert cookie `Path` and that company-user JWTs are rejected here.
8. **Platform admin controllers + CLI:** `GET /api/v1/platform/tenants`, `GET /api/v1/platform/tenants/{id}`, `POST /api/v1/platform/users`, `CreatePlatformUserCli` runner.
9. **Cross-cutting security:** rate limiting (Bucket4j), lockout counter, audit log writers, security headers, CORS, global `@ControllerAdvice` mapping to the PRD error envelope.
10. **Frontend scaffold + routes + auth shell:** app config, routes, `AuthService`, `authGuard`, `authInterceptor`, `errorInterceptor`, signal stores. Unit tests for stores + service.
11. **Frontend register wizard (3 steps) + login + dashboard placeholder.** Each step component lands with a Vitest test for its form behavior and async validator wiring.
12. **Verification gate:** sdd-verify runs `./mvnw test` + `bun run test`, checks RLS-cross-tenant test fails second access, checks cookies have `Path` set correctly, checks OpenAPI spec generates.

## Key Decisions (resolving PRD ambiguities)

> Each decision is called out so sdd-spec can write the corresponding requirement without re-asking.

| # | Topic | Decision | PRD ref | Alternatives considered |
|---|-------|----------|---------|-------------------------|
| D1 | Spring Boot version | **Keep 4.0.6** (what's installed) — adapt code to 4.0 idioms. Do NOT downgrade to 3.5. | PRD line 109 (says 3.5) vs pom.xml (4.0.6) | (a) downgrade to 3.5.x — risks losing 4.0 features; (b) lock 3.5 in a fresh pom — wastes already-installed 4.0.6 |
| D2 | `EmailService` in v1 | **Interface + `NoOpEmailService` bean** registered via `@ConditionalOnProperty(name="app.email.enabled", havingValue="false", matchIfMissing=true)`. No SMTP, no real impl. Methods exist so callers compile. | PRD lines 934-959 | (a) only define the interface, no impl — fails to satisfy "preparado" since no call site can be wired; (b) full SMTP impl — out of scope |
| D3 | JWT secret in v1 | **Single static env var `JWT_SECRET`** (HS256, ≥256 bits, base64). No JWKS, no key rotation. Document as a v2 concern. | PRD lines 860-861 | (a) JWKS endpoint with key rotation — adds infra (key store, rotation job) for v1's risk profile; (b) RS256 with public key — overkill for single issuer |
| D4 | RLS session variable lifecycle | **`SET LOCAL` per request** (transaction-scoped) emitted from `RlsAspect` after tenant context is resolved from JWT. `SET LOCAL` auto-discards at commit/rollback — no leak between requests, no connection-pool hazard. | PRD lines 88-89, 491 | (a) connection-bound via Hikari `connection-init-sql` — would require one connection per tenant, breaks pooling; (b) per-statement — not supported by PG |
| D5 | BCrypt strength | **12** (matches PRD line 116 and line 856). Hard-coded as `BCryptPasswordEncoder(12)` — not env-tunable in v1. | PRD lines 116, 856 | (a) 10 — weaker, no PRD justification; (b) 14 — measurable latency hit on login, no PRD requirement |
| D6 | Platform user login UI in v1 | **NO UI**. Only backend endpoints + CLI bootstrap. Frontend routes for platform do not exist in v1. PRD-aligned. | PRD lines 1178-1184 | (a) build a `/platform/login` page — explicitly out per PRD; (b) reuse `/login` and branch by email — confusing UX, leaks intent |
| D7 | Docker Compose dev DB | **Postgres 16-alpine** at repo root, port 5432, volume `postgres_data`. Matches PRD lines 1402-1422 verbatim. App connects via `localhost:5432` with `app_user`/`app_admin`/`app_platform` created in an init script. | PRD lines 1402-1422 | (a) H2 in-memory — cannot validate RLS, fails IT gate; (b) external Neon even in dev — costs and network dep |
| D8 | PRD v1.0 leftover references (`users` table) | **Follow v1.1.** Use `company_users` + `platform_users` + `roles`. The line 1275 "users" reference is a stale convention doc — corrected in sdd-spec and sdd-design. | PRD lines 1275 (stale) vs lines 161-415 (correct) | (a) keep `users` — contradicts the rest of the PRD and v1.1 changelog; (b) rename to `users` later — migration churn |
| D9 | Package layout | **Use the corrected layout** in PRD lines 1239-1265 (per-domain: `auth/`, `tenant/`, `platform/`, `common/`, `config/`, `shared/`). Ignore the stale tree at lines 1190-1236 (PRD footnote confirms). | PRD lines 1238-1265 | (a) follow the stale tree — contradicts v1.1; (b) invent a new layout — violates "don't invent" rule |
| D10 | Testcontainers vs H2 | **Testcontainers + real Postgres 16** for ITs. RLS can only be exercised against real Postgres (H2 ignores RLS). | PRD lines 124, 1458-1463 | (a) H2 — RLS not enforced, fails the gate; (b) shared dev DB — flaky, not parallel-safe |

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `backend/pom.xml` | Modified | Add web, data-jpa, security, validation, oauth2-resource-server, flyway-core, flyway-database-postgresql, postgresql, jjwt, bucket4j, springdoc, sentry, mapstruct, lombok, spotless, jacoco |
| `backend/src/main/java/ar/com/logistics/**` | New | All packages per PRD lines 1240-1253 |
| `backend/src/main/resources/db/migration/V1__…V10__…sql` | New | 10 migration files per PRD lines 1255-1265 |
| `backend/src/main/resources/application.yml` + `application-dev.yml` + `application-prod.yml` | New | Per PRD lines 1306-1379 |
| `backend/src/test/java/**` | New | Unit + IT per PRD §Testing (lines 1450-1465) |
| `frontend/src/app/**` | New | Per PRD lines 977-1019 |
| `frontend/src/app/app.config.ts`, `app.routes.ts`, `app.component.ts` | New | Routing, interceptors, providers |
| `frontend/src/styles.css` + `postcss.config.js` | New | Tailwind 4 wiring per PRD lines 1095-1108 |
| `frontend/playwright.config.ts` + `e2e/**` | New | E2E per PRD lines 1476-1479 |
| `docker-compose.yml` (repo root) | New | Postgres 16 per PRD lines 1402-1422 |
| `docs/API.md`, `docs/ARCHITECTURE.md`, `docs/RUNBOOK.md`, `docs/ADR/` | New | From PRD §Entregables (line 1537) |
| `.github/workflows/backend-ci.yml`, `frontend-ci.yml` | New | From PRD line 1234-1235 |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| RLS misconfiguration leaks cross-tenant data | Medium | Mandatory Testcontainers IT that crosses 2 tenants (PRD line 1461). Code review checklist: every new query on a tenant-scoped table goes through `companyDataSource`. |
| Three-DataSource wiring confuses future contributors | Medium | Clear naming (`companyDataSource`, `systemDataSource`, `platformDataSource`) + ADR in `docs/ADR/0001-three-datasources.md`. |
| Spring Boot 4.0.6 vs PRD's 3.5.x — API drift | Medium | Pin & test in chore; if any starter blocks, document in ADR. |
| Bootstrap chore runs late and blocks all real tasks | High | Proposal calls out the chore as **blocking**; sdd-tasks must split it out before any feature task lands. |
| 400-line review budget blown by a single feature | Medium | sdd-tasks forecast + chained PRs per work-unit-commits skill. Backend is the highest-risk repo; frontend naturally slices per feature. |
| TDD drift on chore work | Low | Chore tasks explicitly exempt; behavior tasks carry their test in the same commit. |
| Email wiring triggers accidental sends | Low | `NoOpEmailService` is the default; no SMTP creds in v1. |
| OpenAPI spec drifts from actual endpoints | Low | springdoc-openapi generates from controllers; CI fails on missing schema. |

## Rollback Plan

- **Migrations:** each Flyway script is forward-only. Rollback = drop schema + re-apply from V1 (acceptable for v1 since no real tenants exist). Document `flyway clean` as dev-only.
- **Code:** revert by PR. Each phase lands as a self-contained, reviewable commit, so any single phase can be reverted without breaking the next.
- **Config:** `app.email.enabled` flag is the kill switch for email wiring. Rate-limit knobs are env-tunable.
- **Datasource roles:** if `app_platform` policy leaks, set `app.rls.enabled=false` (env flag) to fall back to plain lookups while the policy is fixed. This is logged at WARN.
- **Chore change** (`chore/bootstrap-repos`): pure additive — removing it leaves the repo in its original state.

## Dependencies

- `chore/bootstrap-repos` (blocking, separate change): git init backend, missing starters, linters, Playwright, JaCoCo, docker-compose, pinned versions.
- Postgres 16 available locally (docker-compose) and in CI.
- Bun ≥ 1.2.9, Node ≥ 20 (for Angular 21).
- JDK 21 (LTS).

## Success Criteria

**Functional (subset of PRD §Definition of Done, lines 1483-1503):**
- [ ] User can complete the 3-step wizard and see the post-register success screen
- [ ] User can log in with `slug + username + password` and land on dashboard placeholder
- [ ] `authGuard` redirects unauthenticated `/dashboard` access to `/login`
- [ ] Reserved slugs are rejected; duplicate slug / CUIT / username / email are rejected with the right 409 code
- [ ] Login returns generic `INVALID_CREDENTIALS` (no field disclosure)
- [ ] 5 failed logins in 15 min → 30 min lock, then 403 `ACCOUNT_LOCKED`
- [ ] Logout clears cookies and revokes the refresh token
- [ ] Refresh rotates (new token + old revoked)
- [ ] Access token expires at 15 min
- [ ] Platform user can log in with `email + password` and gets path-scoped cookies
- [ ] Platform user cannot hit `/api/v1/auth/me` or `/api/v1/tenants/me`
- [ ] Company user cannot hit `/api/v1/platform/*`
- [ ] Platform admin can list / detail tenants
- [ ] First company user in a registration has role `COMPANY_ADMIN`
- [ ] Six roles seeded on app start

**Technical:**
- [ ] `./mvnw test` green; `bun run test` green
- [ ] Coverage: backend services >80%, frontend services >70% (JaCoCo + vitest coverage)
- [ ] RLS IT crosses 2 tenants and fails the second access
- [ ] Flyway applies V1–V10 cleanly on empty Postgres
- [ ] OpenAPI accessible at `/swagger-ui.html`
- [ ] Rate limiting triggers on register / login
- [ ] Security headers present on every response
- [ ] `EmailService` interface + `NoOpEmailService` bean exist; `app.email.enabled=false` is the default

**Compliance (preparation, not activation):**
- [ ] `verification_token` generated and persisted on register
- [ ] `email_verified` field exists on `company_users`
- [ ] Privacy-policy link in wizard (URL placeholder OK)

## Review Workload Forecast

- Estimated changed lines (additions): **~6,500–8,500** across backend + frontend (Java entities/services/controllers + SQL + Angular components + tests).
- Backend dominates: 3 DataSources, RLS, JWT, refresh rotation, validators, controllers, 10 migrations, ITs.
- Frontend is moderate: 1 wizard (3 steps) + login + dashboard + stores + interceptors + a few E2E specs.
- **Risk: medium-high.** RLS and JWT are the most error-prone slices.
- **Chained PR recommended: YES.** Suggested chain (top of `sdd-tasks` will refine):
  1. `chore/bootstrap-repos` (no feature code)
  2. Migrations V1–V10 + entity skeletons
  3. Validators + 3-DataSource + RLS aspect + RLS IT
  4. JWT + password encoder + refresh-token service
  5. Auth controllers (company) + auth IT
  6. Auth controllers (platform) + platform IT
  7. Platform admin endpoints + CLI
  8. Security headers + rate limiting + audit + global exception handler
  9. Frontend scaffold + routes + stores + interceptors
  10. Frontend register wizard
  11. Frontend login + dashboard placeholder
  12. Playwright E2E + verify report
- 400-line budget guard: **medium risk** at proposal granularity; will become **low** once sdd-tasks slices per phase.

## Test Strategy

- **Strict TDD** (openspec/config.yaml `apply.tdd: true`): every behavior task lands as **failing test → green → refactor** in the same commit. No production code without a test in the same commit.
- **Chore tasks exempt from red-green** but must keep the suite green.
- **Backend tests:**
  - Unit (JUnit 5 + Mockito + AssertJ): validators (slug, CUIT check digit, password, username), `JwtService`, `RefreshTokenService`, `AuthService`, `EmailService` no-op behavior.
  - Integration (Testcontainers + real Postgres 16): register flow, login flow, refresh rotation, RLS cross-tenant (gate), rate limit triggers, platform-scope cookie rejection, company-vs-platform endpoint isolation.
- **Frontend tests:**
  - Unit (Vitest + jsdom): validators (slug, CUIT, password), `AuthService` (signals + HTTP), `authGuard`, stores.
  - Component (Vitest + Angular TestBed): each wizard step form, login form, error rendering.
  - E2E (Playwright): full register → logout → login → dashboard flow, slug/CUIT validation copy, lockout copy.
- Coverage gates: backend services >80%, frontend services >70%. Measured in `sdd-verify`.
- **No flaky shared DB** — every IT gets a fresh container.
