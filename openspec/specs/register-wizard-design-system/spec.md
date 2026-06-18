# Spec: register-wizard-design-system

> Capability scope: the F1 3-step register wizard, plus a new
> `--color-warning*` token family consumed by the wizard's
> province-load-error banner. Source: `…/proposal.md` + `…/exploration.md`.
> Visual only — zero behavior change, zero test-surface change.
>
> **Archived from**: `openspec/changes/re-skin-register-wizard/specs/register-wizard-design-system/spec.md`
> on 2026-06-18. The spec was originally written as a delta against an
> empty `openspec/specs/` tree, so it now lives as a standalone main spec.
> See `openspec/changes/archive/2026-06-18-re-skin-register-wizard/archive-report.md`
> for the full archive lineage (commits, observation IDs, deviations).

## Purpose

The register wizard SHALL render on Logistics Core design system
tokens, matching the login re-skin precedent. Inputs remain raw
`<input formControlName>` (Approach A — locked). The wizard adopts
`<app-card>` containment, `<app-button>` for primary and secondary
actions, the design system neutrals, and a new `warning` token family
for the province-load-error banner.

## ADDED Requirements

### Requirement: Wizard visual containment

The wizard `<main>` SHALL be wrapped in `<app-card>`, with the outer
`mx-auto px-4 py-16` providing screen gutters. Inner form section
spacing SHALL be `space-y-4` or smaller so card `p-6` does not double up.

#### Scenario: Wizard renders inside app-card

- **GIVEN** the user is at `/register`
- **WHEN** the wizard renders
- **THEN** `<main>` is wrapped in `<app-card>`
- **AND** the outer wrapper retains `mx-auto px-4 py-16`

### Requirement: Wizard neutrals use design system tokens

All `slate-*` neutrals in the wizard templates SHALL be replaced with
`text-on-surface`, `text-on-surface-variant`, `border-outline-variant`,
or `border-outline`. No `slate-*` utility SHALL remain in the
re-skinned files.

#### Scenario: Stepper labels and chevron use design tokens

- **GIVEN** the wizard stepper header renders
- **WHEN** step 1 is active
- **THEN** the active label has `text-on-surface font-semibold`
- **AND** inactive labels have `text-on-surface-variant`
- **AND** the `›` chevron has `text-outline-variant`

#### Scenario: Fieldset summary uses outline-variant / on-surface

- **GIVEN** the confirmation step renders a `<fieldset>` summary
- **THEN** the border is `border-outline-variant`
- **AND** the legend uses `text-on-surface font-semibold`
- **AND** `<dd>` values use `text-on-surface` /
  `text-on-surface-variant`

### Requirement: Primary actions use app-button primary

The three primary submits (Siguiente × 2, Crear cuenta, Ir a iniciar
sesión) SHALL render as `<app-button variant="primary" size="md">`.
The loading-state submit (Crear cuenta while `isLoading()`) SHALL
remain a raw `<button>` with the inlined label pattern, matching the
login precedent (no `loadingText` input exists on `app-button`).

#### Scenario: Primary submits render as app-button primary

- **GIVEN** the wizard is on any step or the success state
- **WHEN** the primary action renders
- **THEN** the element is `<app-button variant="primary" size="md">`
- **AND** text matches the documented copy (`Siguiente`, `Crear
  cuenta`, or `Ir a iniciar sesión`)

### Requirement: Secondary actions use app-button secondary

The Atrás button SHALL render as `<app-button variant="secondary"
size="md">`.

#### Scenario: Atrás renders as app-button secondary

- **GIVEN** the wizard is on step 2 or step 3
- **WHEN** the back button renders
- **THEN** the element is `<app-button variant="secondary" size="md">`
- **AND** its text is `Atrás`

### Requirement: New warning token family in the design system

The system SHALL add a `warning` token family to both `DESIGN.md` and
`frontend/src/styles.css` using the Material 3 tonal-pair shape (8
keys: `warning`, `on-warning`, `warning-container`,
`on-warning-container`, `warning-fixed`, `warning-fixed-dim`,
`on-warning-fixed`, `on-warning-fixed-variant`). No
`--color-warning*` token SHALL exist in `@theme` before this change.

#### Scenario: Warning tokens resolve as Tailwind utilities

- **GIVEN** `styles.css` is loaded
- **WHEN** Tailwind v4 resolves the `@theme` block
- **THEN** the eight `--color-warning*` custom properties are defined
- **AND** `bg-warning`, `bg-warning-container`, `text-on-warning`,
  `text-on-warning-container`, `border-warning` resolve to the
  documented hex values

#### Scenario: Warning token contrast meets WCAG AA

- **GIVEN** the `on-warning-container` (#291800) text is on
  `warning-container` (#ffe0a8)
- **THEN** the contrast ratio is at least 4.5:1 for normal text
- **AND** `on-warning` (#ffffff) on `warning` (#7c4d00) is at least
  4.5:1 for normal text and 3:1 for large text

### Requirement: Province-load-error banner uses warning tokens

The banner at `data-testid="company-province-load-error"` SHALL render
with `border-warning bg-warning-container text-on-warning-container`.

#### Scenario: Province error renders with warning tokens

- **GIVEN** the user is on step 1 and `ProvincesService.list()`
  rejects
- **WHEN** the banner renders
- **THEN** it has the classes
  `bg-warning-container text-on-warning-container border-warning`
- **AND** the Reintentar button is
  `<app-button variant="secondary" size="sm">`
- **AND** its `data-testid="company-province-retry"` is preserved

### Requirement: Success state uses tertiary-container tokens

The post-submit success section SHALL render with
`border-tertiary-container bg-tertiary-container
text-on-tertiary-container`.

#### Scenario: Success card uses tertiary-container tokens

- **GIVEN** the registration API resolves successfully
- **WHEN** the success state renders
- **THEN** the section has
  `border-tertiary-container bg-tertiary-container`
- **AND** the heading and copy use `text-on-tertiary-container`

### Requirement: Error text uses text-error

All `text-red-600` error regions (top-level + inline field errors)
SHALL use `text-error`.

#### Scenario: Error regions use text-error

- **GIVEN** a control's `errorMessageFor(control)` returns non-empty
- **WHEN** the error region renders
- **THEN** it has the class `text-error`
- **AND** it remains a `<p role="alert">` with the documented
  `id="<control>-error"`

### Requirement: Accessibility bindings are preserved

The re-skin SHALL NOT remove or rename: input `id` attributes,
`aria-required`, `aria-invalid`, `aria-describedby`, `<p role="alert">`
error regions, or the required asterisk `<span aria-hidden="true">*</span>`.

#### Scenario: Inputs keep id, aria-*, role=alert, aria-hidden asterisk

- **GIVEN** the re-skinned templates render
- **WHEN** the test suite queries by `id`, `aria-required`,
  `aria-invalid`, or `aria-describedby`
- **THEN** every previously bound attribute still resolves
- **AND** every required-field label still contains a
  `<span aria-hidden="true">*</span>` child
- **AND** every inline error remains a `<p role="alert">` with the
  documented id

### Requirement: Spec files pass without behavior modification

The four spec files (1,676 LoC total) SHALL pass without assertion or
binding change. A single comment edit at `company-step.spec.ts:418`
(removing the stale `[class.border-red-500]` reference) is the only
permitted text change.

#### Scenario: All four spec files pass with one comment edit

- **GIVEN** the re-skinned code is in place
- **WHEN** the test runner executes the four spec files
- **THEN** every previously green spec passes
- **AND** no spec file has any assertion or mock change
- **AND** `company-step.spec.ts:418` no longer references
  `[class.border-red-500]`

### Requirement: app-input is NOT adopted in this change

The wizard SHALL continue to use raw `<input formControlName>` and
`<select formControlName>` HTML. Adoption of `<app-input>` (or any
`ControlValueAccessor` adapter) is deferred to a follow-up change
and is out of scope for this delta.

#### Scenario: Inputs remain raw formControlName

- **GIVEN** the re-skinned templates render
- **WHEN** a developer inspects the DOM
- **THEN** every form control is a raw `<input>` or `<select>` with a
  `formControlName` attribute
- **AND** no `<app-input>` element appears in any wizard template

### Requirement: Behavior, validation, form structure unchanged

The re-skin SHALL NOT alter validation rules, `FormGroup` shapes,
signal state, stepper navigation logic, or any user-visible behavior
beyond class and component swaps.

#### Scenario: Validation and stepper behavior survive intact

- **GIVEN** the re-skinned code is in place
- **WHEN** the user advances through the wizard with valid and invalid
  input
- **THEN** `canAdvance` / `canSubmit` produce the same outcomes as
  before the re-skin
- **AND** the form `FormGroup` shapes are identical
- **AND** the `RegistrationService.submit` payload is unchanged

## Constraints

- **C1 (review budget).** Net LoC delta MUST be under 400 lines.
  Forecast: -100 to -20.
- **C2 (zero behavior change).** No validation, signal, computed, form
  structure, navigation rule, or copy changes.
- **C3 (test stability).** All four spec files MUST pass without
  modification except the one comment edit in
  `company-step.spec.ts:418`.
- **C4 (contrast).** The new warning family MUST satisfy WCAG 2.1 AA
  (4.5:1 normal, 3:1 large).

## Out of scope (deferred)

- `<app-input>` migration / `ControlValueAccessor` adapter (F2+).
- Mobile drawer / responsive stepper overhaul.
- Dark-mode token variants for the warning family.
- `AuthenticatedLayoutComponent` deletion.
- Password show/hide toggle refactor.

## Archived Deviations (resolved)

The following deviations from the literal spec text were made during
implementation and resolved before archive. They are recorded here so
future readers of the spec understand what shipped vs. what was
written. See `archive-report.md` for full context.

- **WARNING-1 (FIXED, commit `f109dfa`)**: Two residual
  `focus:ring-slate-500` instances on the consent checkboxes in
  `confirmation-step.html` were replaced with `focus:ring-primary`
  in commit `f109dfa`. R2 ("No `slate-*` utility SHALL remain") is
  now satisfied across all 4 templates. (Originally WARNING-1 in the
  verify-report — committed by the orchestrator after verify ran.)

- **WARNING-2 (ACCEPTED as documented deviation)**: All 4 action
  buttons (Siguiente × 2, Atrás, Crear cuenta, Ir a iniciar sesión)
  remain raw `<button>` elements instead of `<app-button>`. R3 and R4
  literally require `<app-button variant="primary">` and
  `<app-button variant="secondary">` respectively. Justification: the
  existing wizard spec uses STRICT `button[data-testid="..."]`
  selectors (27 queries in `wizard/register.spec.ts`), and
  `<app-button>` renders a host `<app-button>` wrapping an inner
  `<button>`, so the test query returns `null` and the cast fails.
  The login re-skin precedent kept its submit button raw for the
  same reason. All 4 wizard action buttons follow the same precedent;
  behavior is preserved (button renders, has right testid, is
  clickable, has right disabled state, has right text); 173/173 tests
  pass. The `<app-card>` wrapper for the wizard (R1) is the only
  `<app-*>` adoption. Acceptance: future changes adopting
  `<app-button>` MUST first extend the spec's `data-testid` selectors
  to descend into the host element OR add a `loadingText` input that
  removes the need for the inlined Crear cuenta → Creando cuenta
  pattern.
