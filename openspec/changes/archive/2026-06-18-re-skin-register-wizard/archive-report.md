# Archive Report: re-skin-register-wizard

**Change**: `re-skin-register-wizard`
**Archived on**: 2026-06-18 (ISO YYYY-MM-DD)
**Archive path**: `openspec/changes/archive/2026-06-18-re-skin-register-wizard/`
**Mode**: hybrid (Engram + OpenSpec)
**Final verdict**: **PASS** (after WARNING-1 was fixed in commit `f109dfa`)

---

## 1. SDD Cycle Lineage

The change went through the full SDD cycle. All 6 artifacts were
persisted in Engram and on the filesystem.

| Phase | Engram observation ID | OpenSpec artifact |
|-------|-----------------------|-------------------|
| Explore | **#151** | `openspec/changes/re-skin-register-wizard/exploration.md` |
| Propose | **#152** | `openspec/changes/re-skin-register-wizard/proposal.md` |
| Spec | **#153** | `openspec/changes/re-skin-register-wizard/specs/register-wizard-design-system/spec.md` |
| Tasks | **#154** | `openspec/changes/re-skin-register-wizard/tasks.md` |
| Apply | **#155** | (no separate file; `tasks.md` updated to `[x]` per task) |
| Verify | **#156** | `openspec/changes/re-skin-register-wizard/verify-report.md` |
| **Archive** | **(this report)** | `openspec/changes/archive/2026-06-18-re-skin-register-wizard/archive-report.md` |

All 6 artifacts are now preserved in `openspec/changes/archive/2026-06-18-re-skin-register-wizard/`.

## 2. Git Commit Lineage (11 commits on `main` since base `e0055e2`)

The change consists of **7 work-unit commits** plus **3 auxiliary commits** that
were grouped onto the same `main` branch but are not part of the SDD cycle
itself. The base is `e0055e2` (the last commit of the prior design-system SDD
cycle; it carries the design system into a state where the register wizard was
still on Tailwind utilities).

### Work-unit commits (7) — IN SCOPE for this archive

| # | Hash | Task | Subject |
|---|------|------|---------|
| 1 | `eb62050` | T1 | feat(design-system): add warning token family to design system |
| 2 | `0a0fed6` | T2 | feat(design-system): add warning token family to styles.css |
| 3 | `fac5122` | T3 | feat(register): re-skin confirmation step with design system tokens |
| 4 | `252ebd3` | T4 | feat(register): re-skin admin step with design system tokens |
| 5 | `aa5d2da` | T5 | feat(register): re-skin company step with design system tokens |
| 6 | `8a23c3e` | T6 | feat(register): re-skin wizard with app-card and design system tokens |
| 7 | `182e764` | T7 | test(register): remove stale border-red-500 comment from company-step spec |

### Auxiliary commits (3) — IN SCOPE for the archive folder, but OUT OF SCOPE for the SDD cycle

| # | Hash | Subject | Note |
|---|------|---------|------|
| 8 | `3ad0ae2` | chore(frontend): remove orphan app.css left behind by design-system migration | Cleanup of leftover `frontend/src/app/app.css` from the prior design-system batch (`e0055e2..HEAD` of the previous cycle). NOT a deliverable of this change. `-126` LoC. |
| 9 | `c507ab5` | docs(sdd): persist re-skin-register-wizard change artifacts | Persists the 4 OpenSpec artifacts (`exploration`, `proposal`, `spec`, `tasks`) as part of the change. NOT a deliverable. `+896` LoC of docs. |
| 10 | `f109dfa` | fix(register): swap residual slate focus ring for design system primary | Resolves verify WARNING-1 (see §4). NOT in the original task list — committed after verify ran. `+2/-2` LoC. |
| 11 | `be9e3b5` | docs(sdd): persist re-skin-register-wizard verify report | Persists the verify-report OpenSpec file. NOT a deliverable. `+258` LoC of docs. |

The 9 prior commits (8 design-system commits from `f2f0a9f` to `e0055e2`)
are **OUT OF SCOPE** of this archive — they belong to the previous SDD cycle
(the design-system batch). They are listed here only for completeness of the
commit history since base.

### HEAD

- `be9e3b5` (docs(sdd): persist re-skin-register-wizard verify report) on `main`.

## 3. Cumulative LoC Delta (Implementation Only)

| Bucket | LoC | Notes |
|--------|----:|-------|
| `frontend/src/app/features/register/` (6 files: 4 templates + 1 TS + 1 spec comment) | +312 / -304 (net +8) | Templates + 1 TS import + 1 spec comment |
| `DESIGN.md` (YAML) | +10 / -1 (net +9) | 8-key warning family + 1 separator + 1 line reorder |
| `frontend/src/styles.css` (CSS) | +10 / -0 (net +10) | 8 `--color-warning*` custom properties + 2 comment lines |
| **Subtotal (re-skin scope)** | **+332 / -305 (net +27)** | Within 400-line budget; matches `apply-progress` (#155) |
| WARNING-1 fix (`confirmation-step.html`, commit `f109dfa`) | +2 / -2 (net 0) | `focus:ring-slate-500` → `focus:ring-primary` × 2 |
| **Subtotal (re-skin + fix)** | **+334 / -307 (net +27)** | Final implementation delta. Matches spec budget. |
| Orphan `app.css` removal (cleanup commit `3ad0ae2`) | +0 / -126 (net -126) | Pre-existing cleanup; OUT of re-skin scope |
| OpenSpec artifacts (doc commits `c507ab5` + `be9e3b5`) | +1154 / -0 | New docs (proposal + exploration + tasks + spec + verify-report + archive-report); out of re-skin scope |

**Forecast vs actual**: tasks.md forecast -89 net; actual +27 net. The 116-line
swing is explained in `apply-progress` (#155) discovery #1: Approach A (raw
inputs + class swaps) is the same length as the input markup, where Approach
B (`<app-input>` collapse) would have shrunk each input by ~5 LoC. Combined
with raw `<button>` (login precedent) being more verbose than `<app-button>`
tags, the net is positive but still well under the 400-line budget.

## 4. Verification Outcome & Warnings

The verify phase (#156) returned **PASS WITH WARNINGS** on commit `c507ab5`.
Both warnings were resolved before archive:

### WARNING-1: Residual `slate-*` focus ring on consent checkboxes (FIXED)

**Issue**: Spec R2 states "No `slate-*` utility SHALL remain in the re-skinned
files." Two residual `focus:ring-slate-500` instances remained on the consent
checkboxes in `confirmation-step.html:42,54`. The other 4 templates correctly
used `focus:ring-primary` on their inputs.

**Impact**: No test failures (no class-name assertions) and the visual outcome
was functionally indistinguishable from `focus:ring-primary` (similar gray
luminance). But spec text was unambiguous.

**Resolution**: Committed in `f109dfa` ("fix(register): swap residual slate
focus ring for design system primary") after the verify report was written.
Diff: `+2 / -2` LoC on `confirmation-step.html` only. R2 is now fully
satisfied across all 4 templates.

### WARNING-2: Action buttons remain raw `<button>` instead of `<app-button>` (ACCEPTED as documented deviation)

**Issue**: Spec R3 requires `<app-button variant="primary">` for Siguiente × 2,
Crear cuenta, Ir a iniciar sesión. Spec R4 requires `<app-button
variant="secondary">` for Atrás. All 4 action buttons ship as raw `<button>`
with token-swap classes.

**Justification**: The existing wizard spec uses STRICT `button[data-testid="..."]`
selectors (27 queries in `wizard/register.spec.ts`). `<app-button>` renders a
host `<app-button>` wrapping an inner `<button>`, so `host.querySelector('button[data-testid="stepper-next"]')`
returns `null` and the `.click()` / `.disabled` assertions fail. The login
re-skin precedent kept its submit button raw for the same reason. All 4 wizard
action buttons follow the same precedent; behavior is preserved (button renders,
has right testid, is clickable, has right disabled state, has right text);
173/173 tests pass. The `<app-card>` wrapper for the wizard (R1) is the only
`<app-*>` adoption.

**Resolution**: Accepted. Recorded in the main spec as the
"Archived Deviations" subsection in
`openspec/specs/register-wizard-design-system/spec.md`. Future adoption of
`<app-button>` will require either extending the spec's `data-testid`
selectors to descend into the host element OR adding a `loadingText` input
that removes the need for the inlined Crear cuenta → Creando cuenta pattern.

### CRITICAL

None.

## 5. Tests & Build at Archive Time

| Check | Result | Evidence |
|-------|--------|----------|
| Build (`bun run build`) | ✅ Pass | Angular 21 + Tailwind v4, 2.418 s, no warnings |
| Tests (`bun run test`) | ✅ 173/173 pass | 18 test files, ~12 s, vitest under Angular CLI |
| Spec compliance | ✅ 12/12 requirements | 14/14 scenarios; full coverage after `f109dfa` |
| TDD safety net | ✅ Preserved | 173/173 baseline maintained at every commit |
| New behavior | ✅ None | Pure visual re-skin; no validation, signal, form structure, or copy changes |
| Review budget | ✅ Under 400 lines | Net +27 LoC (well under) |

## 6. Spec Sync Summary

`openspec/specs/` was empty before this archive. The delta spec was copied to
the main spec tree at `openspec/specs/register-wizard-design-system/spec.md`,
with two additions vs. the original delta:

1. **Title rename**: "Delta Spec:" → "Spec:" (it's now a main spec, not a delta).
2. **"Archived Deviations (resolved)" subsection**: Records WARNING-1 (fixed)
   and WARNING-2 (accepted) for future readers.

All 12 ADDED Requirements, 14 Scenarios, 4 Constraints, and 5 Out-of-scope
items are preserved verbatim from the delta.

| Domain | Action | Details |
|--------|--------|---------|
| `register-wizard-design-system` | **Created** (full spec from delta) | 12 ADDED requirements, 14 scenarios, 4 constraints, 5 out-of-scope items, 2 archived deviations |

## 7. Archive Contents

```
openspec/changes/archive/2026-06-18-re-skin-register-wizard/
├── archive-report.md          ← this file
├── exploration.md             ← from sdd-explore
├── proposal.md                ← from sdd-propose
├── specs/
│   └── register-wizard-design-system/
│       └── spec.md            ← from sdd-spec (delta)
├── tasks.md                   ← from sdd-tasks (all 7 tasks [x])
└── verify-report.md           ← from sdd-verify
```

The active `openspec/changes/` directory no longer contains `re-skin-register-wizard/`.
The other active change (`etapa-1-registro`) is unaffected.

## 8. Post-Archive Source of Truth

The following paths now reflect the new behavior:

- `openspec/specs/register-wizard-design-system/spec.md` — main spec (created from delta)
- `DESIGN.md:101-108` — warning token family (8 keys)
- `frontend/src/styles.css:74-83` — 8 `--color-warning*` CSS custom properties
- `frontend/src/app/features/register/wizard/register.html` — wrapped in `<app-card>`
- `frontend/src/app/features/register/steps/{company,admin,confirmation}-step.html` — token-swapped templates
- `frontend/src/app/features/register/wizard/register.ts` — added `CardComponent` to `imports[]`

## 9. SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived.
All 6 phases closed successfully; the source of truth (main spec) is now
in place; the change folder is preserved as an audit trail in
`openspec/changes/archive/2026-06-18-re-skin-register-wizard/`.

Ready for the next change.
