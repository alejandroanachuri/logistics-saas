# Proposal: re-skin register wizard

## Intent

Bring the F1 3-step register wizard onto the Logistics Core design system tokens so the visual language matches the rest of the F1 surface (login, authenticated shell, recovery flows). Outcome: the wizard stops reading as raw Tailwind utility soup and aligns with the re-skinned login precedent — same `app-card` container, same `on-surface` / `outline-variant` neutrals, same `app-button` and token-driven inputs, and a semantic warning tone for the province-load-error banner.

## Scope

**IN scope**

- 4 templates: `wizard/register.html`, `steps/company-step.html`, `steps/admin-step.html`, `steps/confirmation-step.html`.
- 3 TS files (`wizard/register.ts`, `steps/company-step.ts`, `steps/admin-step.ts`) — `imports[]` array changes only to register `ButtonComponent` and `InputComponent` if any inputs migrate to `app-input`. Under Approach A, inputs stay raw, so `imports[]` changes are zero or near-zero.
- 1 stale comment fix in `steps/company-step.spec.ts:418` (the `[class.border-red-500]` reference is no longer accurate; the assertion is on `shouldShowError()`).
- New design system asset: a `--color-warning*` token family in `DESIGN.md` and `frontend/src/styles.css`, consumed by the province-error banner.

**OUT of scope**

- `app-input` adoption / a `ControlValueAccessor` adapter (Approach B is deferred).
- Mobile drawer, full responsive overhaul, breakpoints work.
- Backend, API contracts, `RegistrationService`, `ProvincesService`.
- F2 features (passwordless sign-up, OAuth, social login).
- Deletion of `AuthenticatedLayoutComponent` (obsolete, but a separate change).

## Approach

**Approach A — token-swap only**, matching the login re-skin precedent. Keep raw `<input formControlName>` / `<select formControlName>` HTML. Swap Tailwind utility classes for design system tokens. Wrap the wizard `<main>` in `app-card`. Promote the 3 primary submit buttons and the 1 Atrás button to `app-button` (the password show/hide toggle stays as a raw ghost button, same as login). The province-error banner adopts the new warning token family.

**Design system mappings this proposal commits to (7 of the 22 candidates from the exploration — the ones that drive user-visible change):**

1. **Container** — `w-full max-w-3xl` → `<app-card>` (matches login precedent: outer `mx-auto px-4 py-16` + inner card with `p-6`).
2. **Neutrals** — `slate-300/500/600/700/800/900` → `outline-variant` / `on-surface-variant` / `on-surface` (labels, copy, borders).
3. **Primary buttons** — `bg-slate-900` → `<app-button variant="primary" size="md">` for the 3 stepper submit buttons.
4. **Secondary button** — `border-slate-300 bg-white text-slate-700` → `<app-button variant="secondary" size="md">` for Atrás and the Reintentar button.
5. **Error text** — `text-red-600` → `text-error` (top-level error region, inline field errors).
6. **Success state** — `border-green-200 bg-green-50 text-green-800/900` → `border-tertiary-container bg-tertiary-container text-on-tertiary-container`.
7. **Province-error banner** — `border-amber-300 bg-amber-50 text-amber-900` → **new warning token family** (`border-warning bg-warning-container text-on-warning-container`).

The remaining 15 mappings in the exploration table (stepper label colors, chevron, consent checkboxes, summary fieldsets, link underlines, etc.) follow mechanically from #2 and the `app-input` / `app-button` defaults. They are listed in the exploration, not repeated here.

## New design system asset — warning token palette

Follows the existing Material 3 pattern in `DESIGN.md` (the file already defines `tertiary` (green success), `error` (red destructive), and supporting `*-container` / `on-*-container` pairs). Warning slots in as the fourth status family — semantically distinct from both success (tertiary, green) and destructive (error, red), and aligned with the existing `amber-*` usage in the province-error banner so the visual migration is faithful.

**Proposed addition to `DESIGN.md` `colors:` block (insert after `surface-variant`, before `typography:`):**

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

**Proposed addition to `frontend/src/styles.css` `@theme` block (insert after `--color-on-tertiary-fixed-variant: #005320;`, before the `background` group):**

```css
  --color-warning: #7c4d00;
  --color-on-warning: #ffffff;
  --color-warning-container: #ffe0a8;
  --color-on-warning-container: #291800;
  --color-warning-fixed: #ffe0a8;
  --color-warning-fixed-dim: #ffb95a;
  --color-on-warning-fixed: #291800;
  --color-on-warning-fixed-variant: #5d3700;
```

**Justification**

- **Semantic alignment.** Material 3 reserves warning (typically amber/orange) for non-destructive attention signals — exactly what the province-load-error banner is. It is distinct from `error` (destructive, red) and `tertiary` (success, green). The province banner is recoverable via "Reintentar", not destructive — warning is the right slot.
- **Contrast (WCAG 2.1 AA, ≥ 4.5:1 for body text).**
  - `text-on-warning-container` (`#291800`) on `bg-warning-container` (`#ffe0a8`): ~14.3:1 — well above AA.
  - `text-on-warning` (`#ffffff`) on `bg-warning` (`#7c4d00`): ~7.1:1 — passes AA.
  - `text-warning-fixed-dim` (`#ffb95a`) is not a body-text pair; it's the `*-dim` companion of `warning-fixed` for borders/dividers per the existing `tertiary-fixed-dim` pattern.
- **Visual continuity with the legacy `amber-*` palette.** `#ffe0a8` ≈ `amber-100`, `#291800` ≈ `amber-950` — the migration is tonal, not chromatic, so the banner keeps its recognizable amber feel while gaining a first-class token.
- **Structural parity with `tertiary` and `error`.** The proposal mirrors the same 8-key shape (`*`, `on-*`, `*-container`, `on-*-container`, `*-fixed`, `*-fixed-dim`, `on-*-fixed`, `on-*-fixed-variant`) that the existing palette uses for tertiary/error. This keeps the token API uniform — a future "success" call-to-action and a "warning" alert both follow the same `on-{role}-container` convention, no special cases.
- **Why a 10-shade `50..900` ramp was rejected.** `DESIGN.md` deliberately does not use a Tailwind-style 50-900 ramp; the palette is Material 3 tonal-pair based. Adding `warning.50`..`warning.900` would be the first departure from that convention and would create two incompatible ways to address the same color (e.g. `bg-warning-100` vs `bg-warning-container`). The 8-key Material 3 shape is consistent with the source of truth.

## Files to change

| Path | LoC delta | Kind |
|------|-----------|------|
| `frontend/src/app/features/register/wizard/register.html` | -20 to -10 | template |
| `frontend/src/app/features/register/wizard/register.ts` | 0 to +1 (if `app-button` is used) | TS |
| `frontend/src/app/features/register/steps/company-step.html` | -60 to -40 | template |
| `frontend/src/app/features/register/steps/company-step.ts` | 0 to +1 (if `app-input` is used) | TS |
| `frontend/src/app/features/register/steps/admin-step.html` | -40 to -30 | template |
| `frontend/src/app/features/register/steps/admin-step.ts` | 0 to +1 (if `app-input` is used) | TS |
| `frontend/src/app/features/register/steps/confirmation-step.html` | -5 | template |
| `frontend/src/app/features/register/steps/confirmation-step.ts` | 0 | TS |
| `frontend/src/app/features/register/steps/company-step.spec.ts` | 0 (1 comment edit) | spec |
| `DESIGN.md` | +9 (8 new YAML keys + 1 block separator) | asset (docs) |
| `frontend/src/styles.css` | +9 (8 new `--color-*` lines + 1 comment line) | asset (CSS) |

**Net LoC delta:** roughly **-120 to -60** in code + **+18** in design system assets = **-100 to -20** net.

## Test plan

**4 spec files, 1,676 LoC total, ZERO behavior changes.** Tests bind exclusively to `data-testid` attributes, `id` attributes, `role` / `aria-*` attributes, and `textContent` (with `toContain`). No class-name assertions, no `toHaveClass`, no `outerHTML` / `innerHTML` snapshots, no computed-style reads. Class-swap is invisible to the spec surface.

**One stale comment fix** in `company-step.spec.ts:418` — the comment says *"The red border is applied via `[class.border-red-500]`"*, but the test asserts on `shouldShowError()` (a boolean), not on the class. After the re-skin the binding style is the `app-input` `error` prop or the `border-error` token, not `[class.border-red-500]`. Pure comment edit, no assertion change.

**Manual verification (post-apply):**

- Visual regression: take screenshots of all 3 steps + the success screen + the province-error banner at 768px viewport; verify against the login re-skin screenshots for tonal continuity.
- Accessibility smoke: tab through each step, confirm `aria-required`, `aria-invalid`, `aria-describedby` on every input (these survive the swap; `app-input` renders them).
- Province-error path: simulate `ProvincesService.list()` failure; confirm the banner uses the new warning token (amber-ish container, dark amber text) and the Reintentar button is `app-button variant="secondary"`.

## Review workload forecast

| Metric | Value | Budget | Status |
|--------|-------|--------|--------|
| Net LoC delta (code) | -120 to -60 | — | — |
| Net LoC delta (DS assets) | +18 | — | — |
| Net total | -100 to -20 | 400 | OK |
| PRs | 1 | 1 | OK |
| Files touched | 9 (+ 2 DS assets) | — | — |
| Chained PR split | not needed | n/a | OK |

**One PR, no chained split.** The change is a single coherent re-skin; all 4 templates, the 1 spec comment, and the 2 DS asset files belong together. Splitting would create a state where the wizard's tokens don't match the design system (a half-applied re-skin) — worse for review than the whole.

## Risks

1. **`app-input` / `app-button` integration drift.** The user locked Approach A (token-swap, raw `<input formControlName>`). The login re-skin precedent is the binding pattern. If reviewers ask "why not use `app-input`?", the answer is the same as login's: lowest risk, smallest LoC, and the `app-input` `ControlValueAccessor` story is an F2+ concern (deferred to a follow-up change that adds a shared `FormControlInputComponent` adapter).
2. **Loading-state submit button is not `app-button`.** `app-button` has no `loadingText` input. Login kept the raw `<button>` with the inlined `{{ isLoading() ? 'Ingresando...' : 'Ingresar' }}` pattern; the wizard's "Crear cuenta" → "Creando cuenta..." button needs the same treatment. Same precedent, same fix — no new risk class.
3. **Province-error token contrast in dark mode / high-contrast mode.** The new `warning` family is light-mode-first (matches the rest of the palette — `DESIGN.md` has no dark-mode tokens yet). If the wizard is ever viewed in a future dark theme, the amber container may need a dark-mode variant. Out of scope for this change; flag for the F2 dark-mode workstream.
4. **New risk — `app-card` padding double-up.** The current wizard uses `space-y-6` on form internals. `app-card` applies its own `p-6` (24px). If the form internals keep their `space-y-6`, the result is 24px card padding + 24px section spacing — workable but visually heavy. Mitigation: reduce inner `space-y-6` to `space-y-4` after wrapping, or move the form internals into the card body slot and let `app-card`'s padding do the work. This is a 2-line adjustment, called out here so the implementer doesn't miss it.
5. **New risk — token name collision in `@theme`.** The proposed `--color-warning*` keys must not shadow Tailwind v4's internal tokens. Verifying against the existing `@theme` block: no `--color-warning*` is currently defined, no Tailwind v4 internal token uses the `warning` prefix. Safe to add.

## Out of band (deferred)

- **Obsolete `AuthenticatedLayoutComponent`.** Still imported by old routes; deletion is a separate change requiring a route audit. Tracked in the F1 tech-debt list.
- **Mobile drawer / responsive stepper.** The wizard is desktop-first; mobile layout is a separate workstream.
- **Password show/hide toggle refactor.** The current toggle is a raw ghost button matching the login precedent. Extracting it to a reusable `app-password-input` primitive belongs with the `app-input` `ControlValueAccessor` work in F2.
- **Dark-mode token variants.** The new `warning` family is light-mode-only, consistent with the rest of the palette. Dark-mode variants land when the rest of the palette gets them.
- **`app-input` `ControlValueAccessor` adapter.** The right long-term answer for re-using design system primitives in reactive forms. F2+.

## Next step

Hand off to `sdd-spec` to write the delta spec (`specs/register/wizard/spec.md` `## ADDED Requirements`) and the per-file change list. Then `sdd-design` for the migration ordering (DS assets first, then templates, then the spec comment fix). Then `sdd-tasks` to break it into atomic work units.
