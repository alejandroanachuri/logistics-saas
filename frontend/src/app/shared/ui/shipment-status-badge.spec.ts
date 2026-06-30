import { TestBed } from '@angular/core/testing';

import { ShipmentStatusBadgeComponent } from './shipment-status-badge';

describe('ShipmentStatusBadgeComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ShipmentStatusBadgeComponent] });
  });

  it('renders the Spanish label for the status', () => {
    const fixture = TestBed.createComponent(ShipmentStatusBadgeComponent);
    fixture.componentRef.setInput('status', 'EN_TRANSITO_A_HUB');
    fixture.detectChanges();

    const badge: HTMLElement = fixture.nativeElement.querySelector('[data-status]')!;
    expect(badge.dataset['status']).toBe('EN_TRANSITO_A_HUB');
    expect(badge.textContent).toContain('En tránsito a hub');
  });

  it('applies the primary-container palette for PRE_ALTA', () => {
    const fixture = TestBed.createComponent(ShipmentStatusBadgeComponent);
    fixture.componentRef.setInput('status', 'PRE_ALTA');
    fixture.detectChanges();

    const badge: HTMLElement = fixture.nativeElement.querySelector('[data-status]')!;
    expect(badge.className).toContain('bg-primary-container');
    expect(badge.className).toContain('text-on-primary-container');
  });

  it('applies the secondary-container palette for CLASIFICADO', () => {
    const fixture = TestBed.createComponent(ShipmentStatusBadgeComponent);
    fixture.componentRef.setInput('status', 'CLASIFICADO');
    fixture.detectChanges();

    const badge: HTMLElement = fixture.nativeElement.querySelector('[data-status]')!;
    expect(badge.className).toContain('bg-secondary-container');
    expect(badge.className).toContain('text-on-secondary-container');
  });

  it('applies the tertiary palette (solid) for ENTREGADO', () => {
    const fixture = TestBed.createComponent(ShipmentStatusBadgeComponent);
    fixture.componentRef.setInput('status', 'ENTREGADO');
    fixture.detectChanges();

    const badge: HTMLElement = fixture.nativeElement.querySelector('[data-status]')!;
    // ENTREGADO is the "success" state — uses the solid
    // tertiary (not container) so it stands out in the list.
    expect(badge.className).toContain('bg-tertiary');
    expect(badge.className).toContain('text-on-tertiary');
    expect(badge.className).not.toContain('bg-tertiary-container');
  });

  it('applies the error-container palette for CANCELADO', () => {
    const fixture = TestBed.createComponent(ShipmentStatusBadgeComponent);
    fixture.componentRef.setInput('status', 'CANCELADO');
    fixture.detectChanges();

    const badge: HTMLElement = fixture.nativeElement.querySelector('[data-status]')!;
    expect(badge.className).toContain('bg-error-container');
    expect(badge.className).toContain('text-on-error-container');
  });

  it('applies the warning-container palette for INCIDENTE_ACTIVO', () => {
    const fixture = TestBed.createComponent(ShipmentStatusBadgeComponent);
    fixture.componentRef.setInput('status', 'INCIDENTE_ACTIVO');
    fixture.detectChanges();

    const badge: HTMLElement = fixture.nativeElement.querySelector('[data-status]')!;
    expect(badge.className).toContain('bg-warning-container');
    expect(badge.className).toContain('text-on-warning-container');
  });

  it('falls back to the neutral surface palette for unknown statuses', () => {
    // Defensive — handles future status additions the badge
    // doesn't yet recognise. The neutral surface palette
    // keeps the UI visually consistent instead of going blank.
    const fixture = TestBed.createComponent(ShipmentStatusBadgeComponent);
    fixture.componentRef.setInput('status', 'FUTURE_STATUS' as never);
    fixture.detectChanges();

    const badge: HTMLElement = fixture.nativeElement.querySelector('[data-status]')!;
    expect(badge.className).toContain('bg-surface-container-high');
    expect(badge.className).toContain('text-on-surface-variant');
  });
});
