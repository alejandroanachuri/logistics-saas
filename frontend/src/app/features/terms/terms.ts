import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Public Terms & Conditions page. Standalone component — no
 * PublicLayout wrapper because the page is so small that the
 * minimal brand-and-back link IS the layout. F2+ may revisit
 * this and wrap the page in PublicLayoutComponent if a richer
 * shared chrome (footer, language switcher, etc.) is needed.
 *
 * <p>The legal copy is a placeholder. The intent of this PR
 * (gap #5 of the F1 wrap-up CHANGELOG) is to remove the
 * `href="#"` placeholder from the ConfirmationStep's
 * "Términos y Condiciones" link and to give the user a real,
 * readable page when they click. The actual legal text is owned
 * by the product / legal team; this skeleton establishes the
 * section structure (scope, data, IP, liability, contact) and
 * the visual layout, so swapping in the final copy is a
 * string-only change.
 */
@Component({
  selector: 'app-terms',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './terms.html',
})
export class TermsComponent {}
