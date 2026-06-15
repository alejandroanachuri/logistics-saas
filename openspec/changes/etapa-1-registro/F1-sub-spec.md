# Sub-Spec F1 — Auth shell + register wizard + login + API client

> Source: 4 capability specs (`api-client-and-interceptors.md`,
> `auth-shell-layout.md`, `login-page.md`,
> `wizard-registration.md`). Backend prerequisites for E2E
> are all landed (PR3c/PR4a/PR4b/PR4c/PR5a/PR5b): register,
> login, refresh, logout, GET /me, plus the canonical error
> envelope and `withCredentials`-friendly cookie contract.

## Goal

Ship a runnable Angular 21 app that lets a real user:

1. Land on `/`, see the public layout + a brand header + a
   "Ir a iniciar sesión" CTA.
2. Click "Registrate", go through the 3-step CDK stepper
   (Company → Admin → Consent), submit to the backend, see
   the success screen, click "Ir a iniciar sesión".
3. Land on `/login`, fill `(slug, username, password)`, see
   backend errors as Spanish copy, on success land on
   `/dashboard` inside the authenticated layout with a user
   menu.
4. Click "Cerrar sesión" → cookie cleared, store cleared, back
   to `/`.

The HTTP layer is the contract glue: cookies, refresh retry,
typed errors, signal stores.

## In scope

- `core/types/api-error.ts` (typed error envelope shape).
- `core/types/auth.ts` (AuthUser, LoginRequest/Response,
  RegisterRequest/Response, MeResponse).
- `core/types/availability.ts` (slug / cuit / username
  availability check shape).
- `core/state/auth-store.ts` (signal-based: currentUser,
  isAuthenticated, isLoading).
- `core/state/tenant-store.ts` (signal-based: currentTenant).
- `core/api/auth-interceptor.ts` (withCredentials).
- `core/api/error-interceptor.ts` (envelope mapping + 401
  refresh-and-retry + forced logout).
- `core/services/auth.service.ts` (login, logout, refresh,
  fetchMe).
- `core/services/availability.service.ts` (slug / cuit /
  username availability with 300ms debounce).
- `core/services/registration.service.ts` (POST /register).
- `app.config.ts` wires `provideHttpClient` with the
  interceptor chain in the documented order.
- `app.routes.ts` routes the four paths with the two
  layouts.
- `core/guards/auth-guard.ts` (functional CanActivateFn).
- `shared/layouts/public-layout.ts` (header + router-outlet).
- `shared/layouts/authenticated-layout.ts` (top nav + user
  menu + router-outlet).
- `features/dashboard/dashboard.ts` (placeholder, reads
  stores).
- `features/login/login.ts` (slug + username + password
  form).
- `features/register/wizard/register-wizard.ts` (CDK
  stepper shell).
- `features/register/steps/company-step.ts`,
  `admin-step.ts`, `confirmation-step.ts`.
- Vitest unit tests for the two interceptors + the auth
  store + the tenant store (no Testcontainers, no Angular
  TestBed; per the spec, the HTTP layer is mockable without
  the backend).

## Out of scope

- E2E (Playwright) — deferred to a separate session; the
  unit tests are the gate per the spec.
- Province list (24 Argentine provinces) — will hard-code
  the same list the backend uses; the `GET /reference/provinces`
  endpoint is not on the F1 critical path.
- i18n — copy is Spanish per the spec; no English translation
  layer.
- A11y audit beyond the spec's `aria-live` + `aria-invalid`
  + `aria-describedby` mentions.

## Acceptance

- `bun run build` exits 0; bundle within the 1MB production
  budget set in `angular.json`.
- `bun test` exits 0; unit tests cover the 6 mandatory
  scenarios from the api-client-and-interceptors spec.
- The app boots and the 4 paths render. E2E is deferred
  but the boot + render is the static gate.

## Files touched (~1100 added lines)

| Path | Status | LoC |
|------|--------|-----|
| `src/app/core/types/api-error.ts` | NEW | ~10 |
| `src/app/core/types/auth.ts` | NEW | ~50 |
| `src/app/core/types/availability.ts` | NEW | ~20 |
| `src/app/core/types/index.ts` | NEW | ~5 |
| `src/app/core/state/auth-store.ts` | NEW | ~50 |
| `src/app/core/state/tenant-store.ts` | NEW | ~30 |
| `src/app/core/api/auth-interceptor.ts` | NEW | ~30 |
| `src/app/core/api/error-interceptor.ts` | NEW | ~150 |
| `src/app/core/services/auth.service.ts` | NEW | ~80 |
| `src/app/core/services/availability.service.ts` | NEW | ~60 |
| `src/app/core/services/registration.service.ts` | NEW | ~30 |
| `src/app/core/guards/auth-guard.ts` | NEW | ~20 |
| `src/app/shared/layouts/public-layout.ts` | NEW | ~30 |
| `src/app/shared/layouts/authenticated-layout.ts` | NEW | ~60 |
| `src/app/features/dashboard/dashboard.ts` | NEW | ~50 |
| `src/app/features/login/login.ts` | NEW | ~120 |
| `src/app/features/register/wizard/register-wizard.ts` | NEW | ~30 |
| `src/app/features/register/steps/company-step.ts` | NEW | ~150 |
| `src/app/features/register/steps/admin-step.ts` | NEW | ~100 |
| `src/app/features/register/steps/confirmation-step.ts` | NEW | ~70 |
| `src/app/app.config.ts` | EDIT (provideHttpClient chain) | +10 |
| `src/app/app.routes.ts` | EDIT (4 routes + 2 layouts + guard) | +30 |
| `src/app/app.ts` | EDIT (router-outlet only) | -3 |
| `src/app/app.html` | EDIT (just <router-outlet/>) | -10 |
| `src/app/core/api/auth-interceptor.spec.ts` | NEW | ~30 |
| `src/app/core/api/error-interceptor.spec.ts` | NEW | ~120 |
| `src/app/core/state/auth-store.spec.ts` | NEW | ~30 |
| `src/app/core/state/tenant-store.spec.ts` | NEW | ~20 |
