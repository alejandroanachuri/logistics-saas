# Exploration: re-skin register wizard

## Current state

The F1 register wizard is a 3-step linear flow rendered as a **signal-based custom stepper** (no `@angular/cdk/stepper`). All 3 step components stay in the DOM at all times and toggle visibility via `[class.hidden]`. Form validity gates navigation through `canAdvance` / `canSubmit` computeds that read a `formTick` signal bumped on every form `statusChanges`.

**Layout.** Wizard container is `<main class="w-full max-w-3xl">` (768px cap). Each step body is a `<section class="w-full max-w-2xl">` (672px). The stepper header is an `<ol>` of 3 labels (Empresa / Administrador / Confirmación) with chevron separators and a blue active label. The success screen is a green-bordered `<section>` with a "Ir a iniciar sesión" button.

**Forms.** 3 reactive `FormGroup`s with typed shapes:
- **company-step** (15 controls, 1 nested `address` group): legalName, commercialName, cuit (mod-11 + async availability), taxType (select), slug (async availability), contactEmail, contactPhone, address{country,province,city,line,number,floor,apartment,postalCode}. 8 inputs + 2 selects.
- **admin-step** (6 controls): firstName, lastName, username (per-tenant async availability), email, password (show/hide), passwordConfirmation (cross-field `matchValidator`). 6 inputs + 1 password toggle button.
- **confirmation-step** (2 controls, read-only summary inputs): acceptsTerms, acceptsPrivacy (both `requiredTrue`). Renders company + admin summary as `<dl>` grids. **No password is ever echoed back** (security).

**Validation wiring.** Per-step `errorMessageFor(control)` returns the server error from the `fieldErrors` input first, then the local validator error gated on `touched`/`dirty`. `shouldShowError(control)` returns true for server errors regardless of touched state. The wizard projects the relevant subset of `fieldErrors` per step via `projectedFieldErrorsForStep(stepIndex)` (gap #1 fix).

**Province load error.** 2026-06-16 follow-up surfaces an amber-bordered banner with a "Reintentar" button when `ProvincesService.list()` fails (`data-testid="company-province-load-error"` / `company-province-retry`).

**Styling today.** All Tailwind utility soup — `slate-50`/`slate-100`/`slate-200`/`slate-300`/`slate-500`/`slate-600`/`slate-700`/`slate-800`/`slate-900` for neutrals, `bg-slate-900` for primary buttons, `border-red-500` / `text-red-600` for errors, `bg-green-50` / `border-green-200` / `text-green-800/900` for the success card, `border-amber-300` / `bg-amber-50` / `text-amber-900` for the province-error banner. No shared primitives in use.

**Test surface.** 4 spec files (`wizard/register.spec.ts` = 573 LoC, `steps/company-step.spec.ts` = 505, `steps/admin-step.spec.ts` = 401, `steps/confirmation-step.spec.ts` = 197). Tests bind exclusively to: `data-testid` attributes, element `id` attributes, `role` attributes, `aria-*` attributes (`aria-required`, `aria-invalid`, `aria-describedby`, `aria-live`, `aria-pressed`, `aria-label`), `textContent` (with `toContain`), and the `fieldErrors()` signal. **No test asserts on class names, no `toHaveClass`, no `outerHTML`/`innerHTML` snapshots, no computed-style reads.** This is critical — it means class-name swaps are invisible to the spec.

## Files affected

| File | LoC | Current styling | What needs to change |
|------|-----|-----------------|----------------------|
| `frontend/src/app/features/register/wizard/register.html` | 177 | Tailwind utility soup: `slate-900` buttons, `slate-300` borders, `green-200/50/800/900` success card, `red-600` error copy | Wrap `<main>` in `app-card`; swap `slate-300/500/600/700/800/900` → `outline-variant`/`on-surface-variant`/`on-surface`; swap `bg-slate-900` → `app-button variant="primary"`; swap `slate-300` borders → `outline-variant`; swap `green-200/50/800/900` → `tertiary-container`/`on-tertiary-container`; swap `red-600` → `text-error`; swap stepper header `slate-900/500/300` → `on-surface`/`on-surface-variant`/`outline-variant` |
| `frontend/src/app/features/register/wizard/register.ts` | 425 | None (TS only, template owns the markup) | No logic change. May add `ButtonComponent` to `imports[]` if stepper buttons migrate to `app-button`. |
| `frontend/src/app/features/register/steps/company-step.html` | 340 | Tailwind utility soup: `slate-300/500/600/700/900` on inputs/labels, `red-500/600` for error border/copy, `amber-300/400/50/100/500/900` for province-error banner | Swap 8 inputs + 2 selects → `app-input` (with `error` input for the `errorMessageFor` output, `required` for the asterisk); swap `red-500` border → handled by `app-input` `error` prop; swap `slate-700` labels → `text-on-surface`; swap `amber-*` province-error banner → `tertiary-container`/`on-tertiary-container` (or new error variant); swap `slate-900` Siguiente button → `app-button variant="primary"` |
| `frontend/src/app/features/register/steps/company-step.ts` | 409 | None (TS only) | Add `InputComponent` to `imports[]`. No logic change. (Note: spec line 418 says "The red border is applied via `[class.border-red-500]`" — this comment becomes stale on re-skin; update the comment but the test asserts on `shouldShowError()` boolean, not the class.) |
| `frontend/src/app/features/register/steps/admin-step.html` | 167 | Tailwind utility soup: `slate-300/500/600/700/900` on inputs/labels, `red-500/600` errors, `slate-900` button | Swap 6 inputs + the password toggle (or keep the toggle as a ghost button) → `app-input`; swap `red-500` border → handled by `app-input`; swap `slate-700` labels → `text-on-surface`; swap `slate-900` Siguiente button → `app-button variant="primary"` |
| `frontend/src/app/features/register/steps/admin-step.ts` | 341 | None (TS only) | Add `InputComponent` to `imports[]`. No logic change. |
| `frontend/src/app/features/register/steps/confirmation-step.html` | 63 | Tailwind utility soup: `slate-200/500/700/900` on summary fieldsets, `slate-300/900` on checkboxes | Keep `<dl>`/`<dt>`/`<dd>` markup (semantically correct for key/value summary); swap `slate-200` fieldset borders → `outline-variant`; swap `slate-500/700/900` → `on-surface-variant`/`on-surface`; swap checkbox `slate-300/900` → `outline-variant`/`primary`; swap the underlines + "Términos"/"Privacidad" `slate-900` → `text-primary` |
| `frontend/src/app/features/register/steps/confirmation-step.ts` | 146 | None (TS only) | No change. (No form controls to wrap in `app-input`.) |
| `frontend/src/app/features/register/wizard/register.spec.ts` | 573 | — | **No change expected.** Tests bind to `data-testid`/ids/aria/text. If the re-skin moves the success screen copy into a header slot of `app-card`, double-check `data-testid="register-success"` stays on the visible region. |
| `frontend/src/app/features/register/steps/company-step.spec.ts` | 505 | — | **No change expected.** Tests bind to `data-testid`/ids/aria/text. Update the stale comment at line 418 about `[class.border-red-500]` (the assertion is on `shouldShowError()`, not the class). |
| `frontend/src/app/features/register/steps/admin-step.spec.ts` | 401 | — | **No change expected.** Tests bind to `data-testid`/ids/aria/text. |
| `frontend/src/app/features/register/steps/confirmation-step.spec.ts` | 197 | — | **No change expected.** Tests bind to host data only. |

## Design system mappings

| Current element | Current classes | Target |
|----------------|-----------------|--------|
| Wizard `<main>` (success + form) | `w-full max-w-3xl` | `<app-card elevated>` (or just `<app-card>` if elevated looks too heavy for a wizard) — the success state already feels card-like; the form state should feel card-like too |
| H1 ("Registrar empresa") | `text-2xl font-semibold text-slate-900` | `text-2xl font-semibold text-on-surface` (DS `headline-sm` mapped to Tailwind) |
| H1 subtitle | `text-sm text-slate-600` | `text-sm text-on-surface-variant` |
| Stepper labels (active) | `text-slate-900 font-semibold` | `text-on-surface font-semibold` |
| Stepper labels (inactive) | `text-slate-500` | `text-on-surface-variant` |
| Stepper chevron `›` | `text-slate-300` | `text-outline-variant` |
| Stepper active background (optional) | (none today) | `bg-surface-container` if we want a pill highlight |
| Success card (green) | `border-green-200 bg-green-50`, copy `text-green-800/900` | `border-tertiary-container bg-tertiary-container text-on-tertiary-container` (uses the success token family) |
| Success H2 | `text-green-900` | `text-on-tertiary-container` |
| Success "Ir a iniciar sesión" button | `bg-slate-900 text-white` | `<app-button variant="primary" size="md">` |
| Top-level error region (3 places) | `text-red-600` | `text-error` |
| Field labels | `text-sm font-medium text-slate-700` | `text-sm font-medium text-on-surface` (drop the manual `*` `<span class="text-red-600">` — `app-input` renders the asterisk via its own `required` prop) |
| Text inputs / selects (8+6+0) | `border-slate-300 ... focus:border-slate-500 focus:ring-slate-500` | `<app-input inputId label type [value] (valueChange) [error] [required]>` (the `app-input` component handles label, required asterisk, error border, error copy) |
| Password show/hide toggle | `text-slate-600 hover:text-slate-900` | `<app-button variant="ghost" size="sm">` (the login re-skin kept it as a raw button; we should match for consistency) |
| Siguiente / Crear cuenta buttons (3 places) | `bg-slate-900 ... hover:bg-slate-800 ... disabled:bg-slate-300` | `<app-button variant="primary" size="md" [disabled]>` (one per occurrence) |
| Atrás button | `border-slate-300 bg-white text-slate-700` | `<app-button variant="secondary" size="md" [disabled]>` |
| Siguiente disabled state | `disabled:bg-slate-300` | handled by `app-button` (`disabled:opacity-50`) |
| Province-error banner | `border-amber-300 bg-amber-50 text-amber-900` | `border-tertiary-container bg-tertiary-container text-on-tertiary-container` (or a dedicated warning tone if we add one in F2) |
| Province-error "Reintentar" button | `border-amber-400 bg-white text-amber-900` | `<app-button variant="secondary" size="sm">` (the visual style fits secondary; the warning is carried by the container) |
| Summary fieldsets (Confirmation) | `border-slate-200`, copy `text-slate-500/900` | `border-outline-variant`, `text-on-surface-variant`/`text-on-surface` (keep the `<fieldset>`+`<legend>` — it's semantically right for a key/value summary) |
| Consent checkboxes | `border-slate-300 text-slate-900` | `border-outline-variant text-primary` (DS uses primary as the checked color) |
| Consent link underlines | `text-slate-900 underline` | `text-primary hover:underline` (links should use the brand color) |
| Fieldset legend ("Empresa", "Administrador", "Domicilio", "Consentimientos") | `text-sm font-semibold text-slate-700` | `text-sm font-semibold text-on-surface` |
| Step H2 ("Datos de la empresa", etc.) | `text-xl font-semibold text-slate-900` | `text-xl font-semibold text-on-surface` |

## LoC estimate

| File | Net LoC change (rough) | Notes |
|------|------------------------|-------|
| `register.html` | **-20 to -10** | Remove duplicated `slate-*` utilities; add `<app-card>` wrapper (~3 LoC), 4 `app-button` instances (~12 LoC). Net: shorter, but only marginally because of the new component tags. |
| `register.ts` | +1 | Add `ButtonComponent` to `imports[]`. |
| `company-step.html` | **-60 to -40** | 10 inputs collapse into `<app-input>` (saves ~5 LoC each: aria bindings, error borders, manual asterisks, error copy). Siguiente → `app-button` (~-3 LoC). |
| `company-step.ts` | +1 | Add `InputComponent` to `imports[]`. |
| `admin-step.html` | **-40 to -30** | 6 inputs collapse similarly. Password toggle kept as raw button (login precedent). Siguiente → `app-button` (~-3 LoC). |
| `admin-step.ts` | +1 | Add `InputComponent` to `imports[]`. |
| `confirmation-step.html` | **-5** | Class swaps; structure unchanged. |
| `confirmation-step.ts` | 0 | No change. |
| `company-step.spec.ts` comment fix | 0 (comment only) | Line 418 references `[class.border-red-500]` which won't exist anymore. |
| **Total template + TS LoC delta** | **~ -120 to -60 net** | Roughly **-80 LoC net** after adding component imports. The new `app-button` / `app-input` tags are slightly more verbose than raw `<button>`/`<input>` but eliminate ~4 attribute lines each. |

Spec files: **0 LoC change expected**. The single stale comment fix in `company-step.spec.ts` is a docs nit, not a code change.

## Risks

1. **`app-input` is a "dumb" wrapper — no FormControl integration.** The component takes `value` and emits `valueChange`; consumers drive the wiring. The login re-skin kept raw `<input formControlName>` HTML and only adopted `app-card` (so the design system step on login was a token-swap, not a component swap). For the wizard we have **two reasonable approaches**:
   - **Approach A (token swap only — matches login):** Keep raw `<input formControlName>` HTML, swap the classes. Lowest risk, smallest LoC, but doesn't actually use `app-input`. Sets a precedent that `app-input` is unused in production.
   - **Approach B (use `app-input` with a small adapter):** Wrap the 14 inputs in a `ControlValueAccessor` adapter or duplicate the `value`/`valueChange` wiring per field. More idiomatic, but ~+15-20 LoC per input site (or one shared `FormControlInputComponent` adapter, ~+60 LoC once).
   - **Recommendation: ask the user.** Approach A is consistent with the prior re-skin. Approach B is the right long-term answer.
2. **The province-error banner currently uses `amber-*` (yellow).** The design system has no warning token. Options: (a) re-use `tertiary-container` (green — semantically wrong for an error), (b) re-use `error-container` (red — semantically wrong for a recoverable warning), (c) add a new `--color-warning` token. **Flag this as a small design decision.**
3. **The success card uses `green-*` (tertiary-container) — fits the token family cleanly.** No risk.
4. **`app-button` does not support the loading text swap natively** ("Ingresando..." vs "Ingresar"). The login re-skin inlined `{{ isLoading() ? 'Ingresando...' : 'Ingresar' }}` inside a raw `<button>` and skipped `app-button` for the submit. The wizard's "Crear cuenta" button has the same pattern (`isLoading()` swaps to "Creando cuenta..."). **Same risk as login — keep the submit button raw, or extend `app-button` with a `loadingText` input.**
5. **`app-button` is a `<button>` with `type` defaulted to `'button'`.** The wizard's 3 submit buttons are `type="button"` (driven by the stepper's `next()` / `submit()` click handlers, not form submit). The Atrás button is also `type="button"`. **No risk** if we pass `type="button"` explicitly.
6. **Layout: `app-card` applies `p-6` padding (24px).** The current wizard uses no padding on the success state and `space-y-6` on form internals. If we wrap the form in `app-card`, we need to verify the `max-w-3xl` wizard + `p-6` card padding don't double up. The login precedent is `<main class="mx-auto w-full max-w-md px-4 py-16">` → `<app-card>` (padded inside) — the outer `px-4 py-16` provides screen gutters. **Match the login precedent.**
7. **The `app-card` header projection (`<div card-header>`) renders a 1px border-bottom.** Not used in the wizard today. Don't introduce it for H1 — the title belongs in the card body.
8. **Test brittleness: 0 confirmed.** Verified across all 4 spec files. No class-name assertions, no snapshot tests, no `outerHTML` reads. Tests bind to `data-testid` (preserved on every test-bound element), `id` attributes (preserved on every input), `role` and `aria-*` (preserved or moved into `app-input`/`app-button`), and `textContent` (preserved — labels and copy are unchanged).
9. **Accessibility: `app-input` renders the required asterisk as `<span class="text-error" aria-hidden="true">*</span>` and the `aria-required` attribute.** This matches the current PR13 pattern verbatim — the existing tests on `aria-required="true"` and the asterisk `<span aria-hidden="true">` will pass against the new component.
10. **Chained-PR budget: a single PR that touches 4 templates + 3 TS files is ~150-200 LoC net delta** (negative direction). Well under the 400-LoC review budget. **No chained-PR split needed.** This is one PR.

## Test surface to preserve

| Spec | LoC | What it binds to |
|------|-----|------------------|
| `wizard/register.spec.ts` | 573 | `data-testid` on every interactive element (`stepper-next`, `stepper-prev`, `submit-create`, `loading`, `register-error`, `register-success`, `go-login`, `step-body-*`, `step-label-*`); `textContent` of step labels (`'Empresa'`, `'Administrador'`, `'Confirmación'`); form control `.setValue()` / `.patchValue()`; `component.currentStepIndex()`; `RegistrationService.submit` mock call args |
| `steps/company-step.spec.ts` | 505 | `id` on every input (`#company-legal-name`, etc.); `data-testid` on `company-province-load-error`, `company-province-retry`, `company-tax-type`, `company-province`; `<p id="...-error" role="alert">` for inline error copy; `aria-required`, `aria-invalid`, `aria-describedby`; `aria-hidden="true"` on the required asterisk `<span>`; `label[for="..."]` query; `fieldErrors()` signal |
| `steps/admin-step.spec.ts` | 401 | Same pattern as company: `id` on inputs, `aria-*`, `aria-label` on the password toggle, `aria-pressed`; `<p id="...-error" role="alert">`; `fieldErrors()` signal |
| `steps/confirmation-step.spec.ts` | 197 | `data-testid` on `consent-terms` / `consent-privacy`; `form.controls.acceptsTerms.setValue(true)`; computed signal values `companySummary()` / `adminSummary()` |

**Net: every spec binding survives a class-only swap. The only re-skin hazard is the `app-input` integration approach (Approach A vs B) and the province-error color decision.**

## Ready for proposal

**Yes — with two clarifying questions for the user before sdd-propose:**

1. **Token-swap only (Approach A, matches the login re-skin) or full `app-input` adoption (Approach B)?** The login re-skin only adopted `app-card` and kept raw `<input>` HTML. If we want the wizard to match, go A. If we want to actually use the design system primitives, go B (and decide whether to introduce a shared `FormControlInputComponent` adapter or duplicate wiring per field).
2. **Province-error warning color:** re-use `tertiary-container` (green) / `error-container` (red) / add a new warning token / accept the current `amber-*` as a one-off.

Once those are decided, the proposal is a single PR (~150-200 LoC net delta, well under the 400-LoC review budget) that touches 4 templates + 3 TS files (imports only) + 1 stale comment in `company-step.spec.ts`. No chained-PR split. No backend touch. No behavior change. No test changes beyond the comment.
