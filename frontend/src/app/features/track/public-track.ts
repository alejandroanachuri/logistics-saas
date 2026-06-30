import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';

import { PublicTrackService } from '../../core/services/public-track.service';
import { PublicTrackResponse } from '../../core/types';

/**
 * PublicTrackComponent — the customer-facing tracking page at
 * {@code /track/:lgstid} (etapa-3-envios / PR-7 Chunk C).
 *
 * <p>Renders the public-safe response from
 * {@code GET /api/v1/public/track/{trackingId}}: the tracking
 * code as a big monospace header, the human-readable status
 * message, the partial-delivery indicator, the package count +
 * total weight, the receiver's masked name, and a vertical
 * timeline of sanitized events (timestamp + message only — no
 * branch IDs, no operator IDs, no metadata).
 *
 * <p>The timeline is rendered INLINE rather than via the
 * shared {@code <app-tracking-timeline>} primitive. The reason
 * is shape: {@code <app-tracking-timeline>} expects the
 * internal {@link TrackingEvent} projection (with
 * {@code eventType}, {@code branchId}, {@code userId},
 * {@code metadata}), whereas the public endpoint deliberately
 * returns a smaller, PII-free projection
 * ({@code timestamp + message} only). Adapting one shape to
 * the other would mean fabricating fields the server did not
 * send — that is exactly the kind of "fake data" we want to
 * avoid on a public surface. The inline renderer is ~10 lines
 * and matches the layout primitives of the operator timeline
 * so the two views stay visually consistent.
 *
 * <p>Error handling:
 * <ul>
 *   <li>{@code 404} → "No encontramos un envío con ese código."</li>
 *   <li>Any other error → a generic Spanish fallback.</li>
 * </ul>
 * The component never shows raw error details — the backend's
 * canonical copy is reserved for authenticated surfaces.
 *
 * <p>Standalone, OnPush, signal-first. Uses {@code input.required}
 * for the route-param trackingId so it composes cleanly with
 * {@code withComponentInputBinding()} (which is the routing
 * convention for the rest of the app).
 */
@Component({
  selector: 'app-public-track',
  standalone: true,
  imports: [CommonModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './public-track.html',
})
export class PublicTrackComponent implements OnInit {
  private readonly publicTrackService = inject(PublicTrackService);

  /** Tracking id supplied by the route param {@code :lgstid}. */
  readonly trackingId = input.required<string>();

  /** Loaded response. {@code null} while the request is in
   * flight or when no successful load has happened yet. */
  protected readonly response = signal<PublicTrackResponse | null>(null);

  /** True while the {@code .track()} observable is open. */
  protected readonly isLoading = signal<boolean>(false);

  /** Localised error message — when non-null the page renders
   * the error state instead of the response view. */
  protected readonly errorMessage = signal<string | null>(null);

  /** True when the loaded response indicates a partial
   * delivery (some packages delivered, some still in transit). */
  protected readonly isPartial = computed<boolean>(() => this.response()?.isPartial === true);

  /** Total weight in kg, formatted with up to two decimals. */
  protected readonly formattedWeight = computed<string>(() => {
    const w = this.response()?.totalWeightKg;
    if (w === null || w === undefined) return '—';
    return `${w.toFixed(2)} kg`;
  });

  ngOnInit(): void {
    const id = this.trackingId();
    if (!id) {
      this.errorMessage.set('No encontramos un envío con ese código.');
      return;
    }
    this.load(id);
  }

  /** Fetch the public-safe response for the given tracking id. */
  private load(trackingId: string): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.publicTrackService.track(trackingId).subscribe({
      next: (resp) => {
        this.response.set(resp);
        this.isLoading.set(false);
      },
      error: (err: unknown) => {
        this.isLoading.set(false);
        if (err instanceof HttpErrorResponse && err.status === 404) {
          this.errorMessage.set('No encontramos un envío con ese código.');
        } else {
          this.errorMessage.set('No pudimos cargar el estado del envío.');
        }
      },
    });
  }
}
