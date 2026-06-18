import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Card — design-system container for grouped content.
 *
 * Implements the cards section of /DESIGN.md. White
 * background, 1px slate 200 border, 8px corner radius.
 * Padding 24px on desktop, 16px on mobile.
 *
 * Two slots:
 * - [header] projection (optional) — rendered at the top
 *   of the card, separated by a 1px divider.
 * - default `<ng-content>` — the main body.
 *
 * Usage:
 *   <app-card>
 *     <div card-header>Card title</div>
 *     <p>Body content here.</p>
 *   </app-card>
 *
 * The [elevated] input adds the soft shadow from
 * /DESIGN.md §"Elevation" for floating elements.
 */
@Component({
  selector: 'app-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div [class]="cardClasses()">
      @if (header()) {
        <div class="border-b border-outline-variant px-6 py-4">
          <ng-content select="[card-header]" />
        </div>
      }
      <div [class]="bodyClasses()">
        <ng-content />
      </div>
    </div>
  `,
})
export class CardComponent {
  readonly elevated = input<boolean>(false);
  readonly padded = input<boolean>(true);
  readonly header = input<boolean>(false);

  protected cardClasses(): string {
    const elev = this.elevated() ? 'elev-1' : '';
    return `rounded-md border border-outline-variant bg-surface-container-lowest ${elev}`.trim();
  }

  protected bodyClasses(): string {
    return this.padded() ? 'p-6' : '';
  }
}
