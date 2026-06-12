import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Root component. Reduced to a router-outlet wrapper in
 * PR10; the layouts (PublicLayout, AuthenticatedLayout)
 * own the per-route chrome.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `<router-outlet />`,
})
export class App {}
