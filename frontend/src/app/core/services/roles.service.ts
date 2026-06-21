import { Injectable, effect, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of, tap } from 'rxjs';

import { TenantStore } from '../state/tenant-store';
import { Role } from '../types';

/**
 * RolesService — typed wrapper over `GET /api/v1/roles?scope=COMPANY`.
 *
 * <p>Since the role catalog is small (4 roles) and rarely changes,
 * the service caches the result in-memory and reuses it across
 * subscriptions within the same tenant. When the tenant changes
 * (via `TenantStore`), the cache is invalidated and the next call
 * refetches.
 *
 * <p>The cache is keyed by tenant id. If the user is unauthenticated
 * (no tenant in the store), the service falls through to a fresh
 * fetch on every call (no caching across anonymous states).
 */
@Injectable({ providedIn: 'root' })
export class RolesService {
  private readonly http = inject(HttpClient);
  private readonly tenantStore = inject(TenantStore);

  /** Per-tenant cache. Cleared whenever `tenantStore.currentTenant()` changes. */
  private readonly _cache = signal<Map<string, Role[]>>(new Map());

  constructor() {
    // Invalidate cache when the tenant changes.
    effect(() => {
      this.tenantStore.currentTenant();
      this._cache.set(new Map());
    });
  }

  listCompanyRoles(): Observable<Role[]> {
    const tenantId = this.tenantStore.currentTenant()?.id ?? null;
    const cache = this._cache();
    if (tenantId !== null) {
      const cached = cache.get(tenantId);
      if (cached !== undefined) {
        return of(cached);
      }
    }

    const params = new HttpParams().set('scope', 'COMPANY');
    return this.http.get<Role[]>('/api/v1/roles', { params }).pipe(
      tap((roles) => {
        if (tenantId !== null) {
          const next = new Map(this._cache());
          next.set(tenantId, roles);
          this._cache.set(next);
        }
      }),
    );
  }
}
