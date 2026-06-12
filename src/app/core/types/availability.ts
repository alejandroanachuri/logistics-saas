/**
 * Wire shape for the slug / cuit / username availability
 * endpoints on the backend. Each returns the same envelope
 * shape with a per-endpoint `reason` code.
 */
export interface AvailabilityResponse {
  available: boolean;
  reason?: 'SLUG_ALREADY_TAKEN' | 'CUIT_ALREADY_REGISTERED' | 'USERNAME_ALREADY_TAKEN' | string;
}
