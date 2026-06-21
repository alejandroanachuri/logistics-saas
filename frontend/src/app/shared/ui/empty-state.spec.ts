import { TestBed } from '@angular/core/testing';

import { EmptyStateComponent } from './empty-state';

describe('EmptyStateComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [EmptyStateComponent] });
  });

  it('renders the title and message copy', () => {
    const fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('icon', '👥');
    fixture.componentRef.setInput('title', 'Sin usuarios');
    fixture.componentRef.setInput('message', 'Todavía no agregaste a nadie en tu equipo.');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sin usuarios');
    expect(fixture.nativeElement.textContent).toContain('Todavía no agregaste a nadie en tu equipo.');
    expect(fixture.nativeElement.textContent).toContain('👥');
  });

  it('does NOT render the CTA button when ctaLabel is not provided', () => {
    const fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('icon', '👥');
    fixture.componentRef.setInput('title', 'Sin usuarios');
    fixture.componentRef.setInput('message', 'msg');
    fixture.detectChanges();

    const cta = fixture.nativeElement.querySelector('[data-cta]');
    expect(cta).toBeNull();
  });

  it('renders the CTA button when ctaLabel is provided', () => {
    const fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('icon', '👥');
    fixture.componentRef.setInput('title', 'Sin usuarios');
    fixture.componentRef.setInput('message', 'msg');
    fixture.componentRef.setInput('ctaLabel', 'Agregar usuario');
    fixture.detectChanges();

    const cta: HTMLButtonElement = fixture.nativeElement.querySelector('[data-cta]');
    expect(cta).toBeTruthy();
    expect(cta.textContent).toContain('Agregar usuario');
  });

  it('emits ctaClick when the CTA button is clicked', () => {
    const fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('icon', '👥');
    fixture.componentRef.setInput('title', 'Sin usuarios');
    fixture.componentRef.setInput('message', 'msg');
    fixture.componentRef.setInput('ctaLabel', 'Agregar usuario');
    fixture.detectChanges();

    let emitted = 0;
    fixture.componentInstance.ctaClick.subscribe(() => emitted++);

    const cta: HTMLButtonElement = fixture.nativeElement.querySelector('[data-cta]');
    cta.click();

    expect(emitted).toBe(1);
  });
});
