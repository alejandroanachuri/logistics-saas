import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { Subject } from 'rxjs';
import { vi } from 'vitest';

import { AuthLayoutComponent } from './auth-layout';
import { AuthStore } from '../../../core/state/auth-store';
import { TenantStore } from '../../../core/state/tenant-store';
import { AuthService } from '../../../core/services/auth.service';
import { AuthUser } from '../../../core/types';

/**
 * Build an AuthUser fixture for the auth store. Mirrors the
 * helper in `core/guards/team-access.guard.spec.ts` and
 * `core/state/auth-store.spec.ts` so the test data shape
 * matches what the rest of the suite uses.
 */
function userWithRoles(roles: string[]): AuthUser {
  return {
    id: 'u1',
    tenantId: 't1',
    tenantSlug: 'mvr',
    username: 'juan',
    email: 'juan@test.com',
    firstName: 'Juan',
    lastName: 'Pérez',
    role: roles[0] ?? '',
    roles,
    scope: 'COMPANY',
    emailVerified: true,
  };
}

/**
 * Render the auth layout with the auth store seeded with
 * the given roles, and emit a NavigationEnd on the router
 * events stream so the title computation runs as in real
 * navigation. Returns the fixture + helpers.
 */
function render(roles: string[] | null, currentUrl: string): {
  fixture: ComponentFixture<AuthLayoutComponent>;
  component: AuthLayoutComponent;
  navItems: () => readonly { label: string; route: string; disabled?: boolean; visible?: () => boolean }[];
  pageTitle: () => string;
} {
  const navigationSubject = new Subject<NavigationEnd>();
  const routerStub = {
    events: navigationSubject.asObservable(),
    navigate: vi.fn(),
    navigateByUrl: vi.fn(),
    url: currentUrl,
  };

  TestBed.configureTestingModule({
    imports: [AuthLayoutComponent],
    providers: [
      { provide: Router, useValue: routerStub },
      { provide: ActivatedRoute, useValue: { snapshot: {} } },
      AuthStore,
      TenantStore,
      { provide: AuthService, useValue: { me: () => ({ subscribe: () => undefined }) } },
    ],
  });

  const fixture = TestBed.createComponent(AuthLayoutComponent);
  const component = fixture.componentInstance;
  const authStore = TestBed.inject(AuthStore);
  if (roles !== null) {
    authStore.setUser(userWithRoles(roles));
  }
  // Drive the router URL so pageTitle() can resolve the route.
  navigationSubject.next(new NavigationEnd(0, currentUrl, currentUrl));
  fixture.detectChanges();

  return {
    fixture,
    component,
    navItems: () => component['navItems']() as readonly { label: string; route: string; disabled?: boolean; visible?: () => boolean }[],
    pageTitle: () => component['pageTitle'](),
  };
}

describe('AuthLayoutComponent — Equipo nav item (PR-5)', () => {
  // -------- warm-up (vitest orphan offset) --------

  it('warm-up — component instantiates', () => {
    const { component } = render(null, '/dashboard');
    expect(component).toBeTruthy();
  });

  // -------- role-gated nav item --------

  it('does not render Equipo nav item when no user is signed in', () => {
    const { navItems } = render(null, '/dashboard');
    const labels = navItems().map((i) => i.label);
    expect(labels).not.toContain('Equipo');
  });

  it('does not render Equipo nav item when the user is COMPANY_OPERATOR', () => {
    const { navItems } = render(['COMPANY_OPERATOR'], '/dashboard');
    const labels = navItems().map((i) => i.label);
    expect(labels).not.toContain('Equipo');
  });

  it('does not render Equipo nav item when the user is COMPANY_DRIVER', () => {
    const { navItems } = render(['COMPANY_DRIVER'], '/dashboard');
    const labels = navItems().map((i) => i.label);
    expect(labels).not.toContain('Equipo');
  });

  it('renders Equipo nav item when the user is COMPANY_ADMIN', () => {
    const { navItems } = render(['COMPANY_ADMIN'], '/dashboard');
    const labels = navItems().map((i) => i.label);
    expect(labels).toContain('Equipo');
    const equipo = navItems().find((i) => i.label === 'Equipo')!;
    expect(equipo.route).toBe('/auth/team');
  });

  it('renders Equipo nav item when the user has COMPANY_ADMIN among other roles', () => {
    const { navItems } = render(['COMPANY_OPERATOR', 'COMPANY_ADMIN'], '/dashboard');
    const labels = navItems().map((i) => i.label);
    expect(labels).toContain('Equipo');
  });

  // -------- route-driven page title --------

  it('page title is "Dashboard" on /dashboard', () => {
    const { pageTitle } = render(['COMPANY_ADMIN'], '/dashboard');
    expect(pageTitle()).toBe('Dashboard');
  });

  it('page title is "Equipo" on /team', () => {
    const { pageTitle } = render(['COMPANY_ADMIN'], '/team');
    expect(pageTitle()).toBe('Equipo');
  });

  it('page title is "Nuevo usuario" on /team/new', () => {
    const { pageTitle } = render(['COMPANY_ADMIN'], '/team/new');
    expect(pageTitle()).toBe('Nuevo usuario');
  });
});
