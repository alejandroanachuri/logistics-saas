import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Public Privacy Policy page. Standalone component — same
 * rationale as {@link TermsComponent}: the page is small
 * enough that the minimal brand-and-back link IS the layout.
 *
 * <p>Placeholder copy per gap #5 of the F1 wrap-up CHANGELOG.
 * The structure follows the typical sections of a privacy
 * policy (data collected, purpose, sharing, retention,
 * user rights, contact) so swapping in the final legal text
 * is a string-only change.
 */
@Component({
  selector: 'app-privacy',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './privacy.html',
})
export class PrivacyComponent {}
