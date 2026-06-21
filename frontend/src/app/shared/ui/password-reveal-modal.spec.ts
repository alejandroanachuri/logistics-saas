import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { PasswordRevealModalComponent } from './password-reveal-modal';

describe('PasswordRevealModalComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PasswordRevealModalComponent] });
  });

  it('renders the temporary password in monospace inside a high-contrast container', () => {
    const fixture = TestBed.createComponent(PasswordRevealModalComponent);
    fixture.componentRef.setInput('username', 'juan');
    fixture.componentRef.setInput('temporaryPassword', 'TmpP@ssw0rd!');
    fixture.componentRef.setInput('warning', 'Compartí esta contraseña por un canal seguro.');
    fixture.detectChanges();

    const code: HTMLElement = fixture.nativeElement.querySelector('[data-password]')!;
    expect(code.textContent).toContain('TmpP@ssw0rd!');
    expect(code.className).toContain('font-mono');
  });

  it('renders the warning copy and the username', () => {
    const fixture = TestBed.createComponent(PasswordRevealModalComponent);
    fixture.componentRef.setInput('username', 'juan');
    fixture.componentRef.setInput('temporaryPassword', 'TmpP@ssw0rd!');
    fixture.componentRef.setInput('warning', 'Compartí esta contraseña por un canal seguro.');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('juan');
    expect(fixture.nativeElement.textContent).toContain('Compartí esta contraseña por un canal seguro.');
  });

  it('renders a "Copiar al portapapeles" button that copies the password via the Clipboard API', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });

    const fixture = TestBed.createComponent(PasswordRevealModalComponent);
    fixture.componentRef.setInput('username', 'juan');
    fixture.componentRef.setInput('temporaryPassword', 'TmpP@ssw0rd!');
    fixture.componentRef.setInput('warning', 'warning');
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-copy]')!;
    button.click();

    expect(writeText).toHaveBeenCalledTimes(1);
    expect(writeText).toHaveBeenCalledWith('TmpP@ssw0rd!');
  });

  it('emits the dismiss output when the close button is clicked', () => {
    const fixture = TestBed.createComponent(PasswordRevealModalComponent);
    fixture.componentRef.setInput('username', 'juan');
    fixture.componentRef.setInput('temporaryPassword', 'TmpP@ssw0rd!');
    fixture.componentRef.setInput('warning', 'warning');
    fixture.detectChanges();

    const emitted: number[] = [];
    fixture.componentInstance.dismiss.subscribe(() => emitted.push(1));

    const close: HTMLButtonElement = fixture.nativeElement.querySelector('[data-dismiss]')!;
    close.click();

    expect(emitted.length).toBe(1);
  });

  it('emits the createAnother output when the create-another button is clicked', () => {
    const fixture = TestBed.createComponent(PasswordRevealModalComponent);
    fixture.componentRef.setInput('username', 'juan');
    fixture.componentRef.setInput('temporaryPassword', 'TmpP@ssw0rd!');
    fixture.componentRef.setInput('warning', 'warning');
    fixture.componentRef.setInput('showCreateAnother', true);
    fixture.detectChanges();

    const emitted: number[] = [];
    fixture.componentInstance.createAnother.subscribe(() => emitted.push(1));

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-create-another]')!;
    btn.click();

    expect(emitted.length).toBe(1);
  });

  it('renders the overlay container with the modal role and aria-modal', () => {
    const fixture = TestBed.createComponent(PasswordRevealModalComponent);
    fixture.componentRef.setInput('username', 'juan');
    fixture.componentRef.setInput('temporaryPassword', 'TmpP@ssw0rd!');
    fixture.componentRef.setInput('warning', 'warning');
    fixture.detectChanges();

    const overlay: HTMLElement = fixture.nativeElement.querySelector('[data-overlay]')!;
    expect(overlay.getAttribute('role')).toBe('dialog');
    expect(overlay.getAttribute('aria-modal')).toBe('true');
  });
});
