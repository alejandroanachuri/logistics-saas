import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ComponentFixture } from '@angular/core/testing';

import { TermsComponent } from './terms';

describe('TermsComponent', () => {
  let fixture: ComponentFixture<TermsComponent>;
  let component: TermsComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });
    fixture = TestBed.createComponent(TermsComponent);
    component = fixture.componentInstance;
    fixture.autoDetectChanges();
  });

  it('warm-up: component instantiates', () => {
    expect(component).toBeTruthy();
  });

  it('renders the terms page header + back link', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="terms-page"]')).not.toBeNull();
    const back = root.querySelector('[data-testid="terms-back"]') as HTMLAnchorElement;
    expect(back).not.toBeNull();
    expect(back.getAttribute('href')).toBe('/');
  });

  it('renders all 7 legal sections + the "versión preliminar" banner', () => {
    const root = fixture.nativeElement as HTMLElement;
    const sections = root.querySelectorAll('section');
    // 1 banner + 7 numbered sections (1. Aceptación … 7. Contacto)
    expect(sections.length).toBe(8);
  });
});
