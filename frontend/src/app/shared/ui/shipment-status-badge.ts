import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ShipmentStatus } from '../../core/types';

/**
 * Pair a {@link ShipmentStatus} with the design-system color
 * tokens the badge should render with, plus a human-readable
 * Spanish label.
 *
 * Color semantics (per Logistics Core design system):
 * - primary / secondary / tertiary / error / warning
 *   containers each map to a stage in the shipment lifecycle:
 *   - primary = "starting" (pre-creation, awaiting input)
 *   - secondary = "received / classified" (in-branch)
 *   - tertiary = "in motion / delivered" (success)
 *   - error = "delivery failed / cancelled"
 *   - warning = "needs attention" (held / incident)
 *
 * Mapping intentionally groups statuses by lifecycle stage,
 * not by enum ordinal, so the UI tells a consistent story.
 */
function statusCopy(status: ShipmentStatus): {
  label: string;
  containerClass: string;
} {
  switch (status) {
    case 'PRE_ALTA':
      return {
        label: 'Pre-alta',
        containerClass: 'bg-primary-container text-on-primary-container',
      };
    case 'ESPERANDO_APROBACION_RUTA':
      return {
        label: 'Esperando aprobación de ruta',
        containerClass: 'bg-primary-container text-on-primary-container',
      };
    case 'CREADO':
      return {
        label: 'Creado',
        containerClass: 'bg-primary-container text-on-primary-container',
      };
    case 'RECIBIDO_EN_SUCURSAL_ORIGEN':
      return {
        label: 'Recibido en sucursal de origen',
        containerClass: 'bg-secondary-container text-on-secondary-container',
      };
    case 'RETENIDO_DOCUMENTACION':
      return {
        label: 'Retenido por documentación',
        containerClass: 'bg-secondary-container text-on-secondary-container',
      };
    case 'CLASIFICADO':
      return {
        label: 'Clasificado',
        containerClass: 'bg-secondary-container text-on-secondary-container',
      };
    case 'EN_TRANSITO_A_HUB':
      return {
        label: 'En tránsito a hub',
        containerClass: 'bg-tertiary-container text-on-tertiary-container',
      };
    case 'EN_HUB':
      return {
        label: 'En hub',
        containerClass: 'bg-tertiary-container text-on-tertiary-container',
      };
    case 'EN_TRANSITO_CON_ALIADO':
      return {
        label: 'En tránsito con aliado',
        containerClass: 'bg-tertiary-container text-on-tertiary-container',
      };
    case 'EN_TRANSITO_A_DESTINO':
      return {
        label: 'En tránsito a destino',
        containerClass: 'bg-tertiary-container text-on-tertiary-container',
      };
    case 'RECIBIDO_EN_SUCURSAL_DESTINO':
      return {
        label: 'Recibido en sucursal de destino',
        containerClass: 'bg-tertiary-container text-on-tertiary-container',
      };
    case 'EN_REPARTO':
      return {
        label: 'En reparto',
        containerClass: 'bg-tertiary-container text-on-tertiary-container',
      };
    case 'ENTREGADO':
      return {
        label: 'Entregado',
        containerClass: 'bg-tertiary text-on-tertiary',
      };
    case 'ENTREGADO_PARCIAL':
      return {
        label: 'Entrega parcial',
        containerClass: 'bg-tertiary-container text-on-tertiary-container',
      };
    case 'ENTREGA_FALLIDA':
      return {
        label: 'Entrega fallida',
        containerClass: 'bg-error-container text-on-error-container',
      };
    case 'DEVUELTO':
      return {
        label: 'Devuelto',
        containerClass: 'bg-error-container text-on-error-container',
      };
    case 'CANCELADO':
      return {
        label: 'Cancelado',
        containerClass: 'bg-error-container text-on-error-container',
      };
    case 'RETENIDO':
      return {
        label: 'Retenido',
        containerClass: 'bg-warning-container text-on-warning-container',
      };
    case 'INCIDENTE_ACTIVO':
      return {
        label: 'Incidente activo',
        containerClass: 'bg-warning-container text-on-warning-container',
      };
    case 'DEVOLUCION_INICIADA':
      return {
        label: 'Devolución iniciada',
        containerClass: 'bg-warning-container text-on-warning-container',
      };
    default:
      // Defensive — handles future status additions the
      // badge doesn't yet know about. Defaults to the neutral
      // surface palette so the UI never goes blank.
      return {
        label: String(status),
        containerClass: 'bg-surface-container-high text-on-surface-variant',
      };
  }
}

/**
 * ShipmentStatusBadge — visual pill for a {@link ShipmentStatus}.
 *
 * Used by the shipment list / detail / timeline pages in
 * etapa-3-envios. Renders a compact pill with the Spanish
 * status label and a background color keyed off the lifecycle
 * stage (see {@link statusCopy} for the full mapping).
 *
 * Colors come from existing design-system tokens — primary,
 * secondary, tertiary, error, and warning containers — pulled
 * from /styles.css. No raw colors and no new tokens.
 *
 * Standalone, OnPush, signal-input API.
 *
 * Usage:
 *   <app-shipment-status-badge [status]="shipment.status" />
 */
@Component({
  selector: 'app-shipment-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      [attr.data-status]="status()"
      [class]="classes()"
      [attr.aria-label]="ariaLabel()"
    >
      {{ label() }}
    </span>
  `,
})
export class ShipmentStatusBadgeComponent {
  readonly status = input.required<ShipmentStatus>();

  protected readonly copy = computed(() => statusCopy(this.status()));
  protected readonly label = computed(() => this.copy().label);
  protected readonly classes = computed(
    () =>
      this.copy().containerClass +
      ' inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold',
  );
  protected readonly ariaLabel = computed<string>(() => `Estado: ${this.label()}`);
}
