import { TestBed } from '@angular/core/testing';

import { StatusBadgeComponent } from './status-badge';

describe('StatusBadgeComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [StatusBadgeComponent] });
  });

  it('renders "Activo" with the tertiary-container palette when status is ACTIVE', () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentRef.setInput('status', 'ACTIVE');
    fixture.detectChanges();

    const badge: HTMLElement = fixture.nativeElement.querySelector('[data-status]')!;
    expect(badge.dataset['status']).toBe('ACTIVE');
    expect(badge.textContent).toContain('Activo');
    expect(badge.className).toContain('bg-tertiary-container');
    expect(badge.className).toContain('text-on-tertiary-container');
  });

  it('renders "Deshabilitado" with the error-container palette when status is DISABLED', () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentRef.setInput('status', 'DISABLED');
    fixture.detectChanges();

    const badge: HTMLElement = fixture.nativeElement.querySelector('[data-status]')!;
    expect(badge.dataset['status']).toBe('DISABLED');
    expect(badge.textContent).toContain('Deshabilitado');
    expect(badge.className).toContain('bg-error-container');
    expect(badge.className).toContain('text-on-error-container');
  });

  it('uses a non-empty visual indicator (status dot) in addition to the label', () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentRef.setInput('status', 'ACTIVE');
    fixture.detectChanges();

    const dot: HTMLElement = fixture.nativeElement.querySelector('[data-status-dot]')!;
    expect(dot).toBeTruthy();
    expect(dot.className).toContain('rounded-full');
  });
});
