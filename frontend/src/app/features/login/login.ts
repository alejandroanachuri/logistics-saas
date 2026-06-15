import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { LoginService } from '../../core/services/login.service';
import { LoginRequest } from '../../core/types';

/**
 * The {@code /login} page (F1 sub-spec §"Login Page").
 * Standalone Angular 21 component — no {@code NgModule}.
 *
 * <p>PR11c wires the form: {@code LoginService.submit} +
 * post-success navigation via {@code LoginService.safeReturnUrl()}
 * + post-failure error rendering in the
 * {@code aria-live="polite"} region (via
 * {@code LoginService.extractErrorCopy}). The password input
 * gets a show/hide toggle (signal-backed).
 *
 * <p>Validation mirrors the backend
 * {@code SlugValidator}/{@code UsernameValidator}/
 * {@code PasswordValidator} (PR2b1) so the form is blocked
 * client-side with the same constraints the server enforces.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
})
export class LoginComponent {
  readonly form: FormGroup<{
    slug: FormControl<string>;
    username: FormControl<string>;
    password: FormControl<string>;
  }>;

  /**
   * Loading flag — flipped synchronously at the start of
   * {@code onSubmit} and back to {@code false} in both the
   * {@code next} and {@code error} branches of the subscribe.
   */
  readonly isLoading = signal(false);

  /**
   * Password visibility — read by the template to decide
   * whether the password input renders as {@code type="password"}
   * (masked) or {@code type="text"} (visible).
   */
  readonly isPasswordVisible = signal(false);

  /**
   * The Spanish error message to render in the
   * {@code aria-live="polite"} region. {@code null} means
   * "no error". Cleared at the start of every {@code onSubmit}
   * so a previous error does not persist across resubmits.
   */
  readonly errorMessage = signal<string | null>(null);

  private readonly loginService = inject(LoginService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  constructor(private readonly fb: NonNullableFormBuilder) {
    this.form = this.fb.group({
      slug: this.fb.control('', {
        validators: [Validators.required, Validators.minLength(2), Validators.maxLength(12)],
      }),
      username: this.fb.control('', {
        validators: [Validators.required, Validators.minLength(3), Validators.maxLength(30)],
      }),
      password: this.fb.control('', {
        validators: [Validators.required, Validators.minLength(8), Validators.maxLength(128)],
      }),
    });
  }

  /**
   * Handle the form's {@code (ngSubmit)} event. Flips
   * {@code isLoading}, clears {@code errorMessage}, calls
   * {@code LoginService.submit} and subscribes — navigating
   * to {@code LoginService.safeReturnUrl()} on success or
   * rendering the mapped Spanish error copy on failure.
   */
  onSubmit(): void {
    if (this.form.invalid) {
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const creds: LoginRequest = {
      slug: this.form.controls.slug.value,
      username: this.form.controls.username.value,
      password: this.form.controls.password.value,
    };

    this.loginService.submit(creds).subscribe({
      next: () => {
        this.isLoading.set(false);
        void this.router.navigateByUrl(this.loginService.safeReturnUrl());
      },
      error: (err: unknown) => {
        this.isLoading.set(false);
        const copy = this.loginService.extractErrorCopy(
          err as Parameters<LoginService['extractErrorCopy']>[0],
        );
        this.errorMessage.set(copy.message);
        // Log the full error for debugging; the user only
        // sees the mapped Spanish copy.
        // eslint-disable-next-line no-console
        console.error('[LoginComponent] submit failed', err);
      },
    });
  }

  /**
   * Flip the password visibility. Called by the show/hide
   * toggle button in the template (a {@code type="button"}
   * so it does not submit the form).
   */
  togglePasswordVisibility(): void {
    this.isPasswordVisible.update((v) => !v);
  }
}
