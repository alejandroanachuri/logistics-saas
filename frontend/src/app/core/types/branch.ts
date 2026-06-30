/**
 * Wire shape for the branch catalog (etapa-3-envios).
 * Mirrors {@code BranchDto} on the backend side (PR-3
 * controller + PR-4/5 service). Branches are read-only
 * reference data in v1 — the catalog endpoint returns the
 * tenant's active branches ordered by code so the shipment
 * form's branch dropdown can be populated without
 * hard-coding.
 *
 * <p>Mutation (create / update / disable) is Etapa-4 scope.
 * The catalog is intentionally small (a handful of branches
 * per tenant) so the response is returned as a flat array,
 * not a {@code PageResponse}.
 */
export interface Branch {
  id: string;
  code: string;
  name: string;
  /** Foreign key to the tenant's address record (the physical
   * location of the branch). Null for branches registered
   * before the address-attached refactor in PR-3b. */
  addressId: string | null;
  isActive: boolean;
}
