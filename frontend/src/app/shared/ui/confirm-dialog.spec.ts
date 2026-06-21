import { TestBed } from '@angular/core/testing';

import { ConfirmDialogComponent } from './confirm-dialog';

describe('ConfirmDialogComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ConfirmDialogComponent] });
  });

  it('renders the title and message copy', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.componentRef.setInput('title', 'Deshabilitar usuario');
    fixture.componentRef.setInput('message', '¿Seguro que querés deshabilitar a juan?');
    fixture.componentRef.setInput('confirmLabel', 'Deshabilitar');
    fixture.componentRef.setInput('cancelLabel', 'Cancelar');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Deshabilitar usuario');
    expect(fixture.nativeElement.textContent).toContain('¿Seguro que querés deshabilitar a juan?');
  });

  it('uses the destructive (error) palette for the confirm button when destructive=true', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.componentRef.setInput('title', 'Deshabilitar usuario');
    fixture.componentRef.setInput('message', 'msg');
    fixture.componentRef.setInput('confirmLabel', 'Deshabilitar');
    fixture.componentRef.setInput('cancelLabel', 'Cancelar');
    fixture.componentRef.setInput('destructive', true);
    fixture.detectChanges();

    const confirm: HTMLButtonElement = fixture.nativeElement.querySelector('[data-confirm] button')!;
    expect(confirm.className).toContain('bg-error');
  });

  it('uses the primary palette for the confirm button when destructive=false', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.componentRef.setInput('title', 'Confirmar');
    fixture.componentRef.setInput('message', 'msg');
    fixture.componentRef.setInput('confirmLabel', 'Aceptar');
    fixture.componentRef.setInput('cancelLabel', 'Cancelar');
    fixture.componentRef.setInput('destructive', false);
    fixture.detectChanges();

    const confirm: HTMLButtonElement = fixture.nativeElement.querySelector('[data-confirm] button')!;
    expect(confirm.className).toContain('bg-primary');
  });

  it('emits confirm when the confirm button is clicked', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.componentRef.setInput('title', 'Confirmar');
    fixture.componentRef.setInput('message', 'msg');
    fixture.componentRef.setInput('confirmLabel', 'Aceptar');
    fixture.componentRef.setInput('cancelLabel', 'Cancelar');
    fixture.detectChanges();

    let emitted = 0;
    fixture.componentInstance.confirm.subscribe(() => emitted++);

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-confirm] button')!;
    btn.click();

    expect(emitted).toBe(1);
  });

  it('emits cancel when the cancel button is clicked', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.componentRef.setInput('title', 'Confirmar');
    fixture.componentRef.setInput('message', 'msg');
    fixture.componentRef.setInput('confirmLabel', 'Aceptar');
    fixture.componentRef.setInput('cancelLabel', 'Cancelar');
    fixture.detectChanges();

    let emitted = 0;
    fixture.componentInstance.cancel.subscribe(() => emitted++);

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-cancel] button')!;
    btn.click();

    expect(emitted).toBe(1);
  });

  it('renders the overlay with role=dialog and aria-modal=true', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.componentRef.setInput('title', 'Confirmar');
    fixture.componentRef.setInput('message', 'msg');
    fixture.componentRef.setInput('confirmLabel', 'Aceptar');
    fixture.componentRef.setInput('cancelLabel', 'Cancelar');
    fixture.detectChanges();

    const overlay: HTMLElement = fixture.nativeElement.querySelector('[data-overlay]')!;
    expect(overlay.getAttribute('role')).toBe('dialog');
    expect(overlay.getAttribute('aria-modal')).toBe('true');
  });

  it('emits cancel when the backdrop (overlay click outside the panel) is clicked', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.componentRef.setInput('title', 'Confirmar');
    fixture.componentRef.setInput('message', 'msg');
    fixture.componentRef.setInput('confirmLabel', 'Aceptar');
    fixture.componentRef.setInput('cancelLabel', 'Cancelar');
    fixture.detectChanges();

    let emitted = 0;
    fixture.componentInstance.cancel.subscribe(() => emitted++);

    const overlay: HTMLElement = fixture.nativeElement.querySelector('[data-overlay]')!;
    overlay.click();

    expect(emitted).toBe(1);
  });
});
