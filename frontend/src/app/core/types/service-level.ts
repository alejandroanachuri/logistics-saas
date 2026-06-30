/**
 * Wire shape for the service-level catalog (etapa-3-envios).
 * Mirrors {@code ServiceLevelDto} on the backend side (PR-3
 * controller + PR-4/5 service). Service levels are read-only
 * reference data in v1 — the catalog endpoint returns the
 * tenant's active service levels ordered by code so the
 * shipment form's service-level dropdown can be populated
 * without hard-coding (e.g. {@code "STD"}, {@code "EXP"},
 * {@code "OVN"}).
 *
 * <p>Mutation (create / update / disable) is Etapa-4 scope.
 * The catalog is intentionally small (typically 3-5 service
 * levels per tenant) so the response is returned as a flat
 * array, not a {@code PageResponse}.
 */
export interface ServiceLevel {
  id: string;
  code: string;
  name: string;
  isActive: boolean;
}
