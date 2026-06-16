import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ComponentFixture } from '@angular/core/testing';

import { PrivacyComponent } from './privacy';

describe('PrivacyComponent', () => {
  let fixture: ComponentFixture<PrivacyComponent>;
  let component: PrivacyComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });
    fixture = TestBed.createComponent(PrivacyComponent);
    component = fixture.componentInstance;
    fixture.autoDetectChanges();
  });

  it('warm-up: component instantiates', () => {
    expect(component).toBeTruthy();
  });

  it('renders the privacy page header + back link', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="privacy-page"]')).not.toBeNull();
    const back = root.querySelector('[data-testid="privacy-back"]') as HTMLAnchorElement;
    expect(back).not.toBeNull();
    expect(back.getAttribute('href')).toBe('/');
  });

  it('renders all 8 privacy sections + the "versión preliminar" banner', () => {
    const root = fixture.nativeElement as HTMLElement;
    const sections = root.querySelectorAll('section');
    // 1 banner + 8 numbered sections (1. Datos … 8. Contacto)
    expect(sections.length).toBe(9);
  });

  it('exposes a mailto: link for privacy inquiries', () => {
    // Section 8 (Contacto) renders a mailto: link. This
    // is a typed cross-reference to the product/privacy
    // team; it does not depend on the router.
    const root = fixture.nativeElement as HTMLElement;
    const mailto = root.querySelector('a[href^="mailto:"]') as HTMLAnchorElement;
    expect(mailto).not.toBeNull();
    expect(mailto.getAttribute('href')).toContain('privacy@logistica.local');
  });
});
