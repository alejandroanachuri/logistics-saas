# Tasks: re-skin register wizard

> Implementation breakdown for the F1 3-step register wizard re-skin. Source: `proposal.md`, `specs/register-wizard-design-system/spec.md`, `exploration.md`. Approach A locked (token-swap only, no `app-input` adoption). New `--color-warning*` token family added to the design system.
> 7 atomic work units, each one commit, each one green at the 173/173 test baseline.

## Review Workload Forecast

| Metric | Value | Budget | Status |
|--------|-------|--------|--------|
| Net LoC delta (code, 4 templates + 1 TS) | -120 to -60 | — | — |
| Net LoC delta (DS assets) | +18 (8 YAML + 1 separator + 8 CSS + 1 comment) | — | — |
| Spec comment fix | 0 (comment-only) | — | — |
| **Net total** | **-100 to -20** | **400** | **OK** |
| PRs | 1 | 1 | OK |
| Files touched | 9 (+ 2 DS assets) | — | — |
| Chained PR split | not needed | n/a | OK |

**Decision needed before apply:** No — single PR is well under the 400-line budget.
**Chained PRs recommended:** No — net delta is -100 to -20 lines; splitting would create a half-applied re-skin (wizard tokens not matching the design system) which is worse for review than the whole.
**400-line budget risk:** Low.
**Delivery strategy:** ask-on-risk (interactive, ask-always) — confirmed by orchestrator preflight.

## Per-Task LoC Summary

| Task | Files | Net LoC | Cumulative |
|------|-------|--------:|-----------:|
| T1 — Warning token in DESIGN.md | 1 (YAML) | +9 | +9 |
| T2 — Warning token CSS | 1 (CSS) | +9 | +18 |
| T3 — Re-skin confirmation step | 1 (HTML) | -5 | +13 |
| T4 — Re-skin admin step | 1 (HTML) | -35 | -22 |
| T5 — Re-skin company step | 1 (HTML) | -50 | -72 |
| T6 — Re-skin wizard (app-card + buttons + success) | 2 (HTML + TS) | -18 + 1 = -17 | -89 |
| T7 — Stale comment fix in company-step.spec.ts:418 | 1 (spec comment) | 0 (comment edit) | -89 |
| **Total** | **9 files** | **-89 net** (DS +18, code -107) | — |

Net total is **-89 lines** (well under the 400-line budget). Per-task max delta is +9 (T1, T2) and minimum delta is -50 (T5); every task is well within the per-commit size that work-unit-commits calls for.

## Dependency Graph

```
T1 (DESIGN.md) ──┐
                 ├─> T2 (styles.css) ──> T3, T4, T5 (steps) ──> T6 (wizard) ──> T7 (spec comment)
T1 itself has no dependency; it MUST land first so the YAML
token names exist when T2 references them in code comments and
when the wizard templates use them in T3..T6.
T2 depends on T1 (the CSS custom properties mirror the YAML).
T3..T5 are independent of each other (3 separate files) and can
land in any order. T6 depends on T3..T5 (the wizard template
wires all 3 step bodies, so any stale class names in the steps
break the visual story). T7 can land any time after T5 (it
references the company-step's red-border behavior) and is
typically the last commit to keep the diff narrative clean.
```

## Task List

- [x] T1 — Add warning token family to `DESIGN.md`
- [x] T2 — Add warning token CSS custom properties to `styles.css`
- [x] T3 — Re-skin `steps/confirmation-step.html`
- [x] T4 — Re-skin `steps/admin-step.html`
- [x] T5 — Re-skin `steps/company-step.html`
- [x] T6 — Re-skin `wizard/register.html` (and add `ButtonComponent` to `wizard/register.ts`)
- [x] T7 — Fix stale comment at `steps/company-step.spec.ts:418`

### T1 — Add warning token family to `DESIGN.md`

| Field | Value |
|-------|-------|
| **Files** | `DESIGN.md` |
| **LoC delta** | +9 (8 new YAML keys + 1 blank-line separator) |
| **Verification** | Visual inspection only (no test, no build) |
| **Dependencies** | none |
| **Commit** | `feat(design-system): add warning token family to design system` |

**What.** Insert the 8-key Material 3 warning family after `surface-variant` (line 99) and before `typography:` (line 101) in `DESIGN.md`'s `colors:` block:

```yaml
  warning: '#7c4d00'
  on-warning: '#ffffff'
  warning-container: '#ffe0a8'
  on-warning-container: '#291800'
  warning-fixed: '#ffe0a8'
  warning-fixed-dim: '#ffb95a'
  on-warning-fixed: '#291800'
  on-warning-fixed-variant: '#5d3700'
```

**Acceptance criteria.**

- The 8 keys exist in `DESIGN.md` `colors:` block, ordered as above.
- They sit between `surface-variant` and `typography:` (matches the existing tertiary / error placement).
- No other YAML structure is disturbed.

**Risks & mitigation.**

- R1.1 — A future contributor might add a 50..900 ramp and break the uniform token API. Mitigation: the 8-key Material 3 shape is documented in the proposal §"Why a 10-shade 50..900 ramp was rejected"; this doc itself is the answer to the question.

### T2 — Add warning token CSS custom properties to `styles.css`

| Field | Value |
|-------|-------|
| **Files** | `frontend/src/styles.css` |
| **LoC delta** | +9 (8 `--color-warning*` lines + 1 comment line) |
| **Verification** | `cd frontend && bun run build` succeeds; visual inspection |
| **Dependencies** | T1 (YAML keys must exist before CSS mirrors them) |
| **Commit** | `feat(design-system): add warning token family to styles.css` |

**What.** Insert 8 `--color-warning*` declarations in the `@theme` block of `frontend/src/styles.css` after line 73 (`--color-on-tertiary-fixed-variant: #005320;`) and before line 74 (`--color-background: #faf8ff;`), with a 1-line comment that documents the family's purpose:

```css
  /* Warning family — Material 3 tonal pair (4th status). Consumed
   * by the register wizard's province-load-error banner. */
  --color-warning: #7c4d00;
  --color-on-warning: #ffffff;
  --color-warning-container: #ffe0a8;
  --color-on-warning-container: #291800;
  --color-warning-fixed: #ffe0a8;
  --color-warning-fixed-dim: #ffb95a;
  --color-on-warning-fixed: #291800;
  --color-on-warning-fixed-variant: #5d3700;
```

**Acceptance criteria.**

- The 8 `--color-warning*` custom properties are defined in the `@theme` block.
- They sit after `--color-on-tertiary-fixed-variant` and before `--color-background` (matches the proposal's `styles.css` insertion point).
- `bun run build` succeeds (Tailwind v4 resolves the new tokens without error).
- `bg-warning`, `bg-warning-container`, `text-on-warning`, `text-on-warning-container`, `border-warning` resolve as Tailwind utilities (spot-check in the DevTools of a dev-server page).

**Risks & mitigation.**

- R2.1 — Token name collision with Tailwind v4 internals. Mitigation: Tailwind v4 uses `--color-*` naming for its theme tokens; `warning` is not a Tailwind-internal color. The `ds-` spacing prefix precedent (commit e0055e2) shows the team has already dealt with similar collision concerns.

### T3 — Re-skin `steps/confirmation-step.html`

| Field | Value |
|-------|-------|
| **Files** | `frontend/src/app/features/register/steps/confirmation-step.html` |
| **LoC delta** | -5 (class swaps, no structural change) |
| **Verification** | `cd frontend && bun run test` 173/173 pass |
| **Dependencies** | T2 (warning tokens optional here — the step uses outline-variant / on-surface / primary only; on-surface / outline-variant are pre-existing tokens; primary is pre-existing) |
| **Commit** | `feat(register): re-skin confirmation step with design system tokens` |

**What.** Class-only swaps (no markup change, no new components):

| Current classes | Target |
|-----------------|--------|
| `text-slate-900` (h2) | `text-on-surface` |
| `text-slate-600` (p) | `text-on-surface-variant` |
| `border-slate-200` (fieldset) | `border-outline-variant` |
| `text-slate-700` (legend) | `text-on-surface` |
| `text-slate-500` (dt) | `text-on-surface-variant` |
| `text-slate-900` (dd) | `text-on-surface` |
| `border-slate-300 text-slate-900` (checkboxes) | `border-outline-variant text-primary` |
| `text-slate-900 underline` (links) | `text-primary hover:underline` |

**Acceptance criteria.**

- No `slate-*` utility remains in the file.
- No `data-testid`, `id`, `aria-*`, or `role` attribute is changed.
- The `<dl>/<dt>/<dd>` summary structure and the 2 consent checkboxes are unchanged.
- `bun run test` reports 173/173 pass.

**Risks & mitigation.**

- R3.1 — The 2 consent links use `target="_blank" rel="noopener"`. The class swap must preserve those attributes. Mitigation: the swap is class-only; `target` and `rel` are not touched.

### T4 — Re-skin `steps/admin-step.html`

| Field | Value |
|-------|-------|
| **Files** | `frontend/src/app/features/register/steps/admin-step.html` |
| **LoC delta** | -35 (6 input class swaps + password toggle stays raw + Siguiente → app-button) |
| **Verification** | `cd frontend && bun run test` 173/173 pass |
| **Dependencies** | T2 (uses pre-existing `text-on-surface`, `text-error`; no warning tokens here) |
| **Commit** | `feat(register): re-skin admin step with design system tokens` |

**What.** Class swaps on 6 inputs (`#admin-first-name`, `#admin-last-name`, `#admin-username`, `#admin-email`, `#admin-password`, `#admin-password-confirmation`):

- `text-slate-700` (labels) → `text-on-surface`
- `text-red-600` (asterisk + error `<p>`) → `text-error`
- `border-slate-300 ... focus:border-slate-500 focus:ring-slate-500` (input) → `border-outline-variant focus:border-primary focus:ring-primary`
- `text-slate-900` (input) → `text-on-surface`
- `[class.border-red-500]` (input) → `[class.border-error]`
- Siguiente `<button class="... bg-slate-900 ...">` → `<app-button variant="primary" size="md" type="button" [disabled]="!canAdvance" data-testid="admin-next">Siguiente</app-button>`

**Password toggle is kept raw** (login precedent — extracting a reusable `app-password-input` is F2+):

- `text-slate-600 hover:text-slate-900` → `text-on-surface-variant hover:text-on-surface`
- `focus:ring-slate-500` → `focus:ring-primary`

**Acceptance criteria.**

- No `slate-*` utility remains in the file.
- 6 input `id`s, all `aria-required`/`aria-invalid`/`aria-describedby` bindings, and all `<p role="alert" id="...-error">` error regions are unchanged.
- The password show/hide toggle's `aria-label`, `aria-pressed`, and click handler are unchanged.
- The Siguiente button keeps `data-testid="admin-next"` and the `[disabled]="!canAdvance"` binding.
- `bun run test` reports 173/173 pass.

**Risks & mitigation.**

- R4.1 — The Siguiente button currently is a raw `<button type="button">` with no `formControl` binding. The `<app-button>` wrapper renders a real `<button type="button" [disabled]="disabled() || loading()">` internally; passing `type="button"` explicitly is the spec. Mitigation: `app-button` already defaults to `type="button"`; the `data-testid` lives on the inner `<button>` (transcluded via `<ng-content>`), so existing `host.querySelector('[data-testid="admin-next"]')` queries in the spec still resolve.

### T5 — Re-skin `steps/company-step.html`

| Field | Value |
|-------|-------|
| **Files** | `frontend/src/app/features/register/steps/company-step.html` |
| **LoC delta** | -50 (8 input + 2 select class swaps, province-error banner → warning tokens, Reintentar → app-button) |
| **Verification** | `cd frontend && bun run test` 173/173 pass |
| **Dependencies** | T2 (consumes `border-warning`, `bg-warning-container`, `text-on-warning-container`) |
| **Commit** | `feat(register): re-skin company step with design system tokens` |

**What.** Class swaps on 8 inputs + 2 selects (same pattern as T4: `slate-*` → design tokens, `red-*` → `error`, `slate-900` button → `app-button primary`).

**Province-error banner** (the visual highlight of this re-skin):

- `border-amber-300 bg-amber-50 text-amber-900` → `border-warning bg-warning-container text-on-warning-container`
- Reintentar button: `border-amber-400 bg-white text-amber-900 ...` → `<app-button variant="secondary" size="sm" type="button" (click)="retryProvinces()" data-testid="company-province-retry">Reintentar</app-button>`

**Acceptance criteria.**

- No `slate-*` or `amber-*` utility remains in the file.
- All 8 input `id`s, the 2 select `id`s / `data-testid` (`company-tax-type`, `company-province`), and the province-error banner's `data-testid="company-province-load-error"` / `company-province-retry` are unchanged.
- The Siguiente button keeps `data-testid="company-next"` and the `[disabled]="!canAdvance"` binding.
- The province-error banner's `data-testid` is preserved on the wrapper `<div role="alert">`.
- `bun run test` reports 173/173 pass.

**Risks & mitigation.**

- R5.1 — The province-error banner currently is a `<div role="alert">` containing a `<span>{{ errMsg }}</span>` and a `<button>Reintentar</button>`. After the swap, the `<button>` becomes `<app-button>`, which renders an inner `<button>`. Mitigation: the spec asserts on `data-testid="company-province-retry"` — that attribute is on the `<app-button>` host element (the inner `<button>` is inside it, and `host.querySelector('[data-testid="company-province-retry"]')` walks the descendant tree in DOM order, so it still resolves). Verify in the test run.
- R5.2 — `[class.border-red-500]` → `[class.border-error]`. Tailwind v4 needs the utility to exist; `border-error` resolves via `--color-error` (pre-existing). Confirmed at the design-system batch (commit 511d53).
- R5.3 — Fieldset legend "Domicilio" `border-slate-200` → `border-outline-variant`. Same swap as T3.

### T6 — Re-skin `wizard/register.html` (and add `ButtonComponent` to `wizard/register.ts`)

| Field | Value |
|-------|-------|
| **Files** | `frontend/src/app/features/register/wizard/register.html`, `frontend/src/app/features/register/wizard/register.ts` |
| **LoC delta** | -18 (HTML) + 1 (TS imports[]) = **-17 net** |
| **Verification** | `cd frontend && bun run build` succeeds; `cd frontend && bun run test` 173/173 pass; visual hard-refresh at 768px |
| **Dependencies** | T3, T4, T5 (wizard template wires the 3 step bodies; any stale class names break the visual story) |
| **Commit** | `feat(register): re-skin wizard with app-card and design system tokens` |

**What — `wizard/register.html` (template).**

1. Wrap the whole `<main>` in `<app-card>` (login precedent). Outer wrapper becomes `mx-auto w-full max-w-3xl px-4 py-16`, inner `<app-card>` contains the form, stepper, action row, and success state.
2. H1 `text-slate-900` → `text-on-surface`; subtitle `text-slate-600` → `text-on-surface-variant`.
3. Stepper labels: `text-slate-900` → `text-on-surface`; `text-slate-500` → `text-on-surface-variant`; chevron `text-slate-300` → `text-outline-variant`.
4. Top-level error region `text-red-600` → `text-error`.
5. Loading indicator `text-slate-600` → `text-on-surface-variant`.
6. Atrás button: `border-slate-300 bg-white text-slate-700` → `<app-button variant="secondary" size="md" type="button" (click)="previous()" [disabled]="currentStepIndex() === 0" data-testid="stepper-prev">Atrás</app-button>`.
7. Siguiente button: `bg-slate-900` → `<app-button variant="primary" size="md" type="button" (click)="next()" [disabled]="!canAdvance()" data-testid="stepper-next">Siguiente</app-button>`.
8. Crear cuenta button: same `app-button primary` pattern, `[disabled]="!canSubmit()"`, `data-testid="submit-create"`.
9. Ir a iniciar sesión button (success state): `bg-slate-900 ...` → `<app-button variant="primary" size="md" type="button" (click)="goToLogin()" data-testid="go-login">Ir a iniciar sesión</app-button>`.
10. Success card: `border-green-200 bg-green-50` → `border-tertiary-container bg-tertiary-container`; `text-green-800/900` → `text-on-tertiary-container`. The success section stays inside the `<app-card>` body (login precedent: success is a card, not a separate page region).
11. Inner form section spacing: change `<form class="space-y-6">` (inside each step) to `space-y-4` so the `app-card`'s `p-6` (24px) padding does not double up with 24px section spacing (proposal §"New risk — app-card padding double-up").

**What — `wizard/register.ts` (TS).**

Add `import { ButtonComponent } from '../../shared/ui/button';` at the top and `ButtonComponent` to the `imports: [...]` array on the `@Component` decorator (one-line change, follows the dashboard-shell.ts pattern at line 7 + line 49).

**Acceptance criteria.**

- `wizard/register.html` has no `slate-*`, `green-*`, or `red-*` utility.
- The outer wrapper is `<app-card>` inside a `mx-auto w-full max-w-3xl px-4 py-16` gutter.
- All 4 stepper buttons (Atrás, Siguiente, Crear cuenta, Ir a iniciar sesión) are `<app-button>` instances with the correct `data-testid` and bindings.
- The loading state "Creando cuenta..." span keeps its `data-testid="loading"` (no class change needed; it's a `<span>`).
- The success card uses `tertiary-container` tokens.
- `imports[]` in `register.ts` includes `ButtonComponent` alongside the 3 step components.
- `bun run build` succeeds; `bun run test` 173/173 pass.
- Visual hard-refresh at 768px: the 3 step bodies, the stepper header, the action row, and the success card render with the design system palette.

**Risks & mitigation.**

- R6.1 — `<app-button>` wraps the inner `<button>`; the spec's `host.querySelector('[data-testid="stepper-next"]')` walks descendants, so the inner `<button>` is reachable. Mitigation: same pattern as the dashboard's "Cerrar sesión" button (dashboard-shell.ts already does this; 173/173 baseline proves it works).
- R6.2 — The success state's "Ir a iniciar sesión" button is the only CTA on the success screen; it must keep `data-testid="go-login"` for the spec's `goToLogin` click test. Mitigation: the `<app-button>` carries the `data-testid` on its host element; the spec already queries via `host.querySelector('[data-testid="go-login"]')` which matches descendants.
- R6.3 — `app-card` adds `p-6` (24px) to the form body. If the inner `space-y-6` stays, layout is 24px + 24px between sections (visually heavy). Mitigation: the spec (T3..T5) reduces inner `space-y-6` to `space-y-4`; call this out in the T6 commit message.
- R6.4 — The `canAdvance` / `canSubmit` computeds read `formTick()` to force re-evaluation on every form change. The re-skin does not touch them. No behavior risk.
- R6.5 — `app-button` is type="button" by default. The wizard's 4 action buttons are click-driven (no form submit), so `type="button"` is the correct setting. The spec binds to `(click)`; no risk.

### T7 — Fix stale comment at `steps/company-step.spec.ts:418`

| Field | Value |
|-------|-------|
| **Files** | `frontend/src/app/features/register/steps/company-step.spec.ts` |
| **LoC delta** | 0 (1 comment line edit) |
| **Verification** | `cd frontend && bun run test` 173/173 pass |
| **Dependencies** | T5 (the company-step template no longer has `[class.border-red-500]` after the re-skin) |
| **Commit** | `test(register): remove stale border-red-500 comment from company-step spec` |

**What.** Edit the comment on line 418 of `frontend/src/app/features/register/steps/company-step.spec.ts`. The current comment reads:

```ts
    // The red border is applied via [class.border-red-500].
    expect(component.shouldShowError(component.form.controls.slug)).toBe(true);
```

Replace it with:

```ts
    // The error border is applied via [class.border-error]
    // (design-system token — was [class.border-red-500] before
    // the re-skin). The assertion below is on shouldShowError(),
    // not on the class itself.
    expect(component.shouldShowError(component.form.controls.slug)).toBe(true);
```

The test body (line 419) is unchanged. The class is `[class.border-error]` after T5 lands.

**Acceptance criteria.**

- Line 418 (and surrounding 3-line comment block) no longer references `[class.border-red-500]`.
- The `expect(...)` on line 419 is byte-identical to the original.
- No other line in the spec file is changed.
- `bun run test` reports 173/173 pass.

**Risks & mitigation.**

- R7.1 — A future contributor might think the comment is documentation of the assertion (it's a historical note, not a description of `shouldShowError`). Mitigation: the comment explicitly says "The assertion below is on shouldShowError(), not on the class itself" — keeps the intent obvious.

## Commit Strategy

| # | Branch position | Commit message | LoC delta | Files |
|---|----------------|----------------|----------:|------|
| 1 | tip of `feat/re-skin-register-wizard` | `feat(design-system): add warning token family to design system` | +9 | 1 |
| 2 | tip | `feat(design-system): add warning token family to styles.css` | +9 | 1 |
| 3 | tip | `feat(register): re-skin confirmation step with design system tokens` | -5 | 1 |
| 4 | tip | `feat(register): re-skin admin step with design system tokens` | -35 | 1 |
| 5 | tip | `feat(register): re-skin company step with design system tokens` | -50 | 1 |
| 6 | tip | `feat(register): re-skin wizard with app-card and design system tokens` | -17 | 2 |
| 7 | tip | `test(register): remove stale border-red-500 comment from company-step spec` | 0 | 1 |

Commit style follows the existing log (commit e0055e2, 1062933, b3b9fc2, 18d57b0, 511d53, f2f0a9f): `feat(<scope>): <imperative>`, no body needed for these atomic changes. The `register` scope groups all 4 register templates; the `design-system` scope groups the DS asset adds; the `test` scope marks the spec comment nit.

Each commit is a single reviewable work unit (per `work-unit-commits`): one purpose, the repo still makes sense after applying only this commit, tests pass at every step.

## Verification Protocol

Run **at every commit** (the build must stay green at every step):

```bash
cd frontend && bun run test     # 173/173 must pass
```

Run **at commits T1, T2, T6**:

```bash
cd frontend && bun run build     # must succeed
```

Run **at commit T6 only** (visual):

1. `cd frontend && bun run start` (dev server)
2. Hard-refresh (Ctrl+Shift+R) the register page (`/register`) at 768px viewport.
3. Tab through all 3 steps. Verify: design-system colors, no `slate-*` rendering, `app-card` containment, button typography.
4. Submit empty form on step 1. Verify: `text-error` rendering on per-field error messages, the Reintentar button does NOT appear (no province error yet).
5. Fill the form with valid data, advance to step 2, advance to step 3, check both consents, click "Crear cuenta". Verify: success state renders with `tertiary-container` tokens, "Ir a iniciar sesión" button is `app-button primary`.

**Simulated failure** (dev tools → Network throttling → Offline on `ProvincesService.list()`):

1. On step 1, the province-error banner appears.
2. Verify: amber container, dark amber text, "Reintentar" button is `app-button variant="secondary" size="sm"`.
3. Click "Reintentar" with the throttle removed. Verify: the banner disappears, the province `<select>` populates.

## Risk Register (Cross-Task)

| # | Risk | Likelihood | Mitigation |
|---|------|-----------:|------------|
| RR-1 | `app-button` and `app-card` inner markup changes break `host.querySelector('[data-testid="..."]')` lookups | Low | The dashboard-shell already uses this pattern; 173/173 baseline proves descendant queries work. |
| RR-2 | Token name collision between `--color-warning*` and Tailwind v4 internals | Low | Tailwind v4 uses `--color-*` for theme tokens; `warning` is not a Tailwind internal. `ds-` spacing prefix precedent (e0055e2) shows the team's collision-handling playbook. |
| RR-3 | Half-applied re-skin if T3..T5 land in arbitrary order | Low | T3..T5 are independent of each other (3 separate files); no shared state. The build is green at every commit. |
| RR-4 | Stale spec comment left at `company-step.spec.ts:418` if T7 is forgotten | Low | T7 is the last commit; `bun run test` doesn't catch the stale comment (it's a comment, not an assertion). The PR description will list T7 explicitly so the reviewer sees it. |
| RR-5 | `space-y-6` → `space-y-4` inner form spacing change has a visual regression | Low | The login re-skin precedent uses the same pattern; the change makes the form less visually heavy (24px card padding + 16px section spacing ≈ login's spacing). |
| RR-6 | The new `warning` family has no dark-mode variant | Medium (F2) | The rest of the palette is light-mode-only; the warning family follows the same convention. Dark-mode work is F2+ and applies to the whole palette, not just `warning`. |

## Out of Scope (Re-confirmed)

Per the proposal §"Out of scope (deferred)" and the spec §"Out of scope":

- `app-input` migration / `ControlValueAccessor` adapter (Approach B is deferred to F2+).
- Mobile drawer / full responsive stepper overhaul.
- Backend, `RegistrationService`, `ProvincesService` (no API change).
- F2 features (passwordless sign-up, OAuth, social login).
- `AuthenticatedLayoutComponent` deletion (separate change, F1 tech-debt list).
- Dark-mode token variants for the warning family.
- `app-button` `loadingText` feature (Crear cuenta uses the inlined `{{ isLoading() ? 'Creando cuenta...' : 'Crear cuenta' }}` pattern, matching login's `Ingresando...` / `Ingresar` precedent).
- Password show/hide toggle refactor (kept as raw ghost button, login precedent).
- `app-card` `elevated` input usage (the wizard's card is `elevated={false}`; the `p-6` padding is enough on its own).

## Hard Rules

- **Build green at every commit.** Each task (T1..T7) is one commit; the build must compile and `bun run test` must report 173/173 after each commit lands.
- **No spec assertion or binding change.** The single permitted text edit is the comment at `company-step.spec.ts:418` (T7). All other 1,675 LoC of spec code is untouched.
- **No behavior change.** Validation rules, signal state, computed values, form `FormGroup` shapes, stepper navigation logic, and the `RegistrationService.submit` payload are byte-identical pre/post re-skin.
- **No new dependencies.** The re-skin consumes the existing `app-button` and `app-card` primitives + the new `warning` token family.
- **Per-commit size.** Every commit is well under the 200-LoC delta the `work-unit-commits` skill recommends; T5 (-50) is the largest, T2 (+9) the smallest.

## Next Step

Hand off to `sdd-apply` to execute T1..T7 in order on a single branch `feat/re-skin-register-wizard`, opening one PR at the end. The orchestrator's `ask-always` delivery strategy means the user will be asked before the PR is opened; the answer to "chained or single?" is **single** (well under 400 lines).
