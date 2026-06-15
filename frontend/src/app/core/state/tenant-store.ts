import { Injectable, signal } from '@angular/core';
import { Tenant } from '../types';

/**
 * Signal-based tenant state. Set by {@code AuthService} after
 * a successful login (from the {@code user.tenantId} +
 * {@code user.tenantSlug} on the response), cleared on logout
 * and on the {@code errorInterceptor}'s forced-logout path.
 *
 * For F1 the store only carries the minimal projection the
 * dashboard reads ({@code id}, {@code slug}, optional
 * {@code legalName}). A future {@code /api/v1/company-users/me}
 * endpoint would query the DB and expand the shape.
 */
@Injectable({ providedIn: 'root' })
export class TenantStore {
  private readonly _currentTenant = signal<Tenant | null>(null);

  readonly currentTenant = this._currentTenant.asReadonly();

  setTenant(tenant: Tenant): void {
    this._currentTenant.set(tenant);
  }

  clear(): void {
    this._currentTenant.set(null);
  }
}
