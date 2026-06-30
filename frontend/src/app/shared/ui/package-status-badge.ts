import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { PackageStatus } from '../../core/types';

/**
 * Pair a {@link PackageStatus} with the design-system color
 * tokens the badge should render with, plus a human-readable
 * Spanish label.
 *
 * <p>{@link PackageStatus} is a type alias for
 * {@link ShipmentStatus} (both use the same enum at the
 * backend), so the mapping is intentionally identical to
 * {@link ShipmentStatusBadgeComponent}'s. We duplicate the
 * helper instead of exporting it from the sibling component
 * because each badge is a standalone surface — a future
 * change to one (e.g. showing a different label for package
 * status vs shipment rollup) must NOT silently affect the
 * other.
 *
 * Color semantics:
 * - primary / secondary / tertiary / error / warning
 *   containers map to the package's lifecycle stage.
 * - ENTREGADO uses the solid tertiary (not container) so it
 *   stands out as the success state in the package list.
 */
function statusCopy(status: PackageStatus): {
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
      return {
        label: String(status),
        containerClass: 'bg-surface-container-high text-on-surface-variant',
      };
  }
}

/**
 * PackageStatusBadge — visual pill for a {@link PackageStatus}.
 *
 * Used by the package list / package detail rows inside the
 * shipment-detail page. Renders a compact pill with the
 * Spanish label and a background color keyed off the
 * lifecycle stage (see {@link statusCopy} for the full
 * mapping).
 *
 * Standalone, OnPush, signal-input API.
 *
 * Usage:
 *   <app-package-status-badge [status]="pkg.status" />
 */
@Component({
  selector: 'app-package-status-badge',
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
export class PackageStatusBadgeComponent {
  readonly status = input.required<PackageStatus>();

  protected readonly copy = computed(() => statusCopy(this.status()));
  protected readonly label = computed(() => this.copy().label);
  protected readonly classes = computed(
    () =>
      this.copy().containerClass +
      ' inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold',
  );
  protected readonly ariaLabel = computed<string>(() => `Estado: ${this.label()}`);
}
