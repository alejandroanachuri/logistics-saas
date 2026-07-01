/**
 * Wire shape for the customer domain (etapa-3-envios).
 * Mirrors {@code CustomerDto}, {@code CustomerSummaryDto},
 * {@code CreateCustomerRequest}, and {@code UpdateCustomerRequest}
 * on the backend side (PR-3 controllers + PR-4/5 services).
 *
 * <p>Customers are first-class company entities. The customer
 * record is shared between the team-management module (who can
 * view which customers) and the shipment module (sender +
 * receiver are both customers). The {@code personType}
 * discriminator selects between the {@code FISICA} and
 * {@code JURIDICA} branches and drives which fields are
 * required at the API boundary.
 *
 * <p>Conventions:
 * - {@code firstName} / {@code lastName} are populated for
 *   {@code FISICA}; {@code razonSocial} is populated for
 *   {@code JURIDICA}. The complementary fields are null.
 * - {@code dni} applies to {@code FISICA}; {@code cuitCuil}
 *   applies to {@code JURIDICA} (and is required for
 *   {@code RESPONSABLE_INSCRIPTO} / {@code MONOTRIBUTISTA}
 *   tax conditions).
 * - {@code dataConsent} reflects Argentina's Ley 25.326 PDP
 *   consent capture — the backend refuses to persist a
 *   customer record without it set to true.
 * - All wire timestamps use ISO-8601 strings.
 */
export type CustomerPersonType = 'FISICA' | 'JURIDICA';

export type CustomerTaxCondition =
  | 'RESPONSABLE_INSCRIPTO'
  | 'MONOTRIBUTISTA'
  | 'EXENTO'
  | 'CONSUMIDOR_FINAL'
  | 'NO_CATEGORIZADO';

/** Single row in `GET /api/v1/customers` list response. Minimal
 * projection — first/last-name + email + status + DNI/CUIT
 * (already masked per JsonView rules). Used by the customer
 * list page and the customer picker in the shipment form. */
export interface CustomerSummary {
  id: string;
  personType: CustomerPersonType;
  firstName: string | null;
  lastName: string | null;
  razonSocial: string | null;
  email: string | null;
  phone: string;
  dni: string | null;
  cuitCuil: string | null;
  taxCondition: CustomerTaxCondition;
  status: 'ACTIVE' | 'DISABLED';
  createdAt: string;
}

/** Detail view in `GET /api/v1/customers/{id}`. Extends
 * summary with the consent trail, default address pointer,
 * and updatedAt timestamp. */
export interface Customer {
  id: string;
  personType: CustomerPersonType;
  firstName: string | null;
  lastName: string | null;
  razonSocial: string | null;
  dni: string | null;
  cuitCuil: string | null;
  taxCondition: CustomerTaxCondition;
  phone: string;
  email: string | null;
  defaultAddressId: string | null;
  dataConsent: boolean;
  consentDate: string | null;
  createdAt: string;
  updatedAt: string | null;
}

/** Body for `POST /api/v1/customers`. The backend validates the
 * personType-specific field set (DNI for FISICA, CUIT for
 * JURIDICA) and rejects with `DNI_INVALID` / `CUIT_INVALID` /
 * `DNI_ALREADY_EXISTS` / `CUIT_ALREADY_EXISTS` as appropriate. */
export interface CreateCustomerRequest {
  personType: CustomerPersonType;
  firstName?: string;
  lastName?: string;
  razonSocial?: string;
  dni?: string;
  cuitCuil?: string;
  taxCondition: CustomerTaxCondition;
  phone: string;
  email?: string;
  dataConsent: boolean;
}

/** Body for `PATCH /api/v1/customers/{id}`. All fields are
 * optional; the backend applies a per-field allowlist.
 * personType is NOT updateable (changing it would invalidate
 * the DNI/CUIT pair). */
export interface UpdateCustomerRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  dni?: string;
  cuitCuil?: string;
  dataConsent?: boolean;
}

/** Query params for `GET /api/v1/customers`. All optional.
 * `search` is free-text across firstName, lastName, razonSocial,
 * email, DNI, and CUIT. `status` defaults to ACTIVE server-side
 * — callers pass `ALL` to include disabled records. */
export interface CustomerListFilters {
  search?: string;
  status?: 'ACTIVE' | 'DISABLED' | 'ALL';
}
