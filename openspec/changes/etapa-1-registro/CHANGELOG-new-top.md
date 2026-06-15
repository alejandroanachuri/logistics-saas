# Changelog — etapa-1-registro

All notable changes to the `etapa-1-registro` change folder are
documented here. The newest entry sits at the top.

## F1 frontend foundation — chain of 6 stacked PRs (etapa-1-registro)

The F1 frontend slice (auth + register) was originally planned
as a single PR10 of ~500 LoC. The implementation surface proved
larger once strict TDD triangulation was applied to the HTTP
layer; the work was restructured into 6 stacked PRs, each under
or within 12 LoC of the 400-line budget, all under the
`stacked-to-main` chain strategy.

| PR  | Branch                                                | LoC   | Purpose                                                |
| --- | ----------------------------------------------------- | ----- | ------------------------------------------------------ |
| 9.5 | `chore/f1-foundation-scaffold`                        | 440   | Foundation scaffold (types, stores, interceptors, app.config wiring, test-setup) |
| 10a | `feat/f1-pr10a-auth-service`                          | 389   | `AuthService` + 8 unit tests                            |
| 10b | `feat/f1-pr10b-availability-registration-services`   | 246   | `AvailabilityService` + `RegistrationService` + 7 unit tests |
| 10c | `feat/f1-pr10c-auth-guard`                            | 102   | `authGuard` + 3 unit tests                              |
| 10d | `feat/f1-pr10d-shell-and-routing`                     | 311   | 2 layouts + dashboard + home + 2 placeholders + routes wiring + app shell reduction |
| 10e | `feat/f1-pr10e-foundation-tests`                      | 387   | 4 spec files for the foundation (interceptors + stores) |

Build: `bun run build` exits 0; initial bundle 238.32 kB
(under the 1 MB Angular production budget).
Tests: `bun run test` exits 0; 8 spec files, 36 tests, 0
failures. Covers the 6 mandatory scenarios from
`api-client-and-interceptors.md` plus the store transitions.

### Files added across the chain (component summary)

- **`core/types/`** (`api-error`, `auth`, `availability`, `index`):
  typed wire shapes; no `any` in the public service surface.
- **`core/state/auth-store.ts`**: signal-based `AuthStore` with
  `currentUser` / `isAuthenticated` (computed) / `isLoading`
  signals plus `setUser` / `clear` / `setLoading` methods.
- **`core/state/tenant-store.ts`**: signal-based `TenantStore`
  with `currentTenant` signal plus `setTenant` / `clear`
  methods.
- **`core/api/auth-interceptor.ts`**: `HttpInterceptorFn` that
  clones the request with `withCredentials: true`. Does NOT
  read `document.cookie`; never sets an `Authorization` header.
- **`core/api/error-interceptor.ts`**: `HttpInterceptorFn` that
  parses the backend error envelope, normalises network
  failures to `NETWORK_ERROR`, and on a 401 on a refreshable
  endpoint performs exactly one `POST /api/v1/auth/refresh`
  via `globalThis.fetch`; if the refresh fails the
  interceptor clears both stores and navigates to
  `/login?returnUrl=<currentUrl>`. The no-retry paths are
  `/api/v1/auth/login`, `/api/v1/auth/register`,
  `/api/v1/auth/refresh`, and the `/api/v1/platform/**` prefix.
- **`core/services/auth.service.ts`**: typed wrappers for
  `login` / `logout` / `me` / `refresh` / `register`. On
  `login` and `me` success the response is committed to the
  `AuthStore` and the `TenantStore`. `refresh` does not
  mutate the stores (it is a one-shot cookie refresher used
  by the error interceptor). `register` returns the
  `RegisterResponse` without auto-login (the wizard shows a
  success screen and the user clicks "Ir a iniciar sesion").
- **`core/services/availability.service.ts`**: typed wrappers
  for the three "is this value available" endpoints
  (slug, cuit, username). No debounce in the service — the
  300ms debounce lives in the wizard's async validators.
- **`core/services/registration.service.ts`**: typed wrapper
  for `POST /api/v1/auth/register`.
- **`core/guards/auth-guard.ts`**: functional `CanActivateFn`
  that reads `AuthStore.isAuthenticated()` and returns a
  `UrlTree` for `/login?returnUrl=<state.url>` when the
  store is empty. Applied to the `dashboard` route only.
- **`shared/layouts/public-layout.ts`**: brand header +
  `<router-outlet />`. Used as the parent route component for
  `''`, `register`, and `login`. No top nav, no user menu.
- **`shared/layouts/authenticated-layout.ts`**: top nav with
  the brand, a user menu (firstName + Cerrar sesion button),
  `<router-outlet />`. On mount, if the store reports
  authenticated but no `currentUser` is cached, the layout
  calls `AuthService.me()` to rehydrate; the error
  interceptor handles the forced-logout branch on failure.
- **`features/home/home.ts`**: tiny landing page with the
  "Ir a iniciar sesion" and "Registrate" CTAs.
- **`features/login/login-placeholder.ts`**: placeholder for
  the F1 login page. The real `LoginComponent` lands in PR11.
- **`features/register/register-placeholder.ts`**: placeholder
  for the F1 register wizard. The real wizard lands in PR12.
- **`features/dashboard/dashboard.ts`**: placeholder per spec
  — reads from `AuthStore` + `TenantStore` only (no HTTP),
  renders the spec copy ("Bienvenido", "Tu slug es", "Tu
  tenant ID es", "Cerrar sesion").
- **`app.routes.ts`**: 4 F1 paths (`''`, `login`, `register`,
  `dashboard`) + wildcard. `/dashboard` is guarded by
  `authGuard`. Home and Dashboard use `loadComponent`
  (lazy); the two placeholders and the layouts are eagerly
  imported.
- **`app.ts` + `app.html`**: reduced to a one-line
  `<router-outlet />` wrapper. The welcome SVG and the
  `title` signal are gone.
- **`app.spec.ts`**: deleted (the test asserted the old
  welcome copy which is now obsolete).
- **`src/test-setup.ts`**: a defensive vitest setup file. The
  Angular CLI's `@angular/build:unit-test` builder already
  auto-injects an `init-testbed.js` virtual file that
  initializes TestBed; this file is the hand-written
  counterpart for scenarios where the builder is bypassed.
- **Tests (8 spec files, 36 tests, all green)**:
  - `auth-interceptor.spec.ts` (2 tests)
  - `error-interceptor.spec.ts` (5 tests)
  - `auth-store.spec.ts` (7 tests for the store + 3 tests
    for the guard)
  - `tenant-store.spec.ts` (4 tests)
  - `auth.service.spec.ts` (8 tests for the login/logout/me/
    refresh/register methods + store mutations)
  - `availability.service.spec.ts` (8 tests for the three
    availability endpoints with the right query params)
  - `registration.service.spec.ts` (1 test)

### Open issues flagged for PR11 / PR12

1. **`MeResponse` is a JWT-claim projection**, not a full user
   record (`firstName`, `lastName`, `email` are not in the
   claim shape). The cold-boot `/me` rehydration in
   `AuthenticatedLayoutComponent` therefore has nothing to
   populate `firstName` from after the post-login response is
   gone; the dashboard briefly falls back to the username.
   PR11's login flow is unaffected (login carries the full
   `AuthUser`). A future PR may add a fuller
   `/api/v1/company-users/me` endpoint, or accept the
   username-as-firstName fallback for v1.
2. **`ACCOUNT_LOCKED` details carry `retryAfterSeconds`** but
   the `errorInterceptor`'s `CODE_TO_COPY` map only emits the
   generic "Demasiados intentos. Proba de nuevo en unos
   minutos" copy — the actual minutes value is not surfaced.
   PR11's `LoginComponent` should read `details?.retryAfterSeconds`
   and render `"Demasiados intentos. Proba de nuevo en
   ${Math.ceil(retryAfterSeconds / 60)} minutos"`. The
   `Retry-After` HTTP header is also a fallback.
3. **Guard runs before layout rehydrates from `/me`.** On
   cold-boot with a stale cookie the guard allows `/dashboard`
   (because `isAuthenticated()` reads the store, which is true
   if the access_token cookie is present), and only then the
   layout fires `me()`. If `/me` 401s the layout's
   `me().subscribe({error: ...})` falls through to the
   interceptor's forced-logout branch. The window of stale
   dashboard is short but visible. PR11 may want a one-time
   "first paint: hide children until /me resolves" gate on
   the authenticated layout, or a `canMatch` guard that waits
   for `/me`.

### Deferred (not in the F1 frontend chain)

- `ApiService` typed wrapper (the per-endpoint service
  methods are inlined in `AuthService` / `AvailabilityService`
  / `RegistrationService` for now; a future PR can extract
  the thin wrapper if the call sites start to repeat).

