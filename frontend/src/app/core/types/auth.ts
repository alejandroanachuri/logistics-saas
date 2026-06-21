/**
 * Wire shape for the user / tenant projection served by the
 * backend. Mirrors {@code LoginResponse.User},
 * {@code RegisterResponse}, and {@code MeResponse.User} on the
 * backend side (PR3c + PR4a + PR5b).
 *
 * <p>Since {@code etapa-2-usuarios} (PR-3 backend, PR-4
 * frontend) the backend returns {@code roles: string[]} instead
 * of a single {@code role: string}. The frontend keeps a
 * backwards-compat {@code role} field (derived as
 * {@code roles[0] ?? ''}) so single-role readers (the
 * dashboard info card, the auth-shell greeting) keep working
 * without an immediate refactor. New code SHOULD read
 * {@code roles} instead of {@code role}.
 */
export interface AuthUser {
  id: string;
  tenantId: string;
  tenantSlug: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  /**
   * @deprecated Since etapa-2-usuarios. Read `roles` instead.
   * Kept for backwards-compat — populated by AuthService as
   * `roles[0] ?? ''`. Single-role consumers can keep reading
   * this; multi-role consumers MUST read `roles`.
   */
  role: string;
  /** Backend roles granted to this user (always present, even
   * if a single-element array for single-role users). */
  roles: string[];
  scope: 'COMPANY' | 'PLATFORM';
  emailVerified: boolean;
}

/**
 * Wire shape for the tenant projection served by the backend
 * (subset of {@code RegisterResponse} + the {@code /me}
 * claim). The auth store keeps the full {@code AuthUser}
 * which already carries the tenant id + slug; this is a
 * separate type so the tenant store is a thin projection the
 * dashboard reads.
 */
export interface Tenant {
  id: string;
  slug: string;
  legalName?: string;
}

export interface LoginRequest {
  slug: string;
  username: string;
  password: string;
}

export interface LoginResponse {
  user: AuthUser;
  expiresIn: number;
}

export interface RegisterRequest {
  company: {
    legalName: string;
    commercialName?: string;
    cuit: string;
    taxType: 'RESPONSABLE_INSCRIPTO' | 'MONOTRIBUTO' | 'EXENTO';
    slug: string;
    contactEmail: string;
    contactPhone?: string;
    address: {
      country: string;
      province: string;
      city: string;
      line: string;
      number: string;
      floor?: string;
      apartment?: string;
      postalCode: string;
    };
  };
  admin: {
    firstName: string;
    lastName: string;
    username: string;
    email: string;
    password: string;
    passwordConfirmation: string;
  };
  acceptsTerms: boolean;
  acceptsPrivacy: boolean;
}

export interface RegisterResponse {
  tenantId: string;
  slug: string;
  adminUserId: string;
  adminUsername: string;
}

export interface MeResponse {
  user: {
    id: string;
    tenantId: string;
    tenantSlug: string;
    username: string;
    roles: string[];
    scope: 'COMPANY' | 'PLATFORM';
    expiresIn: number;
  };
}

/* ===========================================================================
 * etapa-2-usuarios — company-users (team management)
 * ===========================================================================
 *
 * Backend wire types for the /api/v1/company-users/* endpoints (PR-3) and
 * the /api/v1/roles?scope=COMPANY endpoint. Mirrors the Java records on the
 * backend side (RoleDto, CompanyUserSummaryDto, CompanyUserDetailDto,
 * CreateCompanyUserRequest, CreateCompanyUserResponse,
 * UpdateCompanyUserRequest, ResetPasswordResponse, PageResponse<T>).
 *
 * Convention:
 * - All wire shapes use ISO-8601 strings for timestamps (never Date objects
 *   across the HTTP boundary — the interceptor / HttpClient does not run
 *   Date parsing on response bodies).
 * - `Role.description` may be null on the wire (the backend's
 *   RoleAssignmentService does not currently hydrate descriptions for the
 *   COMPANY scope; multi-select tolerates null gracefully).
 * - `isFirstAdmin` is a server-computed boolean (derived from
 *   `company_users.created_by IS NULL`).
 * - `temporaryPassword` appears ONLY in CreateCompanyUserResponse and
 *   ResetPasswordResponse. The frontend shows it ONCE in the reveal modal
 *   and never stores it in a signal that persists past dismiss.
 */

/** Catalog entry returned by `GET /api/v1/roles?scope=COMPANY`. */
export interface Role {
  id: string;
  /** One of `COMPANY_ADMIN`, `COMPANY_OPERATOR`, `COMPANY_DRIVER`,
   * `COMPANY_VIEWER`. The frontend maps each value to a token-based chip
   * color via `RoleChipComponent`. */
  name: string;
  description: string | null;
}

/** Summary row in `GET /api/v1/company-users` list response. */
export interface CompanyUserSummary {
  id: string;
  username: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  status: 'ACTIVE' | 'DISABLED';
  emailVerified: boolean;
  lastLoginAt: string | null;
  roles: Role[];
  createdAt: string;
  /** Server-derived: true when `company_users.created_by IS NULL`. The
   * frontend disables edit/disable buttons for these users and surfaces
   * the "primer administrador" tooltip. */
  isFirstAdmin: boolean;
}

/** Detail view in `GET /api/v1/company-users/{id}`. Extends summary with
 * the security columns the detail page needs. */
export interface CompanyUserDetail extends CompanyUserSummary {
  failedLoginAttempts: number;
  lockedUntil: string | null;
  updatedAt: string | null;
}

/** Body for `POST /api/v1/company-users`. */
export interface CreateCompanyUserRequest {
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  password: string;
  /** Backend requires at least one role. */
  roleIds: string[];
}

/** Body for `PATCH /api/v1/company-users/{id}`. All fields are optional;
 * the backend applies a per-field allowlist. `username` is NOT updateable. */
export interface UpdateCompanyUserRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  roleIds?: string[];
}

/** Body for `POST /api/v1/company-users/{id}/reset-password` (response).
 * `temporaryPassword` is shown ONCE in the reveal modal — never logged. */
export interface ResetPasswordResponse {
  userId: string;
  username: string;
  temporaryPassword: string;
  passwordWarning: string;
}

/** Body for `POST /api/v1/company-users` (response). Mirrors reset-password
 * envelope — the create flow generates a temporary password and shows it
 * once via the reveal modal. */
export interface CreateCompanyUserResponse {
  user: CompanyUserDetail;
  temporaryPassword: string;
  passwordWarning: string;
}

/** Generic paged envelope used by all list endpoints. */
export interface PageResponse<T> {
  data: T[];
  total: number;
  page: number;
  size: number;
}

/** Query params for `GET /api/v1/company-users`. All optional. */
export interface ListCompanyUsersParams {
  page?: number;
  size?: number;
  /** Comma-separated, e.g. `createdAt,desc`. Backend ignores unknown values. */
  sort?: string;
  status?: 'ACTIVE' | 'DISABLED';
  /** Filter by role membership (any of the user's roles matches this id). */
  roleId?: string;
  /** Free-text search across username + email + firstName + lastName. */
  search?: string;
}
