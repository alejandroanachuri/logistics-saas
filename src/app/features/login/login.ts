import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

/**
 * The {@code /login} page (F1 sub-spec §"Login Page", PRD §
 * "Login flow"). Standalone Angular 21 component — no
 * {@code NgModule}.
 *
 * <p><strong>PR11b scope</strong> (this commit): form shell
 * + render + the no-op {@code onSubmit} handler. The
 * component is wired into the router only by PR11c, which
 * also replaces this file's {@code onSubmit} stub with the
 * real {@code LoginService.submit(...)} call + post-success
 * navigation via {@code LoginService.safeReturnUrl()}.
 *
 * <p><strong>Validation rules</strong> mirror the backend
 * {@code SlugValidator}/{@code UsernameValidator}/
 * {@code PasswordValidator} (PR2b1) so the form is blocked
 * client-side with the same constraints the server enforces:
 * <ul>
 *   <li>slug: required, 2-12 chars.</li>
 *   <li>username: required, 3-30 chars.</li>
 *   <li>password: required, 8-128 chars.</li>
 * </ul>
 *
 * <p>The template ships a placeholder
 * {@code <div role="status" aria-live="polite" class="error">}
 * region that renders the error copy once PR11c binds the
 * error message into it. Today the region is empty by design.
 *
 * <p>The "Olvidaste tu contraseña?" affordance is a disabled
 * {@code <button>} with a {@code title="Próximamente"}
 * tooltip. PR11c may re-render it as a link; the test
 * accepts either.
 *
 * <p>The "Registrate" link is a {@code routerLink="/register"}
 * — it works today because the F1 {@code /register} route
 * already points to the {@code RegisterPlaceholder} (PR11+
 * PR12 swap in the register wizard).
 */
@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
})
export class LoginComponent {
  /**
   * Typed reactive form. The {@code FormGroup} shape is
   * {@code { slug, username, password }}; every control is
   * non-nullable so a missing value reads as the empty
   * string and the template does not need {@code ?.value}.
   */
  readonly form: FormGroup<{
    slug: FormControl<string>;
    username: FormControl<string>;
    password: FormControl<string>;
  }>;

  /**
   * Loading flag. PR11b never sets this to {@code true};
   * PR11c flips it before/after the {@code LoginService.submit}
   * observable settles. Exposed as a {@code signal} so the
   * template can read it directly under zoneless change
   * detection (Angular 21 default).
   */
  readonly isLoading = signal(false);

  /**
   * PR11c will inject {@code LoginService} here. The
   * service is not imported in PR11b because it is not yet
   * on the chain tip — {@code LoginService} lands in the
   * sibling PR11a branch and is brought onto the chain by
   * the main merge after PR11c lands.
   *
   * <p>Both {@code Router} and {@code ActivatedRoute} are
   * injected today so the constructor signature is
   * stable across the PR11b → PR11c transition: PR11c
   * only has to add the {@code LoginService} line.
   */
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
   * No-op in PR11b. Wired in PR11c — calls
   * {@code LoginService.submit} and navigates to
   * {@code safeReturnUrl()} on success.
   *
   * <p>PR11c will add {@code safeReturnUrl()} and pass it
   * to {@code router.navigate}.
   */
  onSubmit(): void {
    // Intentionally empty. PR11b ships the form + render
    // shell only; the real submit/error wiring lands in
    // PR11c. Keeping this method as a no-op (rather than
    // throwing) means the production build remains green
    // and the (ngSubmit) handler in the template does not
    // need a stub.
    void this.router;
    void this.route;
  }
}
