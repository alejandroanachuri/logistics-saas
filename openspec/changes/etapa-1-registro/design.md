# Design: `etapa-1-registro`

> **Change**: Company registration + admin login + platform admin login
> **Stack**: Spring Boot 4.0.6 / Java 21 / Maven + Angular 21 / bun / Vitest 4 / Tailwind 4
> **Artifact source**: `sdd/etapa-1-registro/design`
> **Status**: ready for `sdd-tasks`

---

## 1. Architecture Overview

### 1.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Frontend (Angular 21, standalone, signals)                             │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐          │
│  │ / (public) │  │ /register  │  │ /login     │  │ /dashboard │          │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘          │
│        └───────────────┴───────────────┴───────────────┘                 │
│                          │                                              │
│                AuthStore/AuthService  HttpClient + withCredentials       │
│                          │ HTTPS + httpOnly cookies                     │
└──────────────────────────┼──────────────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Backend (Spring Boot 4.0.6, single JVM)                                │
│                                                                         │
│  ┌─ Servlet filter chain ───────────────────────────────────────────┐   │
│  │ SecurityHeadersFilter → RateLimitFilter → CorsFilter →           │   │
│  │ AuthenticationFilter (cookie JWT) → AuthorizationFilter          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│         │                                                               │
│         ▼ JWT (sub,tid,slug,role,scope)                                 │
│  ┌─ TenantContext (ThreadLocal<UUID>) ─┐                                │
│  │ RlsAspect ──► emits SET LOCAL        │                                │
│  └────────────────────┬────────────────┘                                │
│                       │                                                 │
│  ┌────────────────────┼────────────────────────┐                        │
│  │                    ▼                        │                        │
│  │  companyDataSource  systemDataSource  platformDataSource           │
│  │  (app_user)         (app_admin         (app_platform)               │
│  │   RLS ENABLED)      BYPASSRLS)         cross-tenant)                │
│  └─────────┬──────────────┬──────────────┬──────────┘                 │
│            │              │              │                              │
│            └──── Spring Data JPA (Hibernate 6.6) ─── Flyway 11 ──────  │
└─────────────────────────────────────────┬───────────────────────────────┘
                                          │ JDBC
                                          ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  PostgreSQL 16-alpine (single schema `public`, RLS on 3 tables)         │
│  roles: app_user, app_admin (BYPASSRLS), app_platform                   │
│  Tables: tenants, roles, company_users, platform_users,                 │
│          refresh_tokens, audit_log, reserved_slugs                      │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Three-DataSource Wiring (per request routing)

| DataSource bean         | DB user      | RLS behavior  | Used by                                                |
|-------------------------|--------------|---------------|--------------------------------------------------------|
| `companyDataSource`     | `app_user`   | ENFORCED      | `/api/v1/auth/*`, `/api/v1/tenants/*` after login      |
| `systemDataSource`      | `app_admin`  | BYPASSRLS     | Registration, login lookup, slug/cuit availability,    |
|                         |              |               | refresh-token validation, Flyway migrations           |
| `platformDataSource`    | `app_platform` | ENFORCED + cross-tenant policies | `/api/v1/platform/*`                  |

Routing rule (chosen by `@TargetDataSource` annotation or controller package):
- `auth/**`, `tenant/**` (excluding registration & availability) → `companyDataSource`
- `auth/register`, `tenants/me/slug-availability`, `tenants/me/cuit-availability`, `auth/login` (credential lookup), `auth/refresh` (token lookup) → `systemDataSource`
- `platform/**` → `platformDataSource`

**Implementation**: a `DataSourceRouter` (an `AbstractRoutingDataSource`) keyed by a request-scoped `DataSourceContext` (ThreadLocal). Spring AOP aspect `DataSourceRoutingAspect` sets the key after `AuthenticationFilter` resolves the scope from the JWT cookie (or detects path prefix `/api/v1/platform`).

The `RlsAspect` is separate: it only fires on `companyDataSource` calls and emits `SET LOCAL app.current_tenant = ?` from `TenantContext`. `systemDataSource` skips the aspect. `platformDataSource` skips the aspect.

### 1.3 TenantContext Flow

```
HTTP request
   │
   ▼
AuthenticationFilter (cookie JWT)
   │ if valid: parses claims → sets TenantContext.set(jwt.tid, scope)
   │ if invalid/expired: 401 UNAUTHENTICATED
   ▼
DataSourceRoutingAspect
   │ reads TenantContext → sets DataSourceContext("company" | "platform" | "system")
   ▼
Controller
   │ invokes Service → Repository
   ▼
RlsAspect (only when DataSourceContext == "company")
   │ @Transactional opens tx on companyDataSource
   │ emits SQL: SET LOCAL app.current_tenant = '<uuid>'
   │ repository call executes under RLS
   ▼
@Transactional commit
   │ SET LOCAL auto-discarded (PRD line 491)
   │ TenantContext.clear() in @AfterReturning (auto-cleanup)
   ▼
HTTP response
```

Auto-cleanup is a `HandlerInterceptor` (`TenantContextInterceptor`) calling `TenantContext.clear()` in `afterCompletion`. The interceptor also runs in `preHandle` to ensure clean state on entry.

### 1.4 Cookie Scoping

| Scope    | access_token `Path` | refresh_token `Path`       | TTL  |
|----------|---------------------|----------------------------|------|
| COMPANY  | `/api/v1`           | `/api/v1/auth`             | 15m / 7d |
| PLATFORM | `/api/v1/platform`  | `/api/v1/platform/auth`    | 15m / 7d |

Browsers DO NOT attach the wrong cookie because paths differ (PRD line 892). The server also validates `JWT.scope` against the path prefix; mismatch → 403 `FORBIDDEN_SCOPE`.

---

## 2. Package Layout (final)

### 2.1 Backend — `backend/src/main/java/ar/com/logistics/`

```
ar.com.logistics
├── LogisticsApplication.java              — @SpringBootApplication main
├── common/
│   ├── exception/                         — BusinessException, GlobalExceptionHandler (@ControllerAdvice)
│   │   └── ApiError (record: code, message, details)
│   ├── validation/                        — SlugValidator, CuitValidator (mod-11), PasswordValidator, UsernameValidator
│   ├── audit/                             — AuditLogger (writes audit_log via systemDataSource)
│   └── response/                          — PageResponse<T> wrapper
├── config/
│   ├── SecurityConfig                     — filter chain, CORS, security headers
│   ├── DataSourceConfig                   — 3 Hikari DataSources + routing
│   ├── JdbcConfig                         — one EntityManager per DataSource
│   ├── OpenApiConfig                      — springdoc + SecurityScheme for cookies
│   ├── AppProperties                      — @ConfigurationProperties for app.*
│   └── RateLimitConfig                    — Bucket4j buckets per endpoint
├── tenant/
│   ├── TenantContext                      — ThreadLocal<UUID> tenantId + scope
│   ├── DataSourceContext                  — ThreadLocal<String> dsKey
│   ├── RlsAspect                          — @Aspect emits SET LOCAL
│   ├── DataSourceRoutingAspect            — @Aspect chooses DataSource per scope
│   ├── TenantContextInterceptor           — HandlerInterceptor clears ThreadLocals
│   └── domain/                            — Tenant, Address (embedded), TaxType enum
├── shared/
│   └── BaseEntity                         — @MappedSuperclass with id, *_at, *_by
├── auth/                                  — company user auth (PRD lines 1240-1246)
│   ├── controller/                        — AuthController, TenantAvailabilityController
│   ├── service/                           — AuthService, SlugAvailabilityService, CuitAvailabilityService
│   ├── domain/                            — CompanyUser, RefreshToken, UserStatus enum
│   ├── repository/                        — CompanyUserRepository, RefreshTokenRepository
│   ├── dto/                               — request/response records
│   └── security/                          — JwtService, RefreshTokenService, AuthenticationFilter, PasswordEncoderConfig
├── tenant/api/                            — /api/v1/tenants/* (POST register + GET me + availability)
│   ├── controller/                        — TenantController
│   ├── service/                           — TenantService, TenantRegistrationService
│   ├── repository/                        — TenantRepository
│   └── dto/                               — RegisterRequest, RegisterResponse, TenantProfileResponse, AddressDto
└── platform/                              — /api/v1/platform/* (PRD lines 1248-1252)
    ├── auth/                              — PlatformAuthController, PlatformAuthService
    ├── controller/                        — PlatformTenantController, PlatformUserController
    ├── service/                           — PlatformTenantService, PlatformUserService
    ├── domain/                            — PlatformUser
    ├── repository/                        — PlatformUserRepository
    ├── cli/                               — CreatePlatformUserCli (CommandLineRunner)
    └── dto/                               — records
```

`src/main/resources/`:
```
db/migration/
  V1__create_tenants.sql
  V2__create_roles.sql
  V3__create_company_users.sql
  V4__create_platform_users.sql
  V5__create_refresh_tokens.sql
  V6__create_audit_log.sql
  V7__seed_roles.sql
  V8__enable_rls_and_create_roles.sql
  V9__seed_reserved_slugs.sql
  V10__seed_provinces_reference.sql       (idempotent reference data for AR provinces)
application.yml
application-dev.yml
application-prod.yml
logback-spring.xml
```

`src/test/java/ar/com/logistics/`:
```
common/validation/      — *ValidatorTest (unit, JUnit 5 + AssertJ)
auth/service/           — AuthServiceTest, JwtServiceTest, RefreshTokenServiceTest (Mockito)
auth/controller/        — AuthControllerIT, TenantAvailabilityControllerIT (Testcontainers)
platform/cli/           — CreatePlatformUserCliTest
rls/                    — RlsIntegrationIT (gate test, Testcontainers)
ref/                    — ReferenceDataControllerIT (provinces)
```

### 2.2 Frontend — `frontend/src/app/`

```
core/
├── auth/
│   ├── auth.service.ts            — login, logout, refresh, register (typed Observables)
│   ├── auth.guard.ts              — CanActivateFn (functional)
│   ├── auth.interceptor.ts        — HttpInterceptorFn setting withCredentials
│   ├── auth.types.ts              — LoginRequest/Response, RegisterRequest/Response, AuthUser
│   └── auth.store.ts              — Injectable w/ signal<AuthUser|null> + computed isAuthenticated
├── http/
│   ├── api.service.ts             — typed wrapper over HttpClient
│   ├── error.interceptor.ts       — HttpInterceptorFn: envelope mapping, 401→refresh→retry
│   ├── api-error.ts               — typed ApiError class
│   └── provinces.service.ts       — GET /api/v1/reference/provinces
├── tenant/
│   ├── tenant.service.ts          — GET /api/v1/tenants/me
│   ├── tenant.types.ts            — Tenant, Address
│   └── tenant.store.ts            — signal<Tenant|null>
└── platform/                      — empty in v1 (no UI per PRD lines 1178-1184)

features/
├── home/home.component.ts         — landing placeholder
├── register/
│   ├── register.component.ts      — CDK Stepper container
│   ├── steps/company-step.component.ts
│   ├── steps/admin-step.component.ts
│   ├── steps/confirmation-step.component.ts
│   ├── services/register.service.ts
│   └── register.types.ts          — typed form controls, payloads
├── login/login.component.ts
└── dashboard/dashboard.component.ts (placeholder, reads stores only)

shared/
├── components/
│   ├── ui/                        — button, input, alert, form-field (standalone)
│   └── layout/
│       ├── public-layout.component.ts
│       └── authenticated-layout.component.ts
├── validators/                    — slugValidator, cuitValidator, passwordValidator, slugAvailableAsync, cuitAvailableAsync, usernameAvailableAsync
└── data/provinces.ts              — static 24 codes (also validated against backend /reference/provinces)

app.config.ts                      — provideHttpClient(withInterceptors([auth, error])), provideRouter, provideZonelessChangeDetection
app.routes.ts                      — Routes
app.component.ts                   — <router-outlet>
main.ts                            — bootstrapApplication(AppComponent, appConfig)
styles.css                         — @import "tailwindcss"
```

---

## 3. Database Schema (final)

10 Flyway migrations; all use `app_migrations` user (superuser) for DDL.

| File                                       | Created by    | Creates                                                  | Used by                              |
|--------------------------------------------|---------------|----------------------------------------------------------|--------------------------------------|
| `V1__create_tenants.sql`                   | `app_migrations` | `public.tenants` + 3 indexes + tax/status CHECKs       | both DataSources                     |
| `V2__create_roles.sql`                     | `app_migrations` | `public.roles` + scope CHECK                            | both DataSources                     |
| `V3__create_company_users.sql`            | `app_migrations` | `public.company_users` + 3 indexes + status CHECK + unique (tenant,username) and (tenant,email) | `companyDataSource`, `systemDataSource` (registration) |
| `V4__create_platform_users.sql`            | `app_migrations` | `public.platform_users` + email unique + status CHECK  | `platformDataSource`                 |
| `V5__create_refresh_tokens.sql`            | `app_migrations` | `public.refresh_tokens` + 3 indexes + user_scope CHECK | `systemDataSource`                   |
| `V6__create_audit_log.sql`                 | `app_migrations` | `public.audit_log` + 3 indexes + user_scope CHECK       | `systemDataSource`                   |
| `V7__seed_roles.sql`                       | `app_migrations` | INSERT 6 roles (4 COMPANY + 2 PLATFORM)                 | startup                              |
| `V8__enable_rls_and_create_roles.sql`      | `app_migrations` | ALTER TABLE ENABLE RLS, CREATE ROLE, CREATE POLICY, GRANT | runtime                              |
| `V9__seed_reserved_slugs.sql`              | `app_migrations` | INSERT 15 reserved slugs                                | runtime                              |
| `V10__seed_provinces_reference.sql`        | `app_migrations` | INSERT 24 AR provinces into `public.provinces` (id code PK, name) | runtime                              |

### 3.1 RLS Policies (V8 verbatim from PRD lines 446-487)

| Role         | Table             | Policy                          | SELECT | INSERT | UPDATE | DELETE |
|--------------|-------------------|----------------------------------|--------|--------|--------|--------|
| `app_user`   | `tenants`         | `tenants_isolation`              | ✓      |        |        |        |
| `app_user`   | `company_users`   | `company_users_isolation`        | ✓      |        |        |        |
| `app_user`   | `refresh_tokens`  | `refresh_tokens_isolation`       | ✓      |        |        |        |
| `app_admin`  | (BYPASSRLS)       | —                                | ✓ all  | ✓ all  | ✓ all  | ✓ all  |
| `app_platform` | `tenants`       | `tenants_platform_all`           | ✓      |        |        |        |
| `app_platform` | `company_users` | `company_users_platform_read`    | ✓      |        |        |        |
| `app_platform` | `platform_users` | `platform_users_self`          | ✓      |        |        |        |

GRANT matrix (V8):
- `app_user`: SELECT/INSERT/UPDATE/DELETE on `tenants`, `company_users`, `refresh_tokens`; SELECT on `roles`; USAGE on sequences
- `app_admin`: ALL on all tables (BYPASSRLS)
- `app_platform`: SELECT/INSERT/UPDATE/DELETE on `tenants`, `company_users`, `platform_users`, `refresh_tokens`, `audit_log`, `roles`

### 3.2 Indexes (recap from PRD)

- `tenants`: unique on `slug`, unique on `cuit`, partial index on `deleted_at IS NULL`
- `company_users`: `(tenant_id)`, `(role_id)`, partial `(email_verified = FALSE)`
- `platform_users`: `(role_id)`, unique on `email`
- `refresh_tokens`: `(user_id)`, partial `(tenant_id IS NOT NULL)`, `(expires_at)`
- `audit_log`: composite `(tenant_id, created_at DESC)` partial, `(user_id)` partial, `(event_type)`
- `provinces`: PK on `code`, unique on `name`

---

## 4. Backend Module Design

> No Java code — only signatures, contracts, DTO shapes. Package paths from §2.1.

### 4.1 Capability: `tenant-registration`

**Controller**: `TenantController`
- `POST /api/v1/auth/register` → `register(@Valid @RequestBody RegisterRequest): RegisterResponse` (HTTP 201)
- `GET /api/v1/tenants/me/slug-availability?slug={s}` → `SlugAvailabilityResponse` (HTTP 200)
- `GET /api/v1/tenants/me/cuit-availability?cuit={c}` → `CuitAvailabilityResponse` (HTTP 200)
- `GET /api/v1/tenants/me` → `TenantProfileResponse` (HTTP 200) — same controller, different capability

`RegisterRequest` (record):
```text
company: CompanyDto { legalName, commercialName?, cuit, taxType, slug, contactEmail,
                      contactPhone?, address: AddressDto }
admin:   AdminDto   { username, email, firstName, lastName, password }
```

`RegisterResponse`:
```text
tenant: { id, slug, legalName, cuit }
user:   { id, username, email, firstName, lastName, role: "COMPANY_ADMIN" }
emailVerificationRequired: true
```

`AddressDto`:
```text
country (default "AR"), province (one of 24 codes), city, line, number,
floor?, apartment?, postalCode
```

**Service**: `TenantRegistrationService`
- `@Transactional` on `systemDataSource` (BYPASSRLS)
- Sequence: slug validator → CUIT validator → password validator → username validator → uniqueness checks (slug, CUIT, email, username) → BCrypt hash → INSERT tenant → INSERT company_user (role_id = roles.CID for COMPANY_ADMIN) → INSERT verification_token (UUID, 24h) → `AuditLogger.write(TENANT_REGISTERED, ...)` → returns DTO
- Throws: `ValidationException` (400), `SlugAlreadyTakenException` (409), `CuitAlreadyRegisteredException` (409), `ReservedSlugException` (409), `EmailAlreadyTakenException` (409), `UsernameAlreadyTakenException` (409)
- Calls `EmailService.sendVerificationEmail(...)` (no-op in v1)

**Repositories** (Spring Data JPA, all on `systemDataSource`):
- `TenantRepository extends JpaRepository<Tenant, UUID>`, `findBySlug(String)`, `findByCuit(String)`
- `CompanyUserRepository extends JpaRepository<CompanyUser, UUID>`, `findByTenantIdAndUsername(UUID, String)`, `findByTenantIdAndEmail(UUID, String)`

**Domain entities**:
- `Tenant` extends `BaseEntity` (id UUID, slug VARCHAR(12), legalName, commercialName?, cuit VARCHAR(13), taxType enum, contactEmail, contactPhone?, address @Embedded, status enum, *_at, *_by, deletedAt?)
- `CompanyUser` extends `BaseEntity` (id, tenantId UUID, roleId UUID, username, email, firstName, lastName, passwordHash, status enum, emailVerified Boolean, emailVerifiedAt?, verificationToken UUID?, verificationTokenExpiresAt?, lastLoginAt?, failedLoginAttempts int, lockedUntil?, *_at, *_by, deletedAt?)
- `equals/hashCode`: identity-by-id (UUID PK), no field-based equality

**Cross-cutting**:
- `AuthenticationFilter` is not involved (registration is anonymous)
- `DataSourceRoutingAspect` keys `systemDataSource` for `/api/v1/auth/register` (path-based fallback when no JWT present)
- `AuditLogger` writes to `audit_log` via `systemDataSource` with `user_scope = 'COMPANY'`

### 4.2 Capability: `company-user-auth`

**Controller**: `AuthController`
- `POST /api/v1/auth/login` → `login(@Valid @RequestBody LoginRequest): LoginResponse` + Set-Cookie × 2
- `POST /api/v1/auth/refresh` → `refresh(): LoginResponse` + Set-Cookie × 2 (reads cookies only, no body)
- `POST /api/v1/auth/logout` → `logout(): void` (HTTP 204) + Set-Cookie clear × 2
- `GET /api/v1/auth/me` → `me(): AuthMeResponse`

`LoginRequest`: `{ slug, username, password }`
`LoginResponse`: `{ user: { id, tenantId, tenantSlug, username, email, firstName, lastName, role, scope: "COMPANY" }, expiresIn: 900 }`

**Service**: `AuthService` (orchestrator), `LoginService` (credential resolution), `JwtService` (issue/parse), `RefreshTokenService` (issue/rotate/validate)
- `LoginService.authenticate(slug, username, password)`: lookup tenant by slug (`systemDataSource` BYPASSRLS) → if missing, increment nothing, throw `InvalidCredentialsException` (generic) and `AuditLogger.write(USER_LOGIN_FAILED, reason: TENANT_NOT_FOUND)`; lookup user by (tenantId, username); if missing, same path with `reason: USER_NOT_FOUND`; BCrypt-match password; if mismatch, `failedLoginAttempts++` → if reaches 5, set `lockedUntil = now + 30min` + `AuditLogger.write(ACCOUNT_LOCKED, metadata.lockedUntil)`; if locked, throw `AccountLockedException` with `minutesRemaining`; if disabled, throw `AccountDisabledException`. On success: reset counter, set `lastLoginAt`, issue JWT, issue refresh, audit success, return DTO
- `RefreshTokenService.rotate(presentedToken, scope)`: BCrypt-match against all rows with `user_scope = scope` and `revoked_at IS NULL` and `expires_at > now`. If found, set `revoked_at = now()`, issue new token, set `replaced_by` on new row. If row was already revoked (reuse detection per gap 3), see §10.
- `JwtService.issueAccessToken(user, tenant?, role, scope)`: jjwt 0.12 `Jwts.builder().subject(uid).issuer("logistics-saas").audience().add("logistics-saas-web").claim("scope", scope).claim("role", roleName).issuedAt(...).expiration(now+15min).signWith(secretKey, Jwts.SIG.HS256).compact()`
- `JwtService.parseAndValidate(jwsString)`: `Jwts.parser().verifyWith(secretKey).requireIssuer("logistics-saas").requireAudience("logistics-saas-web").build().parseSignedClaims(jws)` → returns a `JwtClaims` record

**AuthenticationFilter** (`OncePerRequestFilter`):
- On `/api/v1/auth/**` and `/api/v1/tenants/**` (excluding register and availability), reads `access_token` cookie, calls `JwtService.parseAndValidate`, sets `SecurityContextHolder` and `TenantContext`
- If missing/invalid: returns 401 `UNAUTHENTICATED` (resolved gap 5)
- If valid but `scope = PLATFORM`: returns 403 `FORBIDDEN_SCOPE` (resolved gap 2)

**Cross-cutting**:
- `PasswordEncoderConfig` exposes `BCryptPasswordEncoder(12)` bean (gap: hard-coded, D5)
- `JwtService` is shared by both auth flows (singleton)
- `AuditLogger` is injected into `AuthService`

### 4.3 Capability: `tenant-me`

`TenantController.me()` (read the JWT `tid` claim, NEVER a query parameter) → fetches tenant from `companyDataSource` (RLS filtered automatically) → maps to `TenantProfileResponse`

`TenantProfileResponse`: matches `GET /tenants/me` from PRD lines 700-722.

### 4.4 Capability: `platform-user-auth`

**Controller**: `PlatformAuthController`
- `POST /api/v1/platform/auth/login` → `login(@Valid @RequestBody PlatformLoginRequest): PlatformLoginResponse` + Set-Cookie × 2 (Path `/api/v1/platform` and `/api/v1/platform/auth`)
- `POST /api/v1/platform/auth/refresh`, `POST /api/v1/platform/auth/logout`, `GET /api/v1/platform/auth/me`

**Service**: `PlatformAuthService` — mirror of `AuthService` without tenant concept; same lockout rule (gap 4); same refresh rotation; scope-specific cookie path.

`PlatformJwtService` uses the same `JwtService` (shared) but issues claims without `tid`/`slug`.

### 4.5 Capability: `platform-tenants`

**Controller**: `PlatformTenantController`, `PlatformUserController`
- `GET /api/v1/platform/tenants?page&size&status&search` → `PageResponse<TenantListItemResponse>`
- `GET /api/v1/platform/tenants/{tenantId}` → `TenantDetailResponse` (404 `TENANT_NOT_FOUND` if missing)
- `POST /api/v1/platform/users` → `PlatformUserResponse` (HTTP 201)

**Service**: `PlatformTenantService`, `PlatformUserService` — operate on `platformDataSource`, return paginated views with `userCount` derived from a JOIN.

### 4.6 Capability: `role-catalog`

**Repository**: `RoleRepository extends JpaRepository<Role, UUID>` with `findByNameAndScope(name, scope): Optional<Role>`.
- Implemented in `auth/repository` package (single roles table read by both flows).
- Seeded by V7; lookup is `findByNameAndScope("COMPANY_ADMIN", "COMPANY")` in `TenantRegistrationService` and `RoleService.assignFirstAdmin(tenantId)`.
- No CRUD endpoint (gap requirement: 404 for `/api/v1/roles*`).

### 4.7 Capability: `multi-tenant-data-isolation`

Already covered in §1.2 and §1.3. The three pieces:
- `DataSourceConfig` builds the three `HikariDataSource` beans
- `DataSourceRouter` (`AbstractRoutingDataSource`) with a `Map<Object, DataSource>` keyed by `company`/`system`/`platform`
- `DataSourceRoutingAspect` (around `@Service` methods annotated with `@UseDataSource(...)`) sets `DataSourceContext.set(key)` and clears in `finally`
- `RlsAspect` (around `@Transactional` methods on `companyDataSource`) opens a tx and executes `SET LOCAL app.current_tenant = ?` first

**Gate test**: `RlsIntegrationIT` (Testcontainers Postgres 16) — creates 2 tenants, sets `app.current_tenant = A`, attempts to read tenant B's user, asserts 0 rows.

### 4.8 Capability: `refresh-token-rotation`

`RefreshTokenService` (singleton, systemDataSource) — methods:
- `issue(userId, scope, tenantId?)` → returns raw UUID; persists row with `token_hash = BCrypt(uuid)`
- `validateAndRotate(presentedToken, expectedScope)` → resolves row, validates scope, marks `revoked_at`, issues replacement, returns new `(rawUuid, row)`. Reuse detection per §10.
- `revoke(presentedToken)` → sets `revoked_at`
- The truncated prefix (first 8 chars of UUID, e.g. `tok_abc12345`) is logged in `audit_log.metadata.tokenPrefix` only.

### 4.9 Capability: `account-lockout-and-rate-limit`

**Lockout state machine**:
- `failedLoginAttempts` is cumulative. Each failed login: `++`. On 5th: `lockedUntil = now + 30min`, audit log emitted, counter preserved at 5 (so the count survives the window — see gap 4 resolution).
- On success: counter reset to 0, `lockedUntil = null`.
- On any attempt while `lockedUntil > now`: throw `AccountLockedException` with `details.minutesRemaining`; counter NOT incremented (locked attempts do not re-arm).
- On lockout expiry (`lockedUntil <= now`): next failed attempt starts fresh from 0; success path also clears `lockedUntil`.

`RateLimitFilter` (Servlet filter, after `SecurityHeadersFilter`):
- Bucket4j in-memory `ConcurrentHashMap<String, Bucket>` keyed by `(clientIp, endpoint)`.
- Endpoints and budgets per PRD lines 901-904:
  - `POST /api/v1/auth/register`: 5 / hour / IP
  - `POST /api/v1/auth/login`, `POST /api/v1/platform/auth/login`: 10 / min / IP
  - `GET /api/v1/tenants/me/slug-availability`, `GET /api/v1/tenants/me/cuit-availability`: 30 / min / IP
- On bucket exhaustion: 429 `RATE_LIMIT_EXCEEDED` + `Retry-After: <seconds>` header + audit log

### 4.10 Capability: `audit-log`

`AuditLogger` (singleton, writes via `systemDataSource` so it bypasses RLS):
- `write(eventType, scope, userId?, tenantId?, request, metadata?)`
- Internally inserts a row with `ip_address` (from `X-Forwarded-For` or `request.getRemoteAddr()`), `user_agent` (truncated to 500 chars), JSONB `metadata`
- 8 v1 event types: `TENANT_REGISTERED`, `USER_LOGIN_SUCCESS`, `USER_LOGIN_FAILED`, `USER_LOGOUT`, `TOKEN_REFRESHED`, `ACCOUNT_LOCKED`, `RATE_LIMIT_EXCEEDED`, `PLATFORM_USER_CREATED`
- Never logs raw passwords, raw tokens, or BCrypt hashes (enforced by static review + a test that greps audit inserts)

### 4.11 NEW Capability: `reference-data`

Added because the frontend wizard needs a province list (PRD line 1048) and a username-availability endpoint was identified as a gap (§6 below). Both land in a new spec file under the same change.

- `GET /api/v1/reference/provinces` → `List<{code, name}>` (24 AR codes), cached client-side
- `GET /api/v1/tenants/me/username-availability?slug={slug}&username={u}` → `UsernameAvailabilityResponse` — see §10 (additions to spec)

---

## 5. Frontend Module Design

> No TypeScript code — only signatures, types, contracts.

### 5.1 Capability: `api-client-and-interceptors`

**`authInterceptor`** (HttpInterceptorFn, registered FIRST):
- Clones each `HttpRequest` with `withCredentials: true`
- Never reads `document.cookie`, never sets `Authorization` (cookie-only in v1)
- Returns `next(clonedReq)`

**`errorInterceptor`** (HttpInterceptorFn, registered SECOND):
- Catches `HttpErrorResponse`
- If `status === 0` or `error` is `ProgressEvent` → re-throw with `error = { code: 'NETWORK_ERROR', message: 'No pudimos conectarnos. Reintentá' }`
- If body has `error.code` → re-throw a typed `ApiError` with `{ code, message, details, status, originalError }`
- Special path on 401:
  - If request URL is `/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/refresh`, or any `/api/v1/platform/**` → re-throw without retry
  - Else: call `POST /api/v1/auth/refresh` (with `withCredentials`). If 200, retry original request once with `next(retryReq)`. If 401, clear `AuthStore` + `TenantStore` + navigate to `/login?returnUrl=<currentUrl>`
- No single-flight lock in v1 (gap: contention possible under load, deferred to v2)

**`AuthStore`** (Injectable, root-provided):
- `currentUser = signal<AuthUser | null>(null)`
- `isAuthenticated = computed(() => this.currentUser() !== null)`
- `isLoading = signal<boolean>(false)`
- Methods: `setUser(user: AuthUser)`, `clear()`, `setLoading(v: boolean)`

**`TenantStore`** (Injectable, root-provided):
- `currentTenant = signal<Tenant | null>(null)`
- Methods: `setTenant(t)`, `clear()`

**`AuthService`** methods (typed):
- `login(creds: LoginRequest): Observable<LoginResponse>`
- `register(payload: RegisterRequest): Observable<RegisterResponse>`
- `logout(): Observable<void>`
- `me(): Observable<AuthMeResponse>`
- `refresh(): Observable<LoginResponse>`

**`ApiService`** thin wrapper over `HttpClient` — exposes typed `get<T>`, `post<TReq, TRes>`, `put`, `delete` that always pass `withCredentials: true`.

**`ProvincesService`**:
- `list(): Observable<Province[]>` — calls `GET /api/v1/reference/provinces`

**Routes** (app.routes.ts):
- `{ path: '', loadComponent: HomeComponent, ... }` — public layout
- `{ path: 'register', loadComponent: RegisterComponent }` — public layout
- `{ path: 'login', loadComponent: LoginComponent }` — public layout
- `{ path: 'dashboard', canActivate: [authGuard], loadComponent: DashboardComponent }` — authenticated layout
- `{ path: '**', redirectTo: '' }`

### 5.2 Capability: `auth-shell-layout`

**`authGuard`** (functional `CanActivateFn`):
- Reads `AuthStore.isAuthenticated()`; if true, return `true`
- Else return `UrlTree` of `/login?returnUrl=<state.url>`

**`PublicLayoutComponent`** (standalone):
- Renders brand header + `<router-outlet>` + no nav menu
- Used as parent route component for `/`, `/register`, `/login`

**`AuthenticatedLayoutComponent`** (standalone):
- Renders top nav + user menu (firstName from `AuthStore.currentUser()?.firstName` + logout button) + `<router-outlet>`
- On mount, if `AuthStore.isAuthenticated() && !AuthStore.currentUser()`, calls `AuthService.me()` to populate

**`DashboardComponent`** (standalone):
- Renders `Bienvenido {firstName}`, `Tu slug es: {slug}`, `Tu tenant ID es: {tenantId}`, `Cerrar sesión` button
- Reads from `AuthStore` / `TenantStore` only (no HTTP)

**`LogoutFlow`** (in `AuthenticatedLayoutComponent`):
- On click: `AuthService.logout()` → on success: `AuthStore.clear()`, `TenantStore.clear()`, `Router.navigate(['/'])`

### 5.3 Capability: `wizard-registration`

**`RegisterComponent`** (standalone, imports `CdkStepperModule`):
- Renders CDK stepper with three steps
- Holds the full `RegisterForm` as a typed `FormGroup<{...}>` (NOT one FormGroup per step — the full model is needed for step 3 summary)
- `isLoading = signal<boolean>(false)`
- On submit: calls `RegisterService.submit()`, shows success screen on 201, returns to step 1 on 400 (with `details.slug`/`details.cuit`) or 409 (slug/cuit conflict)

**`CompanyStepComponent`**: 16 controls per spec §5.3; `cuit` has sync `cuitValidator` + async `cuitAvailableAsync` (300ms debounce); `slug` has sync `slugValidator` + async `slugAvailableAsync` (300ms debounce); `province` select bound to `ProvincesService.list()` (fallback to static array); `taxType` is a select with the three values

**`AdminStepComponent`**: 6 controls; `username` has sync `usernameValidator` + async `usernameAvailableAsync` (300ms debounce, requires both slug AND username per §10); `password` has sync `passwordValidator` and a strength signal `passwordStrength = signal<'Débil'|'Aceptable'|'Fuerte'>(...)` (tier rules: `Débil` if length<10 or missing class; `Aceptable` if length≥10 and meets policy; `Fuerte` if length≥12 and all 4 classes); `passwordConfirmation` has sync `matchValidator('password')`

**`ConfirmationStepComponent`**: two required `FormControl<boolean>`s; submit disabled until both true

**`RegisterService`**:
- `submit(payload: RegisterRequest): Observable<RegisterResponse>`
- `checkSlug(slug): Observable<SlugAvailabilityResponse>`
- `checkCuit(cuit): Observable<CuitAvailabilityResponse>`
- `checkUsername(slug, username): Observable<UsernameAvailabilityResponse>`

**Async validators** (in `shared/validators/`):
- All use a debounce of 300ms (RxJS `debounceTime`)
- Return `null` when available, `{ <code>: true }` when not (e.g. `{ slugTaken: true }`)

**Accessibility** (per PRD lines 1115-1120):
- All inputs `<label for="...">` linked by id
- Invalid fields carry `aria-invalid="true"`
- Error messages linked via `aria-describedby`
- Color contrast WCAG AA (blue-600 CTA, slate-900 text, slate-500 subtext)

### 5.4 Capability: `login-page`

**`LoginComponent`** (standalone):
- Typed `FormGroup<{ slug, username, password }>`
- `slug` placeholder `mvr`
- `password` input has show/hide toggle (signal `passwordVisible = signal(false)`)
- Submit button: `Ingresar` / `Ingresando…` (when `AuthStore.isLoading()`)
- Disabled `Olvidaste tu contraseña?` button with tooltip "Próximamente" (uses simple `title` attribute or Angular CDK Overlay for accessibility)
- `<div role="alert" aria-live="polite">` for error message

**Error mapping** (per spec):
- `INVALID_CREDENTIALS` → "Las credenciales no son correctas"
- `ACCOUNT_LOCKED` → "Demasiados intentos. Probá de nuevo en {X} minutos" where X = `Math.ceil(error.details.retryAfterSeconds / 60)` or from `Retry-After` header
- `NETWORK_ERROR` → "No pudimos conectarnos. Reintentá"
- Other codes → server `error.message` (Spanish fallback from envelope)

**Submit flow**:
- On click: `isLoading = true`, call `AuthService.login()`, on success: `AuthStore.setUser(...)`, `TenantStore.setTenant(...)`, `Router.navigate(['/dashboard'])`. On error: render mapped message, reset `isLoading = false`.

---

## 6. Error Catalog (canonical)

> Resolved gaps 1, 2, 5 below. HTTP status column is final.

| `error.code`                 | HTTP | Scope    | `details` shape                                            |
|------------------------------|------|----------|------------------------------------------------------------|
| `VALIDATION_ERROR`           | 400  | both     | `{ <field>: "human message" }` (object, keys per failing field) |
| `SLUG_ALREADY_TAKEN`         | 409  | both     | `{ slug: "..." }`                                          |
| `CUIT_ALREADY_REGISTERED`    | 409  | both     | `{ cuit: "..." }`                                          |
| `RESERVED_SLUG`              | 409  | both     | `{ slug: "..." }`                                          |
| `USERNAME_ALREADY_TAKEN`     | 409  | both     | `{ username: "..." }` (gap 1)                              |
| `EMAIL_ALREADY_TAKEN`        | 409  | both     | `{ email: "..." }` (gap 1)                                 |
| `INVALID_CREDENTIALS`        | 401  | both     | none                                                       |
| `ACCOUNT_LOCKED`             | 403  | both     | `{ retryAfterSeconds: 900, minutesRemaining: 15 }` (gap 7) |
| `ACCOUNT_DISABLED`           | 403  | both     | none                                                       |
| `UNAUTHENTICATED`            | 401  | both     | none (gap 5)                                               |
| `FORBIDDEN_SCOPE`            | 403  | both     | `{ expectedScope, presentedScope }` (gap 2)                |
| `FORBIDDEN_ROLE`             | 403  | platform | `{ role }`                                                 |
| `REFRESH_TOKEN_INVALID`      | 401  | both     | none                                                       |
| `REFRESH_TOKEN_EXPIRED`      | 401  | both     | none                                                       |
| `REFRESH_TOKEN_REVOKED`      | 401  | both     | none                                                       |
| `REFRESH_TOKEN_SCOPE_MISMATCH` | 401 | both     | `{ expectedScope }` (introduced for clarity)              |
| `TOKEN_REUSE_DETECTED`       | 401  | both     | none (audit event + forced revoke)                         |
| `TENANT_NOT_FOUND`           | 404  | platform | `{ tenantId }`                                             |
| `RATE_LIMIT_EXCEEDED`        | 429  | both     | `{ endpoint, retryAfterSeconds }` (header `Retry-After` also set) |
| `NETWORK_ERROR`              | 0    | frontend | (frontend-only normalization)                              |
| `INTERNAL_ERROR`             | 500  | both     | none                                                       |

**Envelope** (matches PRD line 505):
```json
{ "error": { "code": "...", "message": "...", "details": { ... } } }
```

**Header convention**: every error response includes `Content-Type: application/json; charset=utf-8`. 429 also includes `Retry-After: <seconds>`.

---

## 7. Security Design

### 7.1 JWT Issuance + Verification Flow

```
Client                       AuthService                       JwtService
  │  POST /auth/login          │                                │
  │  {slug,username,password}  │                                │
  │ ─────────────────────────► │  resolveUser + verifyPassword  │
  │                            │ ──────────────────────────────►│
  │                            │       (BCrypt matches)         │
  │                            │                                │
  │                            │  issueAccessToken(user,...)    │
  │                            │ ──────────────────────────────►│
  │                            │       (HS256, 15min, jjwt 0.12)│
  │                            │ ◄──────────────────────────────│
  │                            │                                │
  │  200 + Set-Cookie          │  issueRefreshToken(user)       │
  │   access_token             │ ──────────────────────────────►│
  │   refresh_token            │       (UUID + BCrypt hash)     │
  │ ◄───────────────────────── │ ◄──────────────────────────────│
```

Subsequent request:
```
Client                     AuthenticationFilter              JwtService
  │  GET /tenants/me         │                                │
  │  Cookie: access_token=…  │                                │
  │ ───────────────────────► │  parseAndValidate(jws)          │
  │                          │ ──────────────────────────────►│
  │                          │       (HS256 verify, claims)   │
  │                          │ ◄──────────────────────────────│
  │                          │  set SecurityContext +         │
  │                          │  TenantContext(tid, scope)      │
  │                          │ ──────────► DataSourceRoutingAspect
  │                          │            ──► RlsAspect SET LOCAL
  │ ◄────────────────────────│  200 OK
```

### 7.2 Refresh Token Rotation + Reuse Detection (gap 3)

**Rotation flow** (success path):
1. Client posts to `POST /api/v1/auth/refresh` with `refresh_token` cookie
2. `RefreshTokenService.validateAndRotate(uuid, scope)`:
   - BCrypt-match against rows with `user_scope = scope`, `revoked_at IS NULL`, `expires_at > now`
   - If not found → throw `REFRESH_TOKEN_INVALID`
   - If found and `revoked_at IS NOT NULL` → REUSE DETECTED (see below)
   - If found and `expires_at <= now` → `REFRESH_TOKEN_EXPIRED`
   - Otherwise: set `revoked_at = now()`, generate new UUID, INSERT new row with `replaced_by = old.id`, audit `TOKEN_REFRESHED`
3. Return new access + refresh cookies

**Reuse detection (resolved gap 3 — revoke entire descendant chain)**: Industry standard. When a revoked token is presented:
- Mark the presented row's `revoked_at = now()` (idempotent — it's already set)
- Walk the chain back via `replaced_by` from the presented token to find the most recent valid row. If a valid row exists in the same chain, treat ALL rows in that chain as compromised: `UPDATE refresh_tokens SET revoked_at = now() WHERE id IN (<chain ids>)`
- Audit `TOKEN_REUSE_DETECTED` with `metadata.tokenChainLength`
- Return `REFRESH_TOKEN_REVOKED` (user must log in again)
- **Decision rationale**: stolen token means the attacker may have already chained forward; forcing the legitimate user to re-login is a smaller cost than letting the attacker keep using the descendant chain. The `RefreshTokenRotationIT` test will cover this.

### 7.3 Cookie Attributes Matrix

| Profile | `Secure` | `SameSite` | `HttpOnly` | `Domain` | `Path`        |
|---------|----------|------------|------------|----------|---------------|
| dev     | **false**| `Strict`   | `true`     | unset    | `/api/v1[/...]` |
| prod    | **true** | `Strict`   | `true`     | unset    | `/api/v1[/...]` |

Override knob: `app.cookies.secure=false` in `application-dev.yml` (resolves gap 8). Default in `application.yml` is `true`. SameSite stays `Strict` in both.

### 7.4 Rate Limit Buckets

Per PRD lines 901-904:

| Endpoint                                              | Capacity | Refill              | Bucket key        |
|-------------------------------------------------------|----------|---------------------|-------------------|
| `POST /api/v1/auth/register`                          | 5        | 5 / hour (greedy)   | `register:{ip}`   |
| `POST /api/v1/auth/login`                             | 10       | 10 / min (greedy)   | `login:{ip}`      |
| `POST /api/v1/platform/auth/login`                    | 10       | 10 / min (greedy)   | `login:{ip}`      |
| `GET /api/v1/tenants/me/slug-availability`            | 30       | 30 / min (greedy)   | `avail:{ip}`      |
| `GET /api/v1/tenants/me/cuit-availability`            | 30       | 30 / min (greedy)   | `avail:{ip}`      |
| `GET /api/v1/tenants/me/username-availability`        | 30       | 30 / min (greedy)   | `avail:{ip}`      |

In-memory `ConcurrentHashMap` per Bucket4j 8 idiom (gap R4 in spec: documented limitation when horizontally scaled).

### 7.5 Lockout State Machine (gap 4)

**Cumulative** counter (open gap resolved). The counter does not expire; the lockout does:

```
           ┌──────┐ invalid creds
initial ──►│ OK   │──────────────────►  failed++  (1..4)
           └──────┘                       │
              ▲                           │ failed++ (5th)
              │ success                   ▼
              │                       ┌────────┐
           ┌──────┐                    │ LOCKED │ (30 min)
           │ OK   │                    └────────┘
           └──────┘                       │
              ▲      success or 30min     │ (any attempt while locked)
              │      elapsed              ▼
              └────────────────────── 403 ACCOUNT_LOCKED
                                       (counter NOT incremented)
```

Rules:
- 5 failed attempts in any rolling window → lock for 30 min
- Counter is **cumulative** (not pruned) — if the user accumulates 4 failures over 2 hours, the 5th still triggers lockout. Rationale: simpler, matches the spec's "expired failures are pruned OR remain cumulative" choice. Trade-off acknowledged: a careful attacker spreading attempts will still hit lockout because the threshold is low. The windowed approach is rejected as not worth the complexity in v1.
- On lockout expiry, the next attempt starts from the current `failedLoginAttempts` value (could be 5, in which case the 1st attempt after expiry fails AND re-locks immediately). This is documented behavior; users in this state will need to wait again. v2 will switch to windowed (Redis).

### 7.6 Password Hashing

- BCrypt strength 12, hard-coded
- `BCryptPasswordEncoder` bean (single instance, `@Bean` in `PasswordEncoderConfig`)
- Never logged, never returned in any DTO, never stored in plain
- Verified by `AuditLogNoPiiTest` (static review of audit log inserts)

### 7.7 Security Headers (PRD lines 913-919)

`SecurityHeadersFilter` (first in the chain):
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` (prod only)
- `Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'`
- No `X-XSS-Protection`

### 7.8 CORS

- dev: `http://localhost:4200` with `allowCredentials=true`
- prod: same origin (Vercel + Railway reverse-proxied via Cloudflare)

---

## 8. ADRs

Five ADRs are written in `openspec/changes/etapa-1-registro/adr/`:

| File                                       | Title                                                                |
|--------------------------------------------|----------------------------------------------------------------------|
| `0001-single-schema-rls-vs-schema-per-tenant.md` | Single schema + RLS for multi-tenancy                          |
| `0002-three-datasources-vs-single.md`       | 3 DataSources with role-based bypass vs single DataSource           |
| `0003-hs256-static-secret-vs-jwks.md`       | HS256 with static secret for v1 vs RS256/JWKS                       |
| `0004-opaque-uuid-refresh-vs-jwt-refresh.md` | Refresh tokens are opaque UUIDs hashed in DB vs JWT refresh tokens  |
| `0005-path-scoped-cookies-vs-single-scope.md` | Path-scoped cookies for company vs platform vs single scope cookie |

---

## 9. Test Strategy

### 9.1 Backend

| Layer        | What                                                | Tooling                                           |
|--------------|-----------------------------------------------------|---------------------------------------------------|
| Unit         | Validators (slug, CUIT mod-11, password, username)  | JUnit 5 + AssertJ                                |
| Unit         | `JwtService` (issue, parse, expiry, scope)          | JUnit 5 + Mockito + jjwt 0.12 parser             |
| Unit         | `RefreshTokenService` (issue, rotate, reuse chain)  | JUnit 5 + Mockito + ArgumentCaptor               |
| Unit         | `AuthService` (login success/fail/lock, register)   | JUnit 5 + Mockito                                |
| Unit         | `NoOpEmailService` (no SMTP, log only)              | JUnit 5 + LogCaptor                              |
| IT           | `RlsIntegrationIT` (gate — 2 tenants)               | Spring Boot Test + Testcontainers Postgres 16    |
| IT           | `RegisterFlowIT`, `LoginFlowIT`, `RefreshRotationIT` | Spring Boot Test + Testcontainers              |
| IT           | `RateLimitingIT` (all 4 endpoints)                  | Testcontainers + HttpClient                      |
| IT           | `PlatformAuthIT` (cross-scope rejection)            | Testcontainers                                   |
| IT           | `ReferenceDataIT` (`/reference/provinces`)          | Testcontainers                                   |
| IT           | `AuditLogNoPiiIT` (no secret material)              | Testcontainers                                   |
| Coverage     | Gate 0% (no JaCoCo yet) — `chore/bootstrap-repos` MUST add JaCoCo + threshold 80% services / 100% validators | JaCoCo |
| TDD posture  | All behavior tasks: failing test → green → refactor in same commit  | — |

### 9.2 Frontend

| Layer        | What                                                                  | Tooling                            |
|--------------|-----------------------------------------------------------------------|------------------------------------|
| Unit         | Validators (slug, CUIT, password)                                     | Vitest + jsdom                     |
| Unit         | `AuthStore`, `TenantStore` signal transitions                         | Vitest                             |
| Unit         | `authInterceptor` (withCredentials set, no cookie read)               | Vitest + Angular TestBed           |
| Unit         | `errorInterceptor` (envelope mapping, NETWORK_ERROR, 401 retry)       | Vitest + HttpClientTestingModule   |
| Component    | `RegisterComponent` (step nav, async debounce, post-register success) | Vitest + Angular TestBed           |
| Component    | `LoginComponent` (typed form, error mapping, loading state)           | Vitest + Angular TestBed           |
| Component    | `authGuard` (unauth → /login, authed → allow)                         | Vitest                             |
| E2E          | **Deferred** — `chore/bootstrap-repos` adds Playwright; this change does not require E2E (out of scope) | Playwright |

---

## 10. Resolved Open Gaps

| # | Gap (from spec)                                    | Decision                                                                 | Rationale                                                                 |
|---|----------------------------------------------------|--------------------------------------------------------------------------|---------------------------------------------------------------------------|
| 1 | `USERNAME_ALREADY_TAKEN` vs `EMAIL_ALREADY_TAKEN`  | Use `USERNAME_ALREADY_TAKEN` (409) and `EMAIL_ALREADY_TAKEN` (409) as canonical codes. They never nest under `VALIDATION_ERROR`. | Mirrors the existing pattern of `SLUG_ALREADY_TAKEN` and `CUIT_ALREADY_REGISTERED`. Uniqueness is a separate semantic from format. |
| 2 | `FORBIDDEN_SCOPE` vs `INVALID_CREDENTIALS` for cross-scope cookie | **`FORBIDDEN_SCOPE` (403)** for scope mismatches, **`INVALID_CREDENTIALS` (401)** only for bad credentials. | 401 = "we don't know who you are / your password is wrong"; 403 = "we know who you are, you can't do this". Cleaner semantic; matches OAuth conventions. |
| 3 | Token reuse: revoke entire descendant chain?      | **YES** (resolved: revoke all rows in the chain via `replaced_by` walk). | Industry standard (Auth0, Okta). Cost: legitimate user must re-login. Benefit: attacker cannot keep using a stolen token. `RefreshTokenRotationIT` covers it. |
| 4 | Lockout counter: cumulative vs pruned              | **Cumulative** for v1. | Simpler. Trade-off: user who hits 4 failures once and then returns days later only has 1 attempt before lockout. Acceptable for v1. v2 may switch to windowed with Redis. |
| 5 | 401 envelope code: `UNAUTHENTICATED` vs other      | **`UNAUTHENTICATED` (401)** for missing/invalid/expired access tokens. Distinct from `INVALID_CREDENTIALS` (bad username/password). | Allows the frontend to distinguish "session expired" from "wrong password" and trigger different UX (e.g. silent refresh vs. show login form). |
| 6 | New endpoint `GET /api/v1/tenants/me/username-availability?slug=&username=` | **ADD** a new capability `reference-data` (or extend `tenant-registration` with the username check). Returns `{ slug, username, available, reason? }`. | PRD line 1053 specifies async username check; the backend lacked the endpoint. Adding it now keeps the wizard UX consistent (debounced live check). |
| 7 | `ACCOUNT_LOCKED` shape: `details.minutesRemaining` vs `Retry-After` | **BOTH**: emit `details.retryAfterSeconds` (integer, seconds until unlock) AND `Retry-After` HTTP header (seconds). The frontend uses `Retry-After` if present, falls back to `details.retryAfterSeconds`, then renders "en unos minutos". | Redundant signal is fine; matches HTTP semantics for 429 (and we're using it on 403 here for clarity). |
| 8 | Cookie `Secure` flag in dev                         | `app.cookies.secure` flag (default `true`); `application-dev.yml` sets `app.cookies.secure=false`. `SecurityHeadersFilter`/`CookieWriter` reads the flag. | Solves the "browser rejects Secure cookies on http://localhost" issue. SameSite stays Strict in both profiles. |

### 10.1 Additions to Spec Required

> These need to be appended to the spec set during `sdd-tasks` (or as a follow-up delta). Calling them out explicitly so the orchestrator can decide.

| Spec file (new or extended)                | Addition                                                                                            |
|-------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `specs/reference-data.md` (NEW)            | `GET /api/v1/reference/provinces` returns `Province[]` (24 AR codes, ordered alphabetically). Cached client-side. |
| `specs/tenant-registration.md` (EXTEND)    | Add Requirement: `Username Availability Check Endpoint` mirroring slug/cuit availability, accepting both `slug` and `username` query params. |
| `specs/account-lockout-and-rate-limit.md` (EXTEND) | Add explicit `details.retryAfterSeconds` shape on `ACCOUNT_LOCKED` and the `Retry-After` header. |
| `specs/refresh-token-rotation.md` (EXTEND) | Add Requirement: "Reuse Detection Revokes Descendant Chain" — formalizing gap 3.                  |
| `specs/company-user-auth.md` (EXTEND)      | Add scenario for `UNAUTHENTICATED` (gap 5) replacing the open `or matches` clause.                  |

---

## 11. Risks + Mitigations

| # | Risk                                                                  | Likelihood | Mitigation                                                                                              |
|---|-----------------------------------------------------------------------|------------|---------------------------------------------------------------------------------------------------------|
| R1 | Spring Boot 4.0.6 vs PRD's 3.5.x — API drift in starters (Hibernate, security, jjwt compatibility) | Medium | `chore/bootstrap-repos` pins the working set with a `pom.xml` smoke test. ADRs cite this decision. CI runs `./mvnw -DskipTests=false test` to catch breakages. |
| R2 | 3-DataSource misconfiguration leaks RLS bypass to the wrong DataSource | Medium | `RlsIntegrationIT` is the **gate test** — fails the build if `companyDataSource` lets a cross-tenant SELECT return rows. Also: a `DataSourceUsageInspector` test that scans all `@Repository` interfaces and asserts each is mapped to exactly one DataSource. |
| R3 | Refresh-token reuse chain revocation logic is subtle                   | High       | Dedicated `RefreshTokenRotationIT` covers: (a) normal rotation, (b) reuse of revoked T1 → chain revoked, (c) reuse of middle token → whole chain revoked. Manual smoke flow in verify. |
| R4 | Bucket4j in-memory + horizontal scale = effective budget × N instances | Documented | The Rate Limiting spec already names this; design acknowledges it; v2 will add Redis-backed `ProxyManager`. |
| R5 | BCrypt 12 latency on register (one-time ~250ms)                        | Low        | Acceptable for register (one-time per tenant). Login is on a hot path but only at strength 12, not 14. Logged in `auth/performance-budget.md` (if not, will be in sdd-tasks). |
| R6 | Spring Boot 4 transitive dep surprises (e.g. Jakarta namespace, SpringDoc 2.7) | Medium | Pinned in `chore/bootstrap-repos`; this design uses only well-known Boot 4 APIs (`@ConfigurationProperties`, `OncePerRequestFilter`, `AbstractRoutingDataSource`). Context7 lookup confirmed Boot 4 still exposes `DataSourceProperties` and the security auto-config; jjwt 0.12 is API-stable. |
| R7 | The `email_verified` field exists but no verification endpoint in v1   | Low        | `audit-log` records the unverified user; UI shows the right messaging. PRD §Compliance is explicit that v1 does not activate email. |
| R8 | The `taxType` enum values must match the frontend's 3 options exactly  | Low        | `V1` CHECK constraint enforces `RESPONSABLE_INSCRIPTO`/`MONOTRIBUTO`/`EXENTO`. Frontend `taxType` select hard-codes the same three. A unit test in `wizard-registration` covers the option set. |
| R9 | The province list (24 codes) must be canonical across frontend + backend | Low | Single source of truth: `V10__seed_provinces_reference.sql` seeds `public.provinces`. Frontend `ProvincesService` fetches on app load and caches in a signal. The static `shared/data/provinces.ts` is a fallback only. |

---

## Appendix A: Context7 Lookups Performed

| Library       | Query                                                                  | Library ID                                       | Relevance |
|---------------|------------------------------------------------------------------------|--------------------------------------------------|-----------|
| Spring Boot 4 | Spring Boot 4 breaking changes vs 3.5, Jakarta, multi-datasource, security auto-config | `/websites/spring_io_spring-boot_4_0-snapshot`   | High      |
| jjwt 0.12     | jjwt 0.12 issue/verify HS256, SecretKey, builder/parser                | `/jwtk/jjwt`                                     | High      |
| Angular 20+   | Functional HttpInterceptorFn, withInterceptors, inject, signal store   | `/websites/v20_angular_dev`                      | High (Angular 20 patterns apply; v21 is forward-compatible) |
| Bucket4j 8    | Bucket4j 8 in-memory token bucket, Spring Boot servlet filter          | `/bucket4j/bucket4j`                              | High      |

---

## Appendix B: References to PRD

- §Data model: lines 161-415
- §RLS strategy: lines 432-494
- §API endpoints: lines 511-848
- §Security: lines 853-959
- §Frontend: lines 963-1120
- §Platform admin: lines 1124-1184
- §Package layout (corrected): lines 1238-1265
- §Config: lines 1306-1397
- §Testing: lines 1447-1480
- §DoD: lines 1481-1525
- §Risks: lines 1546-1554
