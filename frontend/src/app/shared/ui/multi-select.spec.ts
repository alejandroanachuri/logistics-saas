import { TestBed } from '@angular/core/testing';

import { MultiSelectComponent } from './multi-select';
import { Role } from '../../core/types';

function makeRole(id: string, name: Role['name'], description: string | null = null): Role {
  return { id, name, description };
}

describe('MultiSelectComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [MultiSelectComponent] });
  });

  it('renders one checkbox per option with the role name + description', () => {
    const fixture = TestBed.createComponent(MultiSelectComponent);
    const options: Role[] = [
      makeRole('r-admin', 'COMPANY_ADMIN', 'Acceso total'),
      makeRole('r-viewer', 'COMPANY_VIEWER', 'Solo lectura'),
    ];
    fixture.componentRef.setInput('options', options);
    fixture.componentRef.setInput('selected', []);
    fixture.detectChanges();

    const checkboxes: NodeListOf<HTMLInputElement> =
      fixture.nativeElement.querySelectorAll('input[type="checkbox"]');
    expect(checkboxes.length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('COMPANY_ADMIN');
    expect(fixture.nativeElement.textContent).toContain('Acceso total');
    expect(fixture.nativeElement.textContent).toContain('COMPANY_VIEWER');
    expect(fixture.nativeElement.textContent).toContain('Solo lectura');
  });

  it('checks the checkbox when the option id is in the selected array', () => {
    const fixture = TestBed.createComponent(MultiSelectComponent);
    const options: Role[] = [makeRole('r-admin', 'COMPANY_ADMIN'), makeRole('r-viewer', 'COMPANY_VIEWER')];
    fixture.componentRef.setInput('options', options);
    fixture.componentRef.setInput('selected', ['r-admin']);
    fixture.detectChanges();

    const checkboxes: NodeListOf<HTMLInputElement> =
      fixture.nativeElement.querySelectorAll('input[type="checkbox"]');
    expect(checkboxes[0].checked).toBe(true);
    expect(checkboxes[1].checked).toBe(false);
  });

  it('emits selectedChange with the union when a checkbox is toggled on', () => {
    const fixture = TestBed.createComponent(MultiSelectComponent);
    const options: Role[] = [makeRole('r-admin', 'COMPANY_ADMIN'), makeRole('r-viewer', 'COMPANY_VIEWER')];
    fixture.componentRef.setInput('options', options);
    fixture.componentRef.setInput('selected', ['r-admin']);
    fixture.detectChanges();

    let emitted: string[] | undefined;
    fixture.componentInstance.selectedChange.subscribe((next) => (emitted = next));

    const checkboxes: NodeListOf<HTMLInputElement> =
      fixture.nativeElement.querySelectorAll('input[type="checkbox"]');
    checkboxes[1].click();

    expect(emitted).toEqual(['r-admin', 'r-viewer']);
  });

  it('emits selectedChange with the option removed when a checkbox is toggled off', () => {
    const fixture = TestBed.createComponent(MultiSelectComponent);
    const options: Role[] = [makeRole('r-admin', 'COMPANY_ADMIN'), makeRole('r-viewer', 'COMPANY_VIEWER')];
    fixture.componentRef.setInput('options', options);
    fixture.componentRef.setInput('selected', ['r-admin', 'r-viewer']);
    fixture.detectChanges();

    let emitted: string[] | undefined;
    fixture.componentInstance.selectedChange.subscribe((next) => (emitted = next));

    const checkboxes: NodeListOf<HTMLInputElement> =
      fixture.nativeElement.querySelectorAll('input[type="checkbox"]');
    checkboxes[0].click();

    expect(emitted).toEqual(['r-viewer']);
  });

  it('disables checkboxes whose ids are in disabledIds', () => {
    const fixture = TestBed.createComponent(MultiSelectComponent);
    const options: Role[] = [
      makeRole('r-admin', 'COMPANY_ADMIN'),
      makeRole('r-viewer', 'COMPANY_VIEWER'),
    ];
    fixture.componentRef.setInput('options', options);
    fixture.componentRef.setInput('selected', []);
    fixture.componentRef.setInput('disabledIds', ['r-admin']);
    fixture.detectChanges();

    const checkboxes: NodeListOf<HTMLInputElement> =
      fixture.nativeElement.querySelectorAll('input[type="checkbox"]');
    expect(checkboxes[0].disabled).toBe(true);
    expect(checkboxes[1].disabled).toBe(false);
  });

  it('does not emit selectedChange when a disabled checkbox is clicked', () => {
    const fixture = TestBed.createComponent(MultiSelectComponent);
    const options: Role[] = [makeRole('r-admin', 'COMPANY_ADMIN')];
    fixture.componentRef.setInput('options', options);
    fixture.componentRef.setInput('selected', []);
    fixture.componentRef.setInput('disabledIds', ['r-admin']);
    fixture.detectChanges();

    let emitted = 0;
    fixture.componentInstance.selectedChange.subscribe(() => emitted++);

    const cb: HTMLInputElement = fixture.nativeElement.querySelector('input[type="checkbox"]');
    cb.click();

    expect(emitted).toBe(0);
  });

  it('renders a warning banner when the selected ids include any of the adminRoleIds', () => {
    const fixture = TestBed.createComponent(MultiSelectComponent);
    const options: Role[] = [makeRole('r-admin', 'COMPANY_ADMIN'), makeRole('r-viewer', 'COMPANY_VIEWER')];
    fixture.componentRef.setInput('options', options);
    fixture.componentRef.setInput('selected', ['r-admin']);
    fixture.componentRef.setInput('adminRoleIds', ['r-admin']);
    fixture.detectChanges();

    const warning: HTMLElement | null = fixture.nativeElement.querySelector('[data-admin-warning]');
    expect(warning).toBeTruthy();
    expect(warning!.textContent).toContain('administradores');
  });

  it('does NOT render the warning banner when the selected ids exclude the admin role', () => {
    const fixture = TestBed.createComponent(MultiSelectComponent);
    const options: Role[] = [makeRole('r-admin', 'COMPANY_ADMIN'), makeRole('r-viewer', 'COMPANY_VIEWER')];
    fixture.componentRef.setInput('options', options);
    fixture.componentRef.setInput('selected', ['r-viewer']);
    fixture.componentRef.setInput('adminRoleIds', ['r-admin']);
    fixture.detectChanges();

    const warning = fixture.nativeElement.querySelector('[data-admin-warning]');
    expect(warning).toBeNull();
  });
});
