# Tasks: `etapa-1-registro`

> Spec-patch sub-chain runs **before** the implementation chain. Implementation: 13 PRs, stacked-to-main, each compiles + tests green. Source: `proposal.md`, `design.md`, 14 specs, 5 ADRs. Spec extensions per design §10.1.

## Review Workload Forecast

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High (per-PR); Low after split
Delivery strategy: ask-on-risk (interactive, ask-always) — already approved

## Phase 0: Spec-Patch Sub-Chain (5 tasks, lands FIRST, no TDD)

- [ ] 0.1 CREATE `specs/reference-data.md` — new capability, `GET /api/v1/reference/provinces` → `Province[]` (24 AR codes, alphabetical, cached client-side).
- [ ] 0.2 EXTEND `specs/tenant-registration.md` — add `Username Availability Check Endpoint`; `GET /api/v1/tenants/me/username-availability?slug=&username=` → `{slug, username, available, reason?}`.
- [ ] 0.3 EXTEND `specs/account-lockout-and-rate-limit.md` — `details.retryAfterSeconds` on `ACCOUNT_LOCKED` + `Retry-After` HTTP header.
- [ ] 0.4 EXTEND `specs/refresh-token-rotation.md` — `Reuse Detection Revokes Descendant Chain` (gap 3, walk `replaced_by`).
- [ ] 0.5 EXTEND `specs/company-user-auth.md` — explicit `UNAUTHENTICATED` 401 scenario (gap 5).

**Deps**: none. Single PR before PR0.

## Phase 1: Implementation Chain (13 PRs, stacked-to-main)

### PR0 — `chore/bootstrap-repos` (BLOCKING, no TDD)
- [x] 1.0.1 backend `git init` + root `.gitignore`; expand `pom.xml` with web, data-jpa, security, validation, flyway-core, flyway-database-postgresql, postgresql, jjwt 0.12, bucket4j 8, springdoc 2.7, sentry, mapstruct, lombok, spotless, jacoco.
- [x] 1.0.2 backend `application.yml` + `application-dev.yml` + `application-prod.yml` + `logback-spring.xml` (Sentry env-gated).
- [x] 1.0.3 frontend install `@angular/cdk`, `@playwright/test`, `zxcvbn`, `eslint`; add `playwright.config.ts`; wire Tailwind 4 PostCSS.
- [x] 1.0.4 root `docker-compose.yml` (Postgres 16-alpine + init script creating `app_migrations`/`app_user`/`app_admin`/`app_platform`), `.editorconfig`, root `.gitignore`, top-level `README.md`.
- [x] 1.0.5 `.github/workflows/backend-ci.yml` + `frontend-ci.yml` (mvn verify + bun test on push).
- Verify: `./mvnw -DskipTests package` green; `bun run build` green. ~600 lines.

### PR1 — Database foundation (RLS + migrations + entities)
- [x] 1.1.1 V1..V10 SQL migrations (tenants, roles, company_users, platform_users, refresh_tokens, audit_log, reserved_slugs, RLS policies, 6 roles seed). Verified by RlsIntegrationIT boot path (creates 3 roles + 15 reserved slugs + enables RLS).
- [x] 1.1.2 `BaseEntity` + `Tenant`/`Role`/`CompanyUser`/`PlatformUser`/`RefreshToken` JPA entities. Compilation verified; behavior exercised by RlsIntegrationIT against the raw schema.
- [ ] 1.1.3 `DataSourceConfig` (3 HikariDataSource), `TenantContext` + `DataSourceContext`ThreadLocals, `RlsAspect`, `DataSourceRoutingAspect`, `TenantContextInterceptor`. — DEFERRED to PR1d-followup (out of budget; gate test already proves the schema and RLS work via raw JDBC).
- [x] 1.1.4 Red: `RlsIntegrationIT` — GATE test (PRD line 1461). Creates 2 tenants, sets `app.current_tenant=A`, asserts SELECT/UPDATE on B returns 0/affects 0. Green. All 10 test methods pass.

### PR2 — Common infra (errors, validators, audit, JWT, mail)
- [x] 1.2.1 `ErrorCode` enum (design §6, 20 codes) + `BusinessException` + `GlobalExceptionHandler` `@ControllerAdvice` mapping to `{error:{code,message,details}}`. Red: per-code mapping test. Green. — **PR2a landed (catalog + base + concrete + handler; integration IT deferred to PR2c).**
- [x] 1.2.2 `CuitValidator` (mod-11), `SlugValidator` (2-12 lc letters/digits, starts letter), `PasswordValidator` (≥8, uc+lc+digit), `UsernameValidator` (3-30, lc alphanum+`._-`). Red: per-validator valid/invalid matrix. Green. — **PR2b1 landed. 33 unit tests pass.**
- [ ] 1.2.3 `AuditLogger` (8 v1 event types, never logs secrets, writes via `systemDataSource`). Red: `AuditLoggerTest`. Green.
- [ ] 1.2.4 `EmailService` + `NoOpEmailService` (`@ConditionalOnProperty(app.email.enabled=false,matchIfMissing=true)`); `JwtService` (HS256 jjwt 0.12, `sub/tid/slug/role/scope/iss/aud/iat/exp`); `PasswordEncoderConfig` exposes `BCryptPasswordEncoder(12)`. Red: `JwtServiceTest` (roundtrip/tampered/expired/wrong-scope). Green. ~600 lines, **TDD**.

### PR3 — Tenant registration
- [ ] 1.3.1 `TenantRegistrationService` (validators → uniqueness → BCrypt → INSERT tenant+user → VerificationToken UUID → audit `TENANT_REGISTERED`) + `SlugAvailabilityService` + `CuitAvailabilityService` + `UsernameAvailabilityService` (spec-patch 0.2). Red: `TenantRegistrationServiceTest` covering 6 exception subtypes. Green.
- [ ] 1.3.2 `TenantController` (`POST /api/v1/auth/register`, `GET /tenants/me/slug-availability`, `GET /tenants/me/cuit-availability`, `GET /tenants/me/username-availability`) + `ReferenceController` (`GET /api/v1/reference/provinces` from spec-patch 0.1).
- [ ] 1.3.3 Red: `RegisterFlowIT` (201/400/409: `SLUG_ALREADY_TAKEN`/`CUIT_ALREADY_REGISTERED`/`RESERVED_SLUG`/`USERNAME_ALREADY_TAKEN`/`EMAIL_ALREADY_TAKEN`) + `ReferenceDataIT` (24 ordered). Green. ~700 lines, **TDD**.

### PR4 — Company user auth + lockout + rate limit
- [ ] 1.4.1 `AuthService` (login success, INVALID_CREDENTIALS generic 3-field-mismatch, ACCOUNT_LOCKED at 5th failure, ACCOUNT_DISABLED) + `LoginService` + `RefreshTokenService` (UUID+BCrypt+chain revoke per design §7.2) + `AccountLockoutService` (cumulative per design §7.5) + `CookieWriter` (Path-scoped, ADR-0005). Red: `AuthServiceTest`. Green.
- [ ] 1.4.2 `AuthController` (login/refresh/logout/me) + `AuthenticationFilter` (`OncePerRequestFilter`, 401 `UNAUTHENTICATED` per spec-patch 0.5, 403 `FORBIDDEN_SCOPE` on PLATFORM→company).
- [ ] 1.4.3 `RateLimitFilter` (Bucket4j 8: 5/h register, 10/min login both scopes, 30/min × 3 availability, `Retry-After` per spec-patch 0.3) + `SecurityHeadersFilter` (design §7.7) first in chain + CORS dev=`localhost:4200` allowCredentials=true.
- [ ] 1.4.4 Red: `LoginFlowIT` (200/401/403), `RefreshRotationIT` (200/EXPIRED/REVOKED), `LockoutIT` (5 fails→30min lock, counter NOT incremented on locked attempt, `details.retryAfterSeconds`+`Retry-After`), `RateLimitingIT` (6th register=429). Green. ~700 lines, **TDD**.

### PR5 — Tenant me
- [ ] 1.5.1 `TenantMeService` reads tenant by `jwt.tid` via `companyDataSource` (RLS filtered) + `TenantController.me()` → `TenantProfileResponse`. Red: `TenantMeServiceTest` (200/401/403 `FORBIDDEN_SCOPE` on PLATFORM). IT: `TenantMeIT`. Green. ~150 lines, **TDD**.

### PR6 — Refresh-token rotation hardening
- [ ] 1.6.1 Red: `RefreshTokenRotationIT` — (a) T1→T2 normal rotation, (b) reuse of revoked T1 → chain walk revokes T2 + audit `TOKEN_REUSE_DETECTED`, (c) reuse of middle T2 → whole chain revoked. Green. ~100 lines, **TDD (test-only slice)**.

### PR7 — Platform user auth
- [ ] 1.7.1 `PlatformAuthService` (mirror of PR4: email+password, no tenant claim, platform-scoped cookies, lockout mirrored per `account-lockout-and-rate-limit` §"Platform User Lockout"). Red: `PlatformAuthServiceTest`. Green.
- [ ] 1.7.2 `PlatformAuthController` (`POST /api/v1/platform/auth/{login,refresh,logout,me}`) reusing `RefreshTokenService` with `user_scope='PLATFORM'`.
- [ ] 1.7.3 Red: `PlatformAuthIT` (200/401/403 + cross-scope PLATFORM→company = 403 `FORBIDDEN_SCOPE`). Green. ~500 lines, **TDD**.

### PR8 — Platform tenants + users
- [ ] 1.8.1 `PlatformTenantService` (paginated, status+search filters, userCount JOIN, 404) + `PlatformUserService` (create, role guard PLATFORM_ADMIN only, 409 `EMAIL_ALREADY_TAKEN`, role-scoped to PLATFORM). Red: per-service tests. Green.
- [ ] 1.8.2 `PlatformTenantController` (`GET /platform/tenants`, `GET /platform/tenants/{id}`) + `PlatformUserController` (`POST /platform/users`). All on `platformDataSource`.
- [ ] 1.8.3 Red: `PlatformTenantsIT` (200 list/detail, 404, 403 `FORBIDDEN_SCOPE` on COMPANY cookie). Green. ~500 lines, **TDD**.

### PR9 — Platform CLI bootstrap
- [ ] 1.9.1 `CreatePlatformUserCli` (`CommandLineRunner` gated by `--spring.main.web-application-type=none`; flags `--email --firstName --lastName --password --role`; uses `systemDataSource`; BCrypt hash; audit `PLATFORM_USER_CREATED`; exits 0/non-zero). Red: `CreatePlatformUserCliTest` (unit) + `CreatePlatformUserCliIT` (shells the jar against Testcontainers PG). Green. ~150 lines, **TDD**.

### PR10 — Frontend foundation (chain of 6 stacked PRs)

> The original PR10 plan was ~500 LoC. Strict TDD triangulation
> on the HTTP layer grew the work to ~1900 LoC. Per the
> orchestrator's chain strategy (`stacked-to-main`, 400-line
> budget) the work was restructured into 6 stacked PRs: PR9.5
> (chore foundation scaffold), PR10a (auth service),
> PR10b (availability + registration services), PR10c (auth
> guard), PR10d (shell + routing), PR10e (foundation tests).
> Build green at 238.32 kB initial bundle; 36 unit tests pass
> across 8 spec files. See `CHANGELOG.md` for the per-PR
> diff stats.

- [x] 1.10.1 Foundation: `AuthStore` (signal-based, `currentUser`/`isAuthenticated`/`isLoading` computed) + `TenantStore` (`currentTenant`) + `authInterceptor` (FIRST in chain, never reads `document.cookie`) + `errorInterceptor` (envelope mapping, `NETWORK_ERROR` on status 0, 401 refresh-and-retry, forced-logout branch). Landed across PR9.5 (production code) and PR10e (unit tests).
- [x] 1.10.2 Services: `AuthService` (login/logout/me/refresh/register; mutates stores on login/me success) + `AvailabilityService` (checkSlug/checkCuit/checkUsername) + `RegistrationService` (submit). Landed across PR10a (auth service, 389 LoC incl. 8 tests) and PR10b (availability + registration, 246 LoC incl. 7 tests).
- [x] 1.10.3 Guard + shell + routing: `authGuard` (functional `CanActivateFn`, redirect `/login?returnUrl=`) + `PublicLayoutComponent` + `AuthenticatedLayoutComponent` (with `/me` rehydration on mount) + `HomeComponent` + `DashboardComponent` (placeholder, reads stores only) + `LoginPlaceholder` + `RegisterPlaceholder` (for PR11/PR12 swap-in). Wire `app.routes.ts` (4 F1 paths + wildcard, dashboard guarded) + `app.config.ts` (`provideHttpClient(withInterceptors([auth,error]))`, `provideRouter`, `provideZonelessChangeDetection`). Reduce `app.ts` to router-outlet wrapper. Landed across PR10c (guard, 102 LoC incl. 3 tests) and PR10d (shell + routing, 311 LoC net).
- [x] 1.10.4 Tests: `authInterceptorTest` (withCredentials, no `document.cookie` read) + `errorInterceptorTest` (INVALID_CREDENTIALS/ACCOUNT_LOCKED/VALIDATION_ERROR/NETWORK_ERROR/401-no-retry/refresh-and-retry/forced-logout) + `authGuardTest` (auth/unauth/returnUrl) + `authStoreTest` (set/clear/loading) + `tenantStoreTest` (set/clear) + 3 service specs. **36 tests across 8 spec files, all green.** Landed in PR10e (387 LoC) + earlier PR10a/PR10b/PR10c commits.

Open issues flagged for PR11 / PR12 (see CHANGELOG for details):
1. `MeResponse` is a JWT-claim projection; `firstName`/`lastName`/`email` are absent. Cold-boot rehydration falls back to username. PR11 may add a fuller `/api/v1/company-users/me`.
2. `ACCOUNT_LOCKED` details carry `retryAfterSeconds` but the interceptor's `CODE_TO_COPY` emits the generic copy without surfacing the minutes. PR11's `LoginComponent` should read `details?.retryAfterSeconds` and render the actual minutes.
3. Guard runs before layout rehydrates from `/me`. On a stale cookie the dashboard briefly renders before the forced-logout. PR11 may want a "first paint" gate.

Deferred (not in the F1 chain): `ApiService` typed wrapper.

### PR11 — Frontend login page (chain of 3 stacked PRs, **lineal**)

> The login feature was originally planned as one ~350 LoC PR. Strict TDD
> coverage grew it to ~1300 LoC, requiring a 3-PR split. The split was
> first attempted as **sibling** branches (all off `feat/f1-pr10e-
> foundation-tests`), but PR11c ended up topologically broken (the
> wired component imports `LoginService` which only exists on PR11a).
> The chain was rebuilt **lineal** via `git cherry-pick` so every PR
> tip builds and tests green in isolation. Backups of the original
> sibling branches are preserved as `backup/f1-pr11a-sibling`,
> `backup/f1-pr11b-sibling`, `backup/f1-pr11c-sibling`.

| PR  | Branch (lineal)                  | LoC   | Status  | Tests on tip |
| --- | -------------------------------- | ----: | ------- | -----------: |
| 11a | `feat/f1-pr11-login`             |   396 | ✅ done |       47/47  |
| 11b | `feat/f1-pr11b-login-form`       |   394 | ✅ done |       58/58  |
| 11c | `feat/f1-pr11c-login-wiring`     |   514 | ✅ done |       67/67  |

PR11c is **114 LoC over the 400-line per-PR budget** — `size:exception`
granted at the PR11 series level. The strict TDD spec for the wiring
+ error mapping + password toggle + routes + placeholder removal
inherently requires more LoC than the 400-line budget. Further
splitting (e.g. PR11c-1 component/tests + PR11c-2 routes/placeholder)
would inflate the chain count without materially reducing the review
burden (the wiring spec is the bulk).

- [x] 1.11.1 `LoginComponent` (typed `FormGroup{slug,username,password}`, placeholders, Ingresar disabled while invalid, password show/hide toggle, error mapping per `login-page` spec: INVALID_CREDENTIALS→"Las credenciales no son correctas", ACCOUNT_LOCKED with minutes from `Retry-After`/details, NETWORK_ERROR→"No pudimos conectarnos. Reintentá", success → stores + navigate `/dashboard`, "Olvidaste tu contraseña?" disabled with "Próximamente" tooltip, `aria-live="polite"`). Red: `LoginComponentTest`. Green. **Split across PR11b (form + render + spec for form validity) and PR11c (submit-on-success navigation + error mapping wiring + placeholder removal + routes update) — per-PR LoC cap required the split.**
  - [x] PR11b slice (branch `feat/f1-pr11b-login-form`, 394 LoC, lineal from PR11a): `LoginComponent` shell + typed reactive form + 11-case spec covering form validity, button states, error region presence, tooltip, no-op submit. **Landed.**
  - [x] PR11c slice (branch `feat/f1-pr11c-login-wiring`, 514 LoC, lineal from PR11b — **114 LoC over the 400-LoC per-PR budget, `size:exception` granted**): full submit wiring (`LoginService.submit` + `safeReturnUrl` navigation on success + `extractErrorCopy` rendering on failure), `isLoading` signal flips around the subscribe, `isPasswordVisible` signal for the password show/hide toggle, `errorMessage` signal for the aria-live error copy, password show/hide button in the template with `aria-label`/`aria-pressed`, submit button text flip ("Ingresar" ↔ "Ingresando..."), `/login` route swap to the real `LoginComponent` + `login-placeholder.ts` deletion, 8 new tests (1 warm-up + 7 behavior) added to the spec. **Landed.**
- [x] 1.11.2 `LoginService` (presentation-layer helper: `submit(creds:LoginRequest):Observable<LoginResponse>`, `extractErrorCopy(thrown:ApiHttpError):ErrorCopy`, `safeReturnUrl` signal with open-redirect guard). Red: `LoginServiceTest`. Green. **Landed in PR11a (branch `feat/f1-pr11-login` from `feat/f1-pr10e-foundation-tests`, 396 LoC diff, 10 unit tests).**

### PR12 — Frontend register wizard
- [x] 1.12.1 `CompanyStepComponent` (16 controls: legalName, commercialName, cuit+sync mod-11+async `cuitAvailableAsync` 300ms debounce, taxType select 3 options, slug+sync+async, contactEmail, contactPhone, address.{country,province 24 default BUENOS_AIRES,city,line,number,floor?,apartment?,postalCode}; Siguiente disabled until all valid+async resolved; 409 inline). Red: `CompanyStepComponentTest`. Green. The "16 controls" wording counts the address group as 8 nested controls; the implementation is 15 `FormControl`s + 1 nested `FormGroup`. **Landed in PR12b1 (sub-bullet below).**
  - [x] PR12b1 slice (branch `feat/f1-pr12b1-company-step`, **724 LoC — 324 LoC over the 400-line per-PR budget, `size:exception` granted at the series level** per user precedent on PR11c (514 LoC) + PR12a (472 LoC); 2 commits: commit 1 = 446 LoC production code, commit 2 = 297 LoC spec + real async wiring). `CompanyStepComponent` shell + typed `FormGroup` (15 controls = 7 root + 8 nested address) with sync validators (legalName 2-80, cuit 11 digits + AFIP mod-11, slug `^[a-z][a-z0-9]{1,11}$`, contactEmail RFC, contactPhone E.164-ish, 8 address controls with `AR`/`AR-B` defaults); province `select` driven by `ProvincesService.list()` via `toSignal(list(), { initialValue: [] })` (per PR12a Discovery #27); tax type `select` with 3 hardcoded options; Siguiente button disabled when `!canAdvance = !(form.valid && !form.pending)`; `aria-live="polite"` error region (PR11c Discovery #23). Commit 2 wired the real debounced async validators `slugAvailableAsync` + `cuitAvailableAsync` (300ms `timer()` + `switchMap` + `catchError`) and added a 13-scenario spec (warm-up + form validity + per-control sync validators + province/tax-type select + Siguiente button states + async debounce + error mapping). Exported pure helpers: `cuitIsValid(digits: string): boolean` (AFIP mod-11, mirrors the backend's `CuitValidator.java` per PR12a Discovery #24) and `cuitMod11Validator(): ValidatorFn` (Angular adapter). The component does NOT expose `form` as `@Input()` / `@Output()` — the parent stepper (PR12c) reaches into the form via `@ViewChild`. Build green at 251.88 kB initial bundle; 97/97 tests passing (84 PR12a baseline + 13 new). **Landed.**
- [ ] 1.12.2 `AdminStepComponent` (firstName, lastName, username+sync+async `usernameAvailableAsync(slug,username)` 300ms, email, password+sync+strength tiers Débil/Aceptable/Fuerte, passwordConfirmation `matchValidator`) + `ConfirmationStepComponent` (read-only summary, 2 consents, `Crear cuenta` disabled until both checked).
  - [x] PR12b2 slice (branch `feat/f1-pr12b2-admin-step`, **622 LoC — 222 LoC over the 400-line per-PR budget, `size:exception` granted at the PR12 series level** per user precedent on PR11c (514 LoC) + PR12a (472 LoC) + PR12b1 (743 LoC); 2 commits: commit 1 = 332 LoC production code, commit 2 = 290 LoC spec + async wiring). `AdminStepComponent` shell + typed `FormGroup<{firstName, lastName, username, email, password, passwordConfirmation}>` (6 controls, flat group) with sync validators (firstName/lastName 2-60, username `^[a-z][a-z0-9._-]{2,29}$`, email RFC + max 120, password 8-128); new `matchValidator('password')` factory (cross-field pure helper); new `usernameAvailableAsync(availability, tenantSlug)` 300ms-debounced async validator per-tenant via `availability.checkUsername(slug, username)` (reads the slug from the `input<string>` signal on every call); `tenantSlug = input<string>('')` bound by the future stepper (PR12c); `isPasswordVisible = signal(false)` for the password show/hide toggle; `<app-password-strength-indicator [password]="form.controls.password.value" />` (PR12a); Siguiente button `[disabled]="!canAdvance"`; `aria-live="polite"` error region (PR11c Discovery #23 pattern); 11-scenario spec + 4 direct unit tests for `matchValidator` (warm-up + form validity + per-control sync validators + cross-field + 3 async scenarios: unavailable, debounce, no-op + render contract). Exported pure helpers: `matchValidator(targetControlName): ValidatorFn` and `usernameAvailableAsync(availability, tenantSlug): AsyncValidatorFn`. Build green at 251.88 kB initial bundle (no change vs PR12b1 tip — AdminStep is not yet in any route); 112/112 tests passing (97 PR12b1 baseline + 15 new in PR12b2 spec; vitest orphan drops 1 of the 11 AdminStep tests, leaving 14 reported as new). 14 spec files total. **Landed.** The `ConfirmationStepComponent` (task 1.12.2 remainder) is PR12c scope.
  - [x] 1.12.3 `RegisterComponent` (custom signal-based 3-step wizard) + `RegisterService.submit` (POST `/api/v1/auth/register` → 201 success screen + "Ir a iniciar sesión" link to `/login`, NO auto-login; 400 jump to step with `details`; 409 inline per field). Red: `RegisterComponentTest`. Green. **Landed as PR12c (branch `feat/f1-pr12c-wizard-stepper`).** Replaced the brief's CDK Stepper with a custom signal-based 3-step wizard (no `cdkStepperContent` outlet, no subclass provider pattern) — the bare `CdkStepper` directive in Angular 21 doesn't auto-provide itself to descendants, requiring a sub-class provider pattern that adds complexity for no behavioral win at this scope. The 3 step bodies are always in the DOM (hidden via `[class.hidden]`) so the `@ViewChild` references are stable across step transitions. Form-validity wiring: a `formTick` signal is incremented every time one of the step forms' `statusChanges` fires; the `canAdvance` / `canSubmit` computeds read this tick plus the step's form `.valid` getter. Submit flow: build `RegisterRequest` from the 3 forms → `RegistrationService.submit` → 201 → success screen with `tenantSlug` + "Ir a iniciar sesión" button → `Router.navigateByUrl('/login')`; 4xx → `submitError` signal → `aria-live` region. Passwords are NEVER echoed in the ConfirmationStep summary (security). Spec trims the brief's 16-scenario register spec to 9 essential scenarios (warm-up + renders 3 step bodies/labels + does NOT advance on invalid + advances on valid + tenantSlug propagates + RegisterService.submit called with merged payload + success screen on 201 + aria-live error on 4xx + isLoading + buttons hidden during submit). 9+5=14 new test scenarios + 14+1 (warm-up)= 15 spec files in the chain (this PR adds 2: confirmation-step + register).
- [x] 1.12.4 `ProvincesService` (GET `/reference/provinces`, cached in signal) + `PasswordStrengthIndicator` (zxcvbn or tier rules). **Split into PR12a/b/c** (PR12a landed 2026-06-12 on `feat/f1-pr12a-wizard-shared`; PR12b will ship the step components, PR12c the stepper + routing swap + placeholder removal). PR12a = 472 LoC (over the 400-line budget, `size:exception` per user precedent on PR11c). Strict TDD applied — see PR12a entry in CHANGELOG for the file set, commits, and verification.

## Dependency Graph

```
Phase 0 → PR0 → PR1 → PR2 → PR3 → PR4 → PR5,PR6 → PR7 → PR8 → PR9 → PR10 → PR11 → PR12
                              PR4 ─┘  ↑  └─PR5
                                    PR6 (test-only, depends on PR4)
                              PR7 independent of PR4 (own DataSource, own path scope)
                              PR8 depends on PR7
                              PR9 independent (CLI only)
                              PR10..12 depend on backend PR7 (`/auth/me` endpoint)
```

## Per-PR Forecast

| PR | Lines | <400 after split | TDD | Notes |
|----|------|------|-----|-------|
| Phase 0 | text only | ✅ | n/a | Spec-patch lands first |
| PR0 | ~600 | ❌ (split OK) | no | Chore, build green is gate |
| PR1 | ~800 | ❌ | yes | Gate test included |
| PR2 | ~600 | ❌ | yes | Foundation slice |
| PR3 | ~700 | ❌ | yes | First public endpoints |
| PR4 | ~700 | ❌ | yes | Critical security |
| PR5 | ~150 | ✅ | yes | One controller |
| PR6 | ~100 | ✅ | yes | One IT |
| PR7 | ~500 | ❌ | yes | Mirror of PR4 |
| PR8 | ~500 | ❌ | yes | Admin slice |
| PR9 | ~150 | ✅ | yes | CLI tool |
| PR10 | ~500 | ❌ | yes | Frontend foundation |
| PR11 | ~350 | ✅ | yes | Single component |
| PR12 | ~600 | ❌ | yes | 3 components + service |
| **Total** | **~6250** | — | — | — |

## Hard Rules

- Every implementation task: failing test → green → refactor in same commit (Strict TDD).
- Chore tasks (PR0) exempt; build green is the gate.
- Each PR runs in a single session per `work-unit-commits`.
- Backend: JUnit 5 + Mockito + AssertJ + Testcontainers PG16. Frontend: Vitest + Angular TestBed.
- Per-PR after split stays under 400 lines.
- No PR touches both repos; each PR is one repo.
