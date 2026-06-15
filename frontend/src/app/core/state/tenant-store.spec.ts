import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { TenantStore } from './tenant-store';

describe('tenantStore (signal transitions)', () => {
  let store: TenantStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [TenantStore] });
    store = TestBed.inject(TenantStore);
  });

  it('starts empty: currentTenant null', () => {
    expect(store.currentTenant()).toBeNull();
  });

  it('setTenant sets the projection', () => {
    store.setTenant({ id: 't1', slug: 'mvr', legalName: 'MVR SA' });
    const t = store.currentTenant();
    expect(t).toEqual({ id: 't1', slug: 'mvr', legalName: 'MVR SA' });
  });

  it('setTenant replaces the previous projection (no stale tenant leak)', () => {
    store.setTenant({ id: 't1', slug: 'mvr' });
    store.setTenant({ id: 't2', slug: 'acme' });
    const t = store.currentTenant();
    expect(t?.id).toBe('t2');
    expect(t?.slug).toBe('acme');
  });

  it('clear() transitions back to null', () => {
    store.setTenant({ id: 't1', slug: 'mvr' });
    store.clear();
    expect(store.currentTenant()).toBeNull();
  });
});
