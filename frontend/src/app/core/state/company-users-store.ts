import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';

import { CompanyUsersService } from '../services/company-users.service';
import { RolesService } from '../services/roles.service';
import {
  CompanyUserDetail,
  CompanyUserSummary,
  CreateCompanyUserRequest,
  CreateCompanyUserResponse,
  ListCompanyUsersParams,
  PageResponse,
  ResetPasswordResponse,
  Role,
  UpdateCompanyUserRequest,
} from '../types';

export interface PaginationState {
  readonly page: number;
  readonly size: number;
  readonly total: number;
}

export interface ListFilters {
  readonly status?: 'ACTIVE' | 'DISABLED';
  readonly roleId?: string;
  readonly search?: string;
}

/**
 * CompanyUsersStore — signal-based state for the team
 * management pages (PR-5). Mirrors the {@code AuthStore}
 * pattern: signals for derived reads, methods for actions.
 *
 * <p>The store holds the current list (page slice), the
 * currently-loaded detail, the available roles catalog, and
 * the pagination/filter state. Actions delegate to the
 * service and update the relevant signals on success.
 *
 * <p>Error policy: on failure, the relevant signal keeps its
 * previous value (so the UI does not flash empty during an
 * intermittent failure) and the error is rethrown to the
 * caller. Callers catch and surface the localized copy from
 * the {@code errorInterceptor}.
 */
@Injectable({ providedIn: 'root' })
export class CompanyUsersStore {
  private readonly companyUsersService = inject(CompanyUsersService);
  private readonly rolesService = inject(RolesService);

  private readonly _currentCompanyUsers = signal<CompanyUserSummary[] | null>(null);
  private readonly _currentCompanyUser = signal<CompanyUserDetail | null>(null);
  private readonly _availableRoles = signal<Role[]>([]);
  private readonly _pagination = signal<PaginationState>({ page: 1, size: 10, total: 0 });
  private readonly _isLoading = signal<boolean>(false);

  readonly currentCompanyUsers = this._currentCompanyUsers.asReadonly();
  readonly currentCompanyUser = this._currentCompanyUser.asReadonly();
  readonly availableRoles = this._availableRoles.asReadonly();
  readonly pagination = this._pagination.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();

  /** Convenience flag: the store has loaded at least one page
   * and the list is currently empty. The team-list page uses
   * this to decide between {@code <app-data-table>} and
   * {@code <app-empty-state>}. */
  readonly isListEmpty = computed(
    () => !this._isLoading() && (this._currentCompanyUsers()?.length ?? 0) === 0,
  );

  /** Fetch a page of company users and update the list +
   * pagination signals on success. */
  async loadList(filters: ListCompanyUsersParams): Promise<void> {
    this._isLoading.set(true);
    try {
      const page: PageResponse<CompanyUserSummary> = await firstValueFrom(
        this.companyUsersService.list(filters),
      );
      this._currentCompanyUsers.set(page.data);
      this._pagination.set({ page: page.page, size: page.size, total: page.total });
    } finally {
      this._isLoading.set(false);
    }
  }

  /** Fetch a single company user detail and update the
   * currentCompanyUser signal. */
  async loadDetail(id: string): Promise<CompanyUserDetail> {
    const detail = await firstValueFrom(this.companyUsersService.get(id));
    this._currentCompanyUser.set(detail);
    return detail;
  }

  /** Create a new company user. Returns the create envelope
   * (detail + temporary password) so the caller can surface
   * the password reveal modal. */
  async create(req: CreateCompanyUserRequest): Promise<CreateCompanyUserResponse> {
    return firstValueFrom(this.companyUsersService.create(req));
  }

  /** Update an existing company user (info + roles). Returns
   * the updated detail. */
  async update(id: string, req: UpdateCompanyUserRequest): Promise<CompanyUserDetail> {
    return firstValueFrom(this.companyUsersService.update(id, req));
  }

  /** Disable a user. Returns void; caller refetches the
   * detail or list to refresh the view. */
  async disable(id: string): Promise<void> {
    await firstValueFrom(this.companyUsersService.disable(id));
  }

  /** Reactivate a disabled user. Returns the updated detail. */
  async reactivate(id: string): Promise<CompanyUserDetail> {
    return firstValueFrom(this.companyUsersService.reactivate(id));
  }

  /** Reset a user's password. Returns the envelope with the
   * new temporary password so the caller can surface the
   * reveal modal. */
  async resetPassword(id: string): Promise<ResetPasswordResponse> {
    return firstValueFrom(this.companyUsersService.resetPassword(id));
  }

  /** Fetch the company role catalog (cached internally by
   * {@code RolesService}). */
  async loadRoles(): Promise<void> {
    const roles = await firstValueFrom(this.rolesService.listCompanyRoles());
    this._availableRoles.set(roles);
  }

  /** Clear the cached detail (used by the team-edit / team-detail
   * pages when navigating away). */
  clearDetail(): void {
    this._currentCompanyUser.set(null);
  }
}
