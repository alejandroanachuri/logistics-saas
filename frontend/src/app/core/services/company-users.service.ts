import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  CompanyUserDetail,
  CompanyUserSummary,
  CreateCompanyUserRequest,
  CreateCompanyUserResponse,
  ListCompanyUsersParams,
  PageResponse,
  ResetPasswordResponse,
  UpdateCompanyUserRequest,
} from '../types';

/**
 * CompanyUsersService — typed wrapper over the 7 admin
 * endpoints under `/api/v1/company-users/*`. All methods
 * return `Observable<T>` so callers can compose them with
 * RxJS operators. Error mapping is delegated to the global
 * `errorInterceptor` — the service itself does no envelope
 * parsing; callers subscribe to the typed error path when
 * they need a specific copy.
 */
@Injectable({ providedIn: 'root' })
export class CompanyUsersService {
  private readonly http = inject(HttpClient);
  private static readonly BASE = '/api/v1/company-users';

  /** `GET /api/v1/company-users?page=...&size=...&sort=...&status=...&roleId=...&search=...`. */
  list(params: ListCompanyUsersParams): Observable<PageResponse<CompanyUserSummary>> {
    let httpParams = new HttpParams();
    if (params.page !== undefined) httpParams = httpParams.set('page', String(params.page));
    if (params.size !== undefined) httpParams = httpParams.set('size', String(params.size));
    if (params.sort !== undefined) httpParams = httpParams.set('sort', params.sort);
    if (params.status !== undefined) httpParams = httpParams.set('status', params.status);
    if (params.roleId !== undefined) httpParams = httpParams.set('roleId', params.roleId);
    if (params.search !== undefined) httpParams = httpParams.set('search', params.search);

    return this.http.get<PageResponse<CompanyUserSummary>>(CompanyUsersService.BASE, {
      params: httpParams,
    });
  }

  /** `GET /api/v1/company-users/{id}`. */
  get(id: string): Observable<CompanyUserDetail> {
    return this.http.get<CompanyUserDetail>(`${CompanyUsersService.BASE}/${id}`);
  }

  /** `POST /api/v1/company-users`. Returns the create envelope
   * (detail + temporary password). */
  create(req: CreateCompanyUserRequest): Observable<CreateCompanyUserResponse> {
    return this.http.post<CreateCompanyUserResponse>(CompanyUsersService.BASE, req);
  }

  /** `PATCH /api/v1/company-users/{id}`. */
  update(id: string, req: UpdateCompanyUserRequest): Observable<CompanyUserDetail> {
    return this.http.patch<CompanyUserDetail>(`${CompanyUsersService.BASE}/${id}`, req);
  }

  /** `POST /api/v1/company-users/{id}/disable`. Returns void
   * (the controller responds with 204 No Content). */
  disable(id: string): Observable<void> {
    return this.http.post<void>(`${CompanyUsersService.BASE}/${id}/disable`, null);
  }

  /** `POST /api/v1/company-users/{id}/reactivate`. */
  reactivate(id: string): Observable<CompanyUserDetail> {
    return this.http.post<CompanyUserDetail>(`${CompanyUsersService.BASE}/${id}/reactivate`, null);
  }

  /** `POST /api/v1/company-users/{id}/reset-password`. Returns
   * the envelope with the new temporary password. */
  resetPassword(id: string): Observable<ResetPasswordResponse> {
    return this.http.post<ResetPasswordResponse>(
      `${CompanyUsersService.BASE}/${id}/reset-password`,
      null,
    );
  }
}
