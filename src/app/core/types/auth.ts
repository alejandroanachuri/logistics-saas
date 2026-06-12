/**
 * Wire shape for the user / tenant projection served by the
 * backend. Mirrors {@code LoginResponse.User},
 * {@code RegisterResponse}, and {@code MeResponse.User} on the
 * backend side (PR3c + PR4a + PR5b).
 */
export interface AuthUser {
  id: string;
  tenantId: string;
  tenantSlug: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
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
    role: string;
    scope: 'COMPANY' | 'PLATFORM';
    expiresIn: number;
  };
}
