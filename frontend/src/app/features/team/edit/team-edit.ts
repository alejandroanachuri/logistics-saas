import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthStore } from '../../../core/state/auth-store';
import { CompanyUsersStore } from '../../../core/state/company-users-store';
import { ButtonComponent } from '../../../shared/ui/button';

/**
 * Team edit page (`/team/:id/edit`) — etapa-2-usuarios PR-5.
 *
 * <p>Lets a COMPANY_ADMIN update the editable info fields
 * (firstName, lastName, email) of a user in their tenant.
 * The password section and the role assignment are NOT
 * here — those flows live in:
 * - `team-detail` (Roles tab) for role changes
 * - `team-detail` (Seguridad tab) for password reset
 *
 * <p>Two protected-target rules redirect the user back to
 * the detail page with no edit form:
 * - Target is the first admin of the tenant (their email /
 *   username / COMPANY_ADMIN role are immutable — see PR-3
 *   spec section B.4 + decision #8).
 * - Target is the current user (no self-edit — see PR-3
 *   spec section C5). For self, even firstName / lastName
 *   are blocked here; the future "My profile" surface will
 *   handle self-edits.
 *
 * <p>The first-admin case still renders the form (with
 * `email` disabled) if the redirect is bypassed — but the
 * spec says redirect first, render never. The class below
 * follows the spec.
 *
 * <p>Standalone, OnPush, signal-first.
 */
@Component({
  selector: 'app-team-edit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent],
  templateUrl: './team-edit.html',
})
export class TeamEditComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly store = inject(CompanyUsersStore);
  protected readonly authStore = inject(AuthStore);

  /** Target user id resolved from the route param map. */
  protected readonly targetUserId = signal<string>('');

  /** True once the detail has loaded (avoids a flash of the
   * un-pre-filled form on the first paint). */
  protected readonly detailLoaded = signal<boolean>(false);

  /** Reactive form for the editable info fields. Email
   * gets disabled at runtime when the target is the first
   * admin (the spec only allows firstName / lastName
   * edits there), but the redirect-on-mount rule below
   * wins first so this branch is defensive only. Public
   * for test access — same pattern as `admin-step.ts`. */
  readonly form = this.fb.group({
    firstName: this.fb.control('', {
      validators: [Validators.required, Validators.minLength(2), Validators.maxLength(60)],
    }),
    lastName: this.fb.control('', {
      validators: [Validators.required, Validators.minLength(2), Validators.maxLength(60)],
    }),
    email: this.fb.control('', {
      validators: [Validators.required, Validators.email, Validators.maxLength(120)],
    }),
  });

  /** True when the email field should be disabled in the UI.
   * First admin's email is immutable per PR-3 spec. */
  protected readonly emailDisabled = computed<boolean>(() => {
    const detail = this.store.currentCompanyUser();
    return detail?.isFirstAdmin === true;
  });

  constructor() {
    // Effect: react to the store's currentCompanyUser once it
    // is populated by loadDetail(). Pre-fills the form and
    // flips the `detailLoaded` gate. Runs again if the store
    // signal changes (the detail fetch in ngOnInit is what
    // triggers it the first time).
    effect(() => {
      const detail = this.store.currentCompanyUser();
      if (!detail) return;
      this.form.patchValue({
        firstName: detail.firstName ?? '',
        lastName: detail.lastName ?? '',
        email: detail.email,
      });
      this.detailLoaded.set(true);
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.targetUserId.set(id);
    if (!id) {
      void this.router.navigate(['/team']);
      return;
    }
    void this.store
      .loadDetail(id)
      .then((detail) => {
        // First-admin protection: redirect to the detail
        // page so the admin sees the read-only view + the
        // tooltip explaining why the form is locked.
        if (detail.isFirstAdmin) {
          void this.router.navigate(['/team', detail.id]);
          return;
        }
        // Self-edit block: redirect to the detail page;
        // the future "My profile" flow handles self-edits.
        const me = this.authStore.currentUser();
        if (me && me.id === detail.id) {
          void this.router.navigate(['/team', detail.id]);
          return;
        }
      })
      .catch(() => {
        // The errorInterceptor surfaces the localized copy;
        // bounce back to the list page so the user can
        // pick another row.
        void this.router.navigate(['/team']);
      });
  }

  /** Submit the form. Validates first; if valid, calls
   * `store.update` and navigates back to the detail page on
   * success. On invalid form, marks every control as touched
   * so the template's per-field error copy surfaces. Public
   * for test access. */
  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const id = this.targetUserId();
    if (!id) return;
    const value = this.form.getRawValue();
    void this.store
      .update(id, {
        firstName: value.firstName,
        lastName: value.lastName,
        email: value.email,
      })
      .then(() => {
        this.store.clearDetail();
        void this.router.navigate(['/team', id]);
      })
      .catch(() => {
        // Error copy is rendered via the global toast; the
        // form keeps its values so the user can retry.
      });
  }

  /** Per-field Spanish error copy. Returns `null` when the
   * control has no errors or has not been touched / dirtied
   * — the template binds the result to an `@if` block so
   * the DOM stays empty on first render. Mirrors the
   * `errorMessageFor` pattern from the register wizard. */
  protected errorMessageFor(
    field: 'firstName' | 'lastName' | 'email',
  ): string | null {
    const control = this.form.controls[field];
    if (!control.errors || (!control.touched && !control.dirty)) {
      return null;
    }
    const errs = control.errors;
    if (errs['required']) return 'Este campo es obligatorio.';
    if (errs['email']) return 'Ingresá un email válido.';
    if (errs['minlength']) {
      const len = (errs['minlength'] as { requiredLength: number }).requiredLength;
      return `Mínimo ${len} caracteres.`;
    }
    if (errs['maxlength']) {
      const len = (errs['maxlength'] as { requiredLength: number }).requiredLength;
      return `Máximo ${len} caracteres.`;
    }
    return 'Revisá este campo.';
  }

  protected shouldShowError(
    field: 'firstName' | 'lastName' | 'email',
  ): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }

  protected onCancel(): void {
    this.store.clearDetail();
    void this.router.navigate(['/team', this.targetUserId()]);
  }
}
