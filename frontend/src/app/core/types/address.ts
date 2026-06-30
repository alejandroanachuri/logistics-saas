/**
 * Wire shape for the address domain (etapa-3-envios).
 * Mirrors {@code AddressDto}, {@code CreateAddressRequest},
 * {@code UpdateAddressRequest} on the backend side
 * (PR-3 controllers + PR-4/5 services).
 *
 * <p>Addresses are inert reference data: they belong to a
 * tenant, can be attached to a customer as a default, and
 * serve as the delivery address for a shipment. The
 * {@code /api/v1/addresses/**} endpoints expose the full CRUD
 * for any authenticated company user; disable / soft-delete is
 * introduced in PR-3b together with the customer / address
 * cascade (not in this chunk).
 *
 * <p>All wire fields are nullable to handle the Argentina
 * reality where floor / apartment / reference are sometimes
 * blank. The minimum required set on create is
 * {@code street}, {@code number}, {@code city},
 * {@code province}, {@code postalCode}, {@code country} —
 * backend validation rejects requests missing any of these
 * with a 400 VALIDATION_ERROR.
 */
export interface Address {
  id: string;
  street: string | null;
  number: string | null;
  floor: string | null;
  apartment: string | null;
  city: string | null;
  province: string | null;
  postalCode: string | null;
  reference: string | null;
  country: string | null;
}

/** Body for `POST /api/v1/addresses`. Backend enforces the
 * non-null required subset on validation. */
export interface CreateAddressRequest {
  street: string;
  number: string;
  floor?: string;
  apartment?: string;
  city: string;
  province: string;
  postalCode: string;
  reference?: string;
  country: string;
}

/** Body for `PATCH /api/v1/addresses/{id}` — all fields
 * optional; the backend applies a per-field allowlist. */
export interface UpdateAddressRequest {
  street?: string;
  number?: string;
  floor?: string;
  apartment?: string;
  city?: string;
  province?: string;
  postalCode?: string;
  reference?: string;
  country?: string;
}
