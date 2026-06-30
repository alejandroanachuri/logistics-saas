import { CommonModule, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { TrackingEvent } from '../../core/types';

/**
 * Translate an internal {@code eventType} (e.g. {@code "package_created"})
 * to a Spanish label suitable for the operator / customer timeline.
 *
 * <p>The backend event types follow a {@code <subject>_<verb>} pattern.
 * Unknown event types fall through to the raw identifier so the UI
 * never hides a record — operators see the code instead of an
 * empty line, which is the correct failure mode for an audit trail.
 */
function formatEventType(eventType: string): string {
  switch (eventType) {
    case 'package_created':
      return 'Paquete creado';
    case 'route_approved':
      return 'Ruta aprobada';
    case 'received_at_origin':
      return 'Recibido en sucursal de origen';
    case 'classified':
      return 'Clasificado';
    case 'in_transit_to_hub':
      return 'En tránsito a hub';
    case 'in_hub':
      return 'En hub';
    case 'in_transit_with_partner':
      return 'En tránsito con aliado';
    case 'in_transit_to_destination':
      return 'En tránsito a destino';
    case 'received_at_destination':
      return 'Recibido en sucursal de destino';
    case 'out_for_delivery':
      return 'En reparto';
    case 'delivered':
      return 'Entregado';
    case 'partial_delivery':
      return 'Entrega parcial';
    case 'delivery_failed':
      return 'Entrega fallida';
    case 'held':
      return 'Retenido';
    case 'incident_opened':
      return 'Incidente abierto';
    case 'incident_resolved':
      return 'Incidente resuelto';
    case 'return_initiated':
      return 'Devolución iniciada';
    case 'returned':
      return 'Devuelto';
    case 'cancelled':
      return 'Cancelado';
    case 'documentation_held':
      return 'Retenido por documentación';
    case 'documentation_released':
      return 'Documentación liberada';
    default:
      return eventType;
  }
}

/**
 * TrackingTimeline — vertical timeline of {@link TrackingEvent}s.
 *
 * <p>Used by the shipment-detail page (operator view) and the
 * public-tracking page (customer view). Events are rendered in
 * the order received (the timeline list is already sorted
 * server-side — see {@code ShipmentService.getTimeline()}).
 *
 * <p>Each row renders:
 * - a 2px primary-colored left rail to signal "this is the
 *   main thread of truth"
 * - the Spanish event label
 * - the event timestamp formatted via Angular's {@code date}
 *   pipe (locale-aware short format)
 * - optional metadata as a JSON {@code <pre>} block (operator
 *   view only — the public-tracking page passes an already
 *   sanitized projection that has no metadata)
 *
 * <p>Standalone, OnPush, signal-input API.
 *
 * Usage:
 *   <app-tracking-timeline [events]="shipment.events" />
 */
@Component({
  selector: 'app-tracking-timeline',
  standalone: true,
  imports: [CommonModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (events().length === 0) {
      <p class="text-sm text-on-surface-variant">Sin eventos registrados.</p>
    } @else {
      <ol class="space-y-4" data-tracking-timeline>
        @for (event of events(); track event.id) {
          <li class="border-l-2 border-primary pl-4 py-2" data-event>
            <p class="text-sm font-medium text-on-surface">
              {{ formatEventType(event.eventType) }}
            </p>
            <p class="text-xs text-on-surface-variant">
              {{ event.eventTimestamp | date: 'short' }}
            </p>
            @if (event.metadata) {
              <pre
                class="mt-1 max-w-full overflow-x-auto rounded bg-surface-container-low p-2 text-xs text-on-surface-variant"
                >{{ formatMetadata(event.metadata) }}</pre
              >
            }
          </li>
        }
      </ol>
    }
  `,
})
export class TrackingTimelineComponent {
  readonly events = input.required<TrackingEvent[]>();

  /**
   * Number of events — exposed for the parent to render a
   * counter ("12 eventos") without iterating twice.
   */
  readonly count = computed(() => this.events().length);

  protected formatEventType(eventType: string): string {
    return formatEventType(eventType);
  }

  protected formatMetadata(metadata: Record<string, unknown>): string {
    return JSON.stringify(metadata, null, 2);
  }
}
