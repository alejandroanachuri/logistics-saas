import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { RoleChipComponent } from './role-chip';
import { Role } from '../../core/types';

/**
 * Helper — build a minimal Role fixture.
 */
function makeRole(name: Role['name']): Role {
  return { id: `id-${name}`, name, description: `The ${name} role` };
}

describe('RoleChipComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [RoleChipComponent] });
  });

  it('renders the role name as the visible label', () => {
    const fixture = TestBed.createComponent(RoleChipComponent);
    fixture.componentRef.setInput('role', makeRole('COMPANY_ADMIN'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('COMPANY_ADMIN');
  });

  it('applies the primary token classes for COMPANY_ADMIN', () => {
    const fixture = TestBed.createComponent(RoleChipComponent);
    fixture.componentRef.setInput('role', makeRole('COMPANY_ADMIN'));
    fixture.detectChanges();

    const chip: HTMLElement = fixture.nativeElement.querySelector('[data-role]')!;
    expect(chip.dataset['role']).toBe('COMPANY_ADMIN');
    expect(chip.className).toContain('bg-primary');
    expect(chip.className).toContain('text-on-primary');
  });

  it('applies the tertiary token classes for COMPANY_OPERATOR', () => {
    const fixture = TestBed.createComponent(RoleChipComponent);
    fixture.componentRef.setInput('role', makeRole('COMPANY_OPERATOR'));
    fixture.detectChanges();

    const chip: HTMLElement = fixture.nativeElement.querySelector('[data-role]')!;
    expect(chip.dataset['role']).toBe('COMPANY_OPERATOR');
    expect(chip.className).toContain('bg-tertiary');
    expect(chip.className).toContain('text-on-tertiary');
  });

  it('applies the secondary token classes for COMPANY_DRIVER', () => {
    const fixture = TestBed.createComponent(RoleChipComponent);
    fixture.componentRef.setInput('role', makeRole('COMPANY_DRIVER'));
    fixture.detectChanges();

    const chip: HTMLElement = fixture.nativeElement.querySelector('[data-role]')!;
    expect(chip.dataset['role']).toBe('COMPANY_DRIVER');
    expect(chip.className).toContain('bg-secondary');
    expect(chip.className).toContain('text-on-secondary');
  });

  it('applies the neutral surface token classes for COMPANY_VIEWER', () => {
    const fixture = TestBed.createComponent(RoleChipComponent);
    fixture.componentRef.setInput('role', makeRole('COMPANY_VIEWER'));
    fixture.detectChanges();

    const chip: HTMLElement = fixture.nativeElement.querySelector('[data-role]')!;
    expect(chip.dataset['role']).toBe('COMPANY_VIEWER');
    expect(chip.className).toContain('bg-surface-container-high');
    expect(chip.className).toContain('text-on-surface-variant');
  });

  it('falls back to the neutral surface classes for unknown role names', () => {
    // Defensive — handles future role additions the chip
    // doesn't yet recognise. Defaults to the viewer palette.
    const fixture = TestBed.createComponent(RoleChipComponent);
    fixture.componentRef.setInput('role', makeRole('UNKNOWN_ROLE' as Role['name']));
    fixture.detectChanges();

    const chip: HTMLElement = fixture.nativeElement.querySelector('[data-role]')!;
    expect(chip.className).toContain('bg-surface-container-high');
  });
});
