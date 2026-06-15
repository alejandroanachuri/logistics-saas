# Delta Spec: auth-shell-layout

> Capability scope (from proposal): `frontend-auth-shell` — public and
> authenticated layouts, `authGuard`, route protection, and the
> dashboard placeholder behavior.
>
> Source PRD: lines 1023-1036 (routes), 1083-1090 (dashboard
> placeholder), 1115-1120 (a11y).
>
> Backend prerequisite: the `company-user-auth` and `tenant-me`
> capabilities must be implemented for end-to-end scenarios. The
> layout / guard signal-state scenarios are unit-testable with mocks.

## Purpose

The Angular application SHALL use standalone components (no
`NgModule`s) to provide two layout shells — a `PublicLayoutComponent`
for unauthenticated routes (`/`, `/register`, `/login`) and an
`AuthenticatedLayoutComponent` for `/dashboard` — and SHALL protect
`/dashboard` with a functional `authGuard` that redirects
unauthenticated users to `/login`.

## ADDED Requirements

### Requirement: Public Layout for Unauthenticated Routes

The system SHALL wrap the routes `''`, `'register'`, and `'login'`
inside a `PublicLayoutComponent` (PRD lines 1026-1028, 1012-1013)
that renders:

- A brand header (logo / app name placeholder is acceptable in v1)
- A centered card containing the routed page content (`<router-outlet>`)
- NO top navigation menu and NO user menu

#### Scenario: Home page renders inside the public layout

- **GIVEN** the user navigates to `/`
- **THEN** the `PublicLayoutComponent` is mounted
- **AND** the document does NOT contain a user-menu element
- **AND** a brand header is visible

#### Scenario: Login page renders inside the public layout

- **GIVEN** the user navigates to `/login`
- **THEN** the `PublicLayoutComponent` is mounted around the
  `LoginComponent`
- **AND** no top navigation menu is rendered

### Requirement: Authenticated Layout for /dashboard

The system SHALL wrap the `'dashboard'` route inside an
`AuthenticatedLayoutComponent` (PRD lines 1008-1009, 1029-1033) that
renders:

- A top navigation bar with the app brand
- A user menu (showing the current user's `firstName` + a logout
  button)
- The routed page content via `<router-outlet>`

The `AuthService` SHALL resolve the current user on layout mount by
calling `GET /api/v1/auth/me` if `auth.store.isAuthenticated()` is
`true` and no `currentUser` is cached.

#### Scenario: Dashboard layout mounts the top nav and user menu

- **GIVEN** an authenticated user navigates to `/dashboard`
- **THEN** the `AuthenticatedLayoutComponent` is mounted
- **AND** the top navigation is visible
- **AND** the user menu shows `auth.store.currentUser().firstName`
- **Backend prerequisite:** `company-user-auth` `GET /auth/me`
  endpoint is implemented and the cookies are sent with
  `withCredentials: true`.

### Requirement: Functional authGuard

The system SHALL provide a functional `authGuard` (`CanActivateFn`)
that:

- Allows activation when `auth.store.isAuthenticated()` is `true`
- Redirects to `/login` (with the original URL preserved in the
  `returnUrl` query parameter) when the store reports
  unauthenticated
- Does NOT block on the result of `GET /auth/me`; the store is the
  source of truth for v1

The `authGuard` SHALL be applied to the `'dashboard'` route only
(PRD line 1031).

#### Scenario: Unauthenticated access to /dashboard redirects to /login

- **GIVEN** `auth.store.isAuthenticated()` is `false`
- **WHEN** the user navigates to `/dashboard`
- **THEN** the guard returns a `UrlTree` redirecting to
  `/login?returnUrl=/dashboard`
- **AND** the `DashboardComponent` is NOT instantiated

#### Scenario: Authenticated access to /dashboard is allowed

- **GIVEN** `auth.store.isAuthenticated()` is `true`
- **WHEN** the user navigates to `/dashboard`
- **THEN** the guard returns `true`
- **AND** the `DashboardComponent` is instantiated inside
  `AuthenticatedLayoutComponent`

### Requirement: Logout Flow

The system SHALL, when the user clicks the logout button in the
authenticated layout's user menu, call `POST /api/v1/auth/logout`
via `AuthService.logout()`, clear `auth.store` and `tenant.store`
state (set `isAuthenticated` to `false`, clear `currentUser`,
`currentTenant`), and navigate to `/` (PRD line 1088).

#### Scenario: Logout clears stores and navigates to /

- **GIVEN** an authenticated user is on `/dashboard`
- **WHEN** the user clicks `Cerrar sesión`
- **THEN** `auth.store.isAuthenticated` becomes `false`
- **AND** `auth.store.currentUser` is `null`
- **AND** `tenant.store.currentTenant` is `null`
- **AND** the router navigates to `/`
- **Backend prerequisite:** `company-user-auth` `POST /auth/logout`
  is implemented and clears the cookies.

### Requirement: Standalone Components Only

The system SHALL NOT use `NgModule` declarations for any of the v1
components, layouts, guards, or interceptors introduced by this
change. All components SHALL be declared with
`standalone: true` (Angular 21 default) and bootstrapped through
`bootstrapApplication` with the `app.config.ts` providers array
(PRD lines 967, 1016-1018).

#### Scenario: No NgModule in the v1 source tree

- **WHEN** the `frontend/src/app/**` tree is scanned
- **THEN** there are no files declaring `@NgModule(...)`
- **AND** `main.ts` calls `bootstrapApplication(AppComponent, appConfig)`

### Requirement: Dashboard Placeholder Content

The system SHALL render the `DashboardComponent` content as a
read-only summary (PRD lines 1083-1090):

- `"Bienvenido, {firstName}"` where `firstName` comes from
  `auth.store.currentUser().firstName`
- `"Tu slug es: {slug}"` where `slug` comes from
  `tenant.store.currentTenant().slug` (or `currentUser.tenantSlug`
  if the tenant store is empty in v1)
- `"Tu tenant ID es: {tenantId}"` from
  `auth.store.currentUser().tenantId`
- A `Cerrar sesión` button that triggers the logout flow

The component SHALL NOT fetch any data — it only reads from the
stores. It is explicitly a placeholder (PRD line 1090).

#### Scenario: Dashboard renders user and tenant info from the stores

- **GIVEN** `auth.store.currentUser = { firstName: "Juan", tenantId:
  "…", tenantSlug: "mvr" }`
- **WHEN** the user lands on `/dashboard`
- **THEN** the page shows `"Bienvenido, Juan"`,
  `"Tu slug es: mvr"`, and `"Tu tenant ID es: …"`
- **AND** a `Cerrar sesión` button is visible
- **AND** no HTTP request is fired on mount

### Requirement: Angular CDK Stepper Usage

The system SHALL use `@angular/cdk/stepper` (or the Angular
Material stepper that depends on it) for the registration wizard
to inherit accessibility (focus management, ARIA, keyboard
navigation) out of the box (PRD line 144).

#### Scenario: Stepper exposes the CDK stepper data attributes

- **WHEN** the wizard is rendered
- **THEN** the root stepper element has
  `aria-label="Asistente de registro"` (or the equivalent
  localized label)
- **AND** each step header has the `cdk-step-header` attribute
- **AND** the active step receives the `cdk-step-active` class
