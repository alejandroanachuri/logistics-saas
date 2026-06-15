# Delta Spec: login-page

> Capability scope (from proposal): `frontend-login` — `slug + username
> + password` form with error mapping, lockout copy, and a disabled
> "forgot password" link with tooltip.
>
> Source PRD: lines 1069-1081 (login screen), 1078-1081 (error states).
>
> Backend prerequisite: the `company-user-auth` capability must be
> implemented (POST /api/v1/auth/login returning the cookie envelope
> and the documented error codes) before any end-to-end scenario of
> this spec can run. The pure form-validation and signal-state
> scenarios can be unit-tested with mocked HttpClient and are not
> gated.

## Purpose

The Angular application SHALL provide a `LoginComponent` at the
`/login` route that authenticates a company user with
`(slug, username, password)`, surfaces backend errors as
user-friendly Spanish copy, and on success transitions the
`auth.store` to authenticated and navigates the user to `/dashboard`.

## ADDED Requirements

### Requirement: Login Form Structure

The system SHALL render a typed reactive `FormGroup` at `/login` with
three controls (PRD lines 1071-1076):

- `slug`: required, matches backend slug format (lowercase
  letters/digits, 2-12 chars, starts with a letter). Placeholder
  `mvr`.
- `username`: required, non-blank. The field is labeled `Usuario`.
- `password`: required, non-blank. The field has a show/hide toggle
  button (PRD line 1073).

The submit button is labeled `Ingresar` and is disabled while the
form is invalid or while a submit is in flight.

#### Scenario: Form is rendered with the documented placeholders

- **WHEN** the user navigates to `/login`
- **THEN** the `slug` input shows placeholder `mvr`
- **AND** the `username` input has no placeholder
- **AND** the `password` input has a `Mostrar`/`Ocultar` toggle
  button adjacent to it

#### Scenario: Password show/hide toggle flips input type

- **GIVEN** the password input is rendered with `type="password"`
- **WHEN** the user clicks the toggle button
- **THEN** the input's `type` becomes `"text"` and the toggle's
  `aria-label` updates to `Ocultar contraseña`
- **AND** clicking again returns it to `type="password"`

#### Scenario: Submit button is disabled while form is invalid

- **GIVEN** any of the three required controls is empty or invalid
- **THEN** the `Ingresar` button is disabled
- **AND** becomes enabled only when all three controls are non-blank
  and `slug` matches the format

### Requirement: Successful Login Transition

The system SHALL, on a successful `POST /api/v1/auth/login` response
(HTTP 200), update the `AuthStore` signal-based state to
`isAuthenticated = true`, populate `currentUser` from the response
body, and navigate the user to `/dashboard` (PRD lines 1030-1033,
1029-1033 router config). While the request is in flight, the
`isLoading` signal SHALL be `true` and the submit button SHALL be
disabled.

#### Scenario: Successful login navigates to /dashboard

- **GIVEN** the user submits valid `(slug, username, password)`
- **WHEN** the backend responds HTTP 200 with the user body
- **THEN** `auth.store.isAuthenticated` is `true`
- **AND** `auth.store.currentUser` is populated with the response
- **AND** the router navigates to `/dashboard`
- **Backend prerequisite:** `company-user-auth` returns HTTP 200 with
  the documented body shape and sets `access_token` /
  `refresh_token` httpOnly cookies.

#### Scenario: Submit button shows loading state while in flight

- **GIVEN** the user clicks `Ingresar` with a valid form
- **WHEN** the HTTP request is pending
- **THEN** `auth.store.isLoading` is `true`
- **AND** the `Ingresar` button is disabled and renders the label
  `Ingresando…`

### Requirement: Error Mapping to User-Facing Copy

The system SHALL map backend error responses from
`POST /api/v1/auth/login` to the user-facing Spanish copy required
by PRD lines 1078-1081:

- `401 INVALID_CREDENTIALS` →
  `"Las credenciales no son correctas"`
- `403 ACCOUNT_LOCKED` →
  `"Demasiados intentos. Probá de nuevo en {X} minutos"`
  (where `X` is derived from the `Retry-After` header or a `details`
  field; if absent, render `"en unos minutos"`)
- Network error / status 0 →
  `"No pudimos conectarnos. Reintentá"`

The error message SHALL be rendered in a live region (`role="alert"`
or `aria-live="polite"`) so screen readers announce it.

#### Scenario: Invalid credentials copy is shown

- **GIVEN** the user submits invalid credentials
- **WHEN** the backend responds HTTP 401 with
  `error.code = "INVALID_CREDENTIALS"`
- **THEN** the form renders the message
  `"Las credenciales no son correctas"` in an `aria-live="polite"`
  region
- **AND** the form is NOT reset — the user can correct and resubmit
- **Backend prerequisite:** `company-user-auth` returns 401 with the
  documented envelope.

#### Scenario: Lockout copy includes a minutes value when available

- **GIVEN** the user submits valid credentials to a locked account
- **WHEN** the backend responds HTTP 403 with
  `error.code = "ACCOUNT_LOCKED"` and a `details.minutesRemaining` or
  `Retry-After` value of `15`
- **THEN** the form renders
  `"Demasiados intentos. Probá de nuevo en 15 minutos"`
- **Backend prerequisite:** `account-lockout-and-rate-limit` and
  `company-user-auth` return `ACCOUNT_LOCKED` with a recoverable
  minutes value (gap carried into sdd-design if not yet present).

#### Scenario: Network error renders the connectivity copy

- **GIVEN** the user clicks `Ingresar`
- **WHEN** the HTTP request fails with status 0 or a `ProgressEvent`
  type error
- **THEN** the form renders `"No pudimos conectarnos. Reintentá"`
- **AND** the `Ingresar` button returns to the enabled state

### Requirement: Auxiliary Links

The system SHALL render two auxiliary links on the login page (PRD
lines 1075-1076):

- `"¿No tenés cuenta? Registrate"` — links to `/register`. This link
  SHALL be enabled.
- `"¿Olvidaste tu contraseña?"` — MUST be rendered as a disabled
  element (`<button disabled>` or `<a aria-disabled="true">`) with a
  tooltip that reads exactly `"Próximamente"`. Hovering or focusing
  the element MUST surface the tooltip. Clicking it MUST NOT navigate.

#### Scenario: "Registrate" link is enabled and routes to /register

- **GIVEN** the login page is rendered
- **WHEN** the user clicks the `Registrate` link
- **THEN** the router navigates to `/register`

#### Scenario: "Olvidaste tu contraseña" is disabled with tooltip

- **GIVEN** the login page is rendered
- **THEN** the `Olvidaste tu contraseña` element has
  `aria-disabled="true"` and a `tabindex="-1"` (not focusable in
  normal tab order)
- **WHEN** the user hovers or focuses the element
- **THEN** a tooltip with the exact text `"Próximamente"` is
  displayed
- **AND** clicking the element does not navigate
