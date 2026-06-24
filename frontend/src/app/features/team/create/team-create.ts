import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { CompanyUsersStore } from '../../../core/state/company-users-store';
import { RolesService } from '../../../core/services/roles.service';
import {
  CreateCompanyUserRequest,
  CreateCompanyUserResponse,
  Role,
} from '../../../core/types';
import { MultiSelectComponent } from '../../../shared/ui/multi-select';
import { PasswordRevealModalComponent } from '../../../shared/ui/password-reveal-modal';
import { PasswordStrengthMeterComponent } from '../../../shared/ui/password-strength-meter';

/**
 * Team create page (`/team/new`) — etapa-2-usuarios PR-5 T-5.5.
 *
 * <p>3-section Reactive Form for a {@code COMPANY_ADMIN} to
 * create a new user in their tenant. The form is composed of:
 *
 * <ol>
 *   <li>**Datos personales** — firstName, lastName (optional),
 *       email + username (required + uniqueness contract on the
 *       backend), plus an inline "Generar contraseña aleatoria"
 *       helper for the temporary password.</li>
 *   <li>**Contraseña temporal** — password + confirmation, with
 *       complexity validator (upper + lower + digit + min 8 chars),
 *       match validator, and the
 *       {@code <app-password-strength-meter>} primitive for live
 *       feedback.</li>
 *   <li>**Roles** — multi-select bound to the COMPANY role
 *       catalog loaded via {@code RolesService.listCompanyRoles()}.
 *       At least one role is required (backend-enforced AND
 *       client-side mirrored in the validator).</li>
 * </ol>
 *
 * <p>After a successful `POST /api/v1/company-users`, the page
 * surfaces the {@code <app-password-reveal-modal>} with the
 * generated `temporaryPassword`. The modal has two actions:
 * "Listo" (navigates back to `/team`) and "Crear otro" (resets
 * the form so the admin can immediately create another user).
 *
 * <p>The `username` and `email` async availability check is
 * intentionally NOT implemented in this PR — the spec leaves
 * it optional and the backend already surfaces
 * `USERNAME_ALREADY_TAKEN` / `EMAIL_ALREADY_TAKEN` on POST, which
 * the {@code errorInterceptor} maps to localized copy. Adding
 * a debounced availability call would require an extra endpoint
 * surface; deferred to a future iteration.
 *
 * <p>Standalone, OnPush, signal-first.
 */
@Component({
  selector: 'app-team-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MultiSelectComponent,
    PasswordRevealModalComponent,
    PasswordStrengthMeterComponent,
  ],
  templateUrl: './team-create.html',
})
export class TeamCreateComponent {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly companyUsersStore = inject(CompanyUsersStore);
  private readonly rolesService = inject(RolesService);

  /** Roles catalog loaded from {@code RolesService.listCompanyRoles()}.
   * Public for the template — the multi-select binds to it. */
  readonly availableRoles = signal<Role[]>([]);

  /** Local UI state. */
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  /** Password reveal modal state. Public for test access (mirrors
   * the {@code team-detail} pattern). */
  readonly showPasswordModal = signal<boolean>(false);
  readonly passwordModalData = signal<{
    temporaryPassword: string;
    warning: string;
    username: string;
  } | null>(null);

  /** Currently-selected role ids — a signal that the multi-select
   * writes to via {@code selectedChange} and the form reads
   * when it commits on submit. Keeps the multi-select fully
   * decoupled from the form (the multi-select is presentational
   * only — see {@code shared/ui/multi-select.ts}). */
  readonly selectedRoleIds = signal<string[]>([]);

  /** The reactive form. Public for test access. The control names
   * are kept simple to make the template ergonomic. */
  readonly form: FormGroup = this.fb.group({
    firstName: ['', [Validators.maxLength(100)]],
    lastName: ['', [Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(120)]],
    username: [
      '',
      [
        Validators.required,
        Validators.minLength(3),
        Validators.maxLength(30),
        Validators.pattern(/^[a-z0-9._-]+$/),
      ],
    ],
    password: [
      '',
      [
        Validators.required,
        Validators.minLength(8),
        TeamCreateComponent.passwordComplexityValidator,
      ],
    ],
    passwordConfirmation: ['', [Validators.required]],
    roleIds: [
      [] as string[],
      [Validators.required, TeamCreateComponent.atLeastOneRoleValidator],
    ],
  }, {
    validators: TeamCreateComponent.matchPasswordValidator,
  });

  /** Convenience access to the password control for the strength
   * meter binding. The meter only needs the current value, so
   * we read from `form.controls.password.value` directly. */
  protected get passwordValue(): string {
    return (this.form.get('password')?.value as string) ?? '';
  }

  constructor() {
    this.loadRoles();
  }

  /** Fetch the COMPANY role catalog and populate
   * {@code availableRoles}. Errors surface via the global toast
   * (the {@code errorInterceptor} renders the localized copy). */
  private loadRoles(): void {
    this.rolesService.listCompanyRoles().subscribe({
      next: (roles) => this.availableRoles.set(roles),
      error: () => {
        // Keep the previous value if any; the template renders
        // nothing for the multi-select when the array is empty.
      },
    });
  }

  /** Handler for the {@code selectedChange} output of the
   * {@code <app-multi-select>}. Keeps both the roleIds form
   * control AND the {@code selectedRoleIds} signal in sync so
   * the multi-select re-renders correctly with the latest state.
   * Public for test access. */
  onRolesChange(ids: string[]): void {
    this.selectedRoleIds.set(ids);
    this.form.controls['roleIds']!.setValue(ids);
    this.form.controls['roleIds']!.markAsTouched();
  }

  /** Generate a 12-char password using {@code crypto.getRandomValues}
   * with at least one character from each of the 4 classes
   * (upper, lower, digit, symbol). The result is shuffled so
   * the first 4 chars are not always upper+lower+digit+symbol.
   * Public for test access. */
  generatePassword(): void {
    const upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
    const lower = 'abcdefghjkmnpqrstuvwxyz';
    const digits = '23456789';
    const symbols = '!@#$%^&*';
    const all = upper + lower + digits + symbols;
    const length = 12;

    const arr = new Uint32Array(length);
    crypto.getRandomValues(arr);

    let pwd = '';
    // Guarantee at least one char per class.
    pwd += upper[arr[0]! % upper.length];
    pwd += lower[arr[1]! % lower.length];
    pwd += digits[arr[2]! % digits.length];
    pwd += symbols[arr[3]! % symbols.length];
    // Fill the remaining positions from the union pool.
    for (let i = 4; i < length; i++) {
      pwd += all[arr[i]! % all.length];
    }
    // Shuffle so the position of the 4 guaranteed-class chars
    // is not predictable.
    pwd = pwd
      .split('')
      .sort(() => Math.random() - 0.5)
      .join('');

    this.form.patchValue({ password: pwd, passwordConfirmation: pwd });
  }

  /** Submit the form. Validates first; on success, fires the
   * POST and opens the password reveal modal. Public for test
   * access. */
  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.isLoading.set(true);
    this.errorMessage.set(null);

    const v = this.form.getRawValue() as {
      firstName: string;
      lastName: string;
      email: string;
      username: string;
      password: string;
      passwordConfirmation: string;
      roleIds: string[];
    };

    const req: CreateCompanyUserRequest = {
      username: v.username,
      email: v.email,
      firstName: v.firstName ? v.firstName : undefined,
      lastName: v.lastName ? v.lastName : undefined,
      password: v.password,
      roleIds: v.roleIds,
    };

    this.companyUsersStore
      .create(req)
      .then((response: CreateCompanyUserResponse) => {
        this.passwordModalData.set({
          temporaryPassword: response.temporaryPassword,
          warning: response.passwordWarning,
          username: response.user.username,
        });
        this.showPasswordModal.set(true);
        this.isLoading.set(false);
      })
      .catch(() => {
        this.errorMessage.set('No pudimos crear el usuario. Probá de nuevo.');
        this.isLoading.set(false);
      });
  }

  /** Close the password reveal modal and navigate back to the
   * team list. Public for test access. */
  onPasswordModalDismiss(): void {
    this.showPasswordModal.set(false);
    void this.router.navigate(['/auth/team']);
  }

  /** Close the modal, clear its data, and reset the form so the
   * admin can immediately create another user. Public for test
   * access. */
  onPasswordModalCreateAnother(): void {
    this.showPasswordModal.set(false);
    this.passwordModalData.set(null);
    this.form.reset();
    this.selectedRoleIds.set([]);
  }

  // -------- pure validators (static for easy mocking / reuse) --------

  /** Password complexity: upper + lower + digit + min 8 chars.
   * The min-length check is handled by {@code Validators.minLength(8)}
   * on the control; this validator only enforces the 3-class
   * requirement so the strength-meter can show partial progress. */
  static passwordComplexityValidator(control: AbstractControl): ValidationErrors | null {
    const value = (control.value as string | null) ?? '';
    if (value.length === 0) return null;
    const hasUpper = /[A-Z]/.test(value);
    const hasLower = /[a-z]/.test(value);
    const hasDigit = /\d/.test(value);
    return hasUpper && hasLower && hasDigit ? null : { complexity: true };
  }

  /** At-least-one-role validator for an array control. Mirrors
   * the backend's `roleIds: "at least one role required"` rule. */
  static atLeastOneRoleValidator(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    return Array.isArray(value) && value.length > 0 ? null : { required: true };
  }

  /** Group-level validator: password === passwordConfirmation. */
  static matchPasswordValidator(group: AbstractControl): ValidationErrors | null {
    const password = group.get('password')?.value;
    const confirmation = group.get('passwordConfirmation')?.value;
    if (!password || !confirmation) return null;
    return password === confirmation ? null : { passwordMismatch: true };
  }
}