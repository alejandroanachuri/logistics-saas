# Delta Spec: wizard-registration

> Capability scope (from proposal): `frontend-register-wizard` — 3-step
> Angular reactive form with live async validators, summary, T&C
> confirmation, and post-register success screen. NO auto-login in v1.
>
> Source PRD: lines 1038-1067 (wizard steps), 1064-1067 (post-register
> success), 1115-1120 (a11y rules), 132-146 (frontend stack).
>
> Backend prerequisite: the `tenant-registration` capability must be
> implemented (POST /api/v1/auth/register, GET slug-availability,
> GET cuit-availability) before any end-to-end scenario of this spec
> can run. The pure UI / form-validation scenarios (step navigation,
> field validators, async debounce plumbing) can be unit-tested with
> mocked HTTP, so they are not gated.

## Purpose

The Angular application SHALL provide a 3-step self-service company
registration wizard at the `/register` route that collects company
data (step 1), admin user data (step 2), and explicit T&C + privacy
consent (step 3), submits the payload to the backend, and on success
shows a confirmation screen with a link to `/login` — without
auto-logging the new user in.

## ADDED Requirements

### Requirement: Wizard Structure and Routing

The system SHALL expose a `RegisterComponent` at the `/register` route
(PRD lines 1027, 1038) that renders an Angular CDK `Stepper` with
three steps in order: `CompanyStepComponent`, `AdminStepComponent`,
`ConfirmationStepComponent`. The stepper MUST advance one step at a
time; back navigation between completed steps SHALL be allowed.

#### Scenario: First visit to /register shows step 1

- **GIVEN** an unauthenticated user navigates to `/register`
- **THEN** the `CompanyStepComponent` is the only step visible
- **AND** the stepper header shows step 1 of 3 as the active step
- **AND** steps 2 and 3 are not yet interactive

#### Scenario: Pressing Enter on a field advances the wizard

- **GIVEN** the user is on step 1 and all required fields are valid
- **WHEN** the user presses the `Enter` key while focus is inside any
  input of the current step (PRD line 1120, "Enter avanza")
- **THEN** the wizard advances to the next step
- **AND** focus moves to the first interactive control of the new step

#### Scenario: Backspace inside a text input does not navigate back

- **GIVEN** the user is on step 2 with text in the first-name field
- **WHEN** the user presses `Backspace` while editing a text input
- **THEN** the wizard remains on step 2 and one character is deleted
  from the input (PRD line 1120, "Backspace no")

### Requirement: Step 1 — Company Data Form

The system SHALL render a typed reactive `FormGroup` for the company
data with the following controls and constraints (PRD lines 1041-1048,
547-553):

- `legalName`: required, non-blank string
- `commercialName`: optional string
- `cuit`: required, 11 digits with or without hyphens, check digit
  must validate (modulo-11 algorithm) AND the value MUST NOT already
  exist in `tenants` (async validator calling
  `GET /api/v1/tenants/me/cuit-availability`)
- `taxType`: required, one of `RESPONSABLE_INSCRIPTO`, `MONOTRIBUTO`,
  `EXENTO`
- `slug`: required, 2-12 chars, lowercase ASCII letters/digits, first
  char is a letter, not in `reserved_slugs` AND not already taken
  (async validator calling
  `GET /api/v1/tenants/me/slug-availability`)
- `contactEmail`: required, RFC-5322 email format
- `contactPhone`: optional, E.164-ish string
- `address.country`: required, defaults to `AR`
- `address.province`: required, one of the 24 Argentine provinces
- `address.city`: required, non-blank
- `address.line`: required, non-blank
- `address.number`: required, non-blank
- `address.floor`: optional
- `address.apartment`: optional
- `address.postalCode`: required, non-blank

Async availability checks SHALL be debounced by 300 ms before hitting
the backend (PRD line 1045, 1053).

#### Scenario: CUIT live verifier flags a bad check digit

- **GIVEN** the user types `30-71234567-0` into the `cuit` field
- **WHEN** the field loses focus or the user pauses typing
- **THEN** the field renders the validator error
  `"CUIT inválido (dígito verificador)"`
- **AND** the control's `aria-invalid` attribute is `"true"`
- **AND** the field's `aria-describedby` references the error message
  element's id (PRD line 1117)

#### Scenario: Slug availability is debounced by 300 ms

- **GIVEN** the user types characters into the `slug` field
- **WHEN** the user types `m`, `mv`, `mvr` within 100 ms each
- **THEN** the async availability validator SHALL issue exactly ONE
  HTTP request (with the latest value `mvr`) after 300 ms of inactivity
  — not three separate requests

#### Scenario: Slug taken by another tenant shows an inline error

- **GIVEN** the async slug-availability request returns
  `available: false, reason: "SLUG_ALREADY_TAKEN"`
- **THEN** the field renders the error
  `"Ese slug ya está en uso. Probá con otro."`
- **AND** the wizard SHALL NOT allow advancing to step 2 until the
  error is resolved

#### Scenario: Tax type select exposes the three v1 values

- **WHEN** step 1 is rendered
- **THEN** the `taxType` control is a select with exactly three
  options: `Responsable Inscripto`, `Monotributo`, `Exento`
- **AND** submitting the form with `taxType` empty is blocked

#### Scenario: Province select exposes 24 Argentine provinces

- **WHEN** step 1 is rendered
- **THEN** the `address.province` control is a select with exactly 24
  options covering all Argentine provinces (PRD line 1048)
- **AND** the default selected value is `BUENOS_AIRES`

#### Scenario: Step 1 next button is disabled while form is invalid

- **GIVEN** step 1 is rendered with an empty form
- **WHEN** the user does not interact
- **THEN** the "Siguiente" button is disabled
- **AND** becomes enabled only after every required control is valid
  AND async validators (slug, CUIT) have completed without error

### Requirement: Step 2 — Admin User Data Form

The system SHALL render a typed reactive `FormGroup` for the admin
user with the following controls (PRD lines 1050-1056):

- `firstName`: required, non-blank
- `lastName`: required, non-blank
- `username`: required, 3-30 chars, lowercase, alphanum + `._-`,
  unique per tenant — async validator calls a per-tenant username
  availability check (debounced 300 ms; PRD line 1053)
- `email`: required, RFC-5322 email format
- `password`: required, ≥8 chars, ≥1 uppercase, ≥1 lowercase, ≥1 digit
- `passwordConfirmation`: required, MUST equal `password` value

The form SHALL display a live password strength indicator (PRD line
1056) with at least three tiers: `Débil`, `Aceptable`, `Fuerte`.

#### Scenario: Password strength indicator moves between tiers

- **GIVEN** the user types `a` in the password field
- **THEN** the strength indicator displays `Débil`
- **WHEN** the user extends the password to `MiPassw0rd!Seguro`
  (12 chars, all four required classes)
- **THEN** the strength indicator displays `Fuerte`

#### Scenario: Password confirmation mismatch shows an error

- **GIVEN** `password = "MiPassw0rd!Seguro"` and
  `passwordConfirmation = "MiPassw0rd!Segur"`
- **WHEN** the user blurs the confirmation field
- **THEN** the confirmation control renders
  `"Las contraseñas no coinciden"`
- **AND** the wizard SHALL NOT allow advancing to step 3

#### Scenario: Username async availability call is debounced

- **GIVEN** the user types `a`, `ad`, `adm` in the username field
  within 200 ms
- **WHEN** 300 ms elapse after the last keystroke
- **THEN** the async availability validator SHALL issue exactly ONE
  HTTP request with the latest value `adm`

### Requirement: Step 3 — Confirmation and Consent

The system SHALL render a read-only summary of the data collected in
steps 1 and 2, followed by two required checkboxes and a submit
button (PRD lines 1058-1062):

- Checkbox `acceptsTerms`: label `Acepto los términos y condiciones`
  with a link to the T&C URL (placeholder OK in v1)
- Checkbox `acceptsPrivacy`: label `Acepto la política de privacidad`
  with a link to the privacy policy URL (placeholder OK in v1)
- Submit button labeled `Crear cuenta`

Both checkboxes MUST be checked before the submit button is enabled.

#### Scenario: Submit button is disabled until both consents are given

- **GIVEN** step 3 is rendered
- **WHEN** neither consent is checked
- **THEN** the `Crear cuenta` button is disabled
- **WHEN** both consents are checked
- **THEN** the `Crear cuenta` button becomes enabled

#### Scenario: Submitting the wizard posts to the backend

- **GIVEN** all three steps are valid and both consents are checked
- **WHEN** the user clicks `Crear cuenta`
- **THEN** the wizard issues
  `POST /api/v1/auth/register` with the assembled payload
- **AND** the submit button enters a `loading` state (signal
  `isLoading = true`) and is disabled
- **Backend prerequisite:** `tenant-registration` is implemented and
  returns HTTP 201.

### Requirement: Post-Register Success State (No Auto-Login)

The system SHALL, on a successful `POST /api/v1/auth/register`
response (HTTP 201), replace the wizard with a success screen that
displays the message
`"Cuenta creada. Iniciá sesión con tu slug, usuario y contraseña."`
and a link to `/login` (PRD lines 1064-1067). The system SHALL NOT
auto-authenticate the new user; no auth cookies SHALL be set, no
`auth.store` transition SHALL fire, and the user SHALL NOT be
redirected to `/dashboard`.

#### Scenario: Success screen is shown after HTTP 201

- **GIVEN** the backend returns HTTP 201 with a `RegisterResponse`
- **WHEN** the wizard finishes processing the response
- **THEN** the success message and a `Ir a iniciar sesión` link to
  `/login` are visible
- **AND** the user is not redirected automatically
- **Backend prerequisite:** `tenant-registration` is implemented.

#### Scenario: Backend validation error is rendered inline

- **GIVEN** the backend returns HTTP 400 with envelope
  `error.code = "VALIDATION_ERROR"` and `details = { slug: "..." }`
- **WHEN** the wizard receives the response
- **THEN** the wizard returns to the appropriate step (step 1 if
  `details.slug` is present, step 2 if `details.username` or
  `details.password` is present)
- **AND** the affected field renders the server-side message inline
  (PRD line 1117 a11y rules apply)

#### Scenario: Backend 409 conflict surfaces per field

- **GIVEN** the backend returns HTTP 409 with
  `error.code = "SLUG_ALREADY_TAKEN"`
- **WHEN** the wizard receives the response
- **THEN** step 1 is shown again
- **AND** the `slug` field renders
  `"Ese slug ya está en uso. Probá con otro."`
- **Backend prerequisite:** `tenant-registration` returns 409 with the
  documented codes (PRD lines 575-581).

### Requirement: Accessibility for the Wizard

The system SHALL satisfy the accessibility rules from PRD lines
1115-1120 for every form field and control of the wizard:

- Every input has a programmatic `<label for="…">` linked by `id`
- Invalid fields carry `aria-invalid="true"`
- Error messages are linked via `aria-describedby`
- Color contrast meets WCAG AA (blue-600 on white for CTA, slate-900
  on white for text, slate-500 on white for subtext — PRD line 1111)
- Focus is visible (Tailwind default ring)

#### Scenario: Each input has a programmatic label

- **WHEN** the wizard is rendered
- **THEN** every `<input>`, `<select>`, and `<textarea>` has a
  matching `<label [for]>` that points to its `id`
- **AND** no input is labeled only by placeholder text

#### Scenario: Keyboard-only user can complete the wizard

- **GIVEN** a user navigates using only Tab, Shift+Tab, and Enter
- **WHEN** the user fills in valid values for all three steps and
  accepts both consents
- **THEN** the user can submit the form via Enter on the submit
  button without using a mouse (PRD line 1120)
