/**
 * Field-level security helpers for DNI and CUIT masking on the
 * client side (etapa-3-envios PR-5).
 *
 * <p>The backend's {@code JsonView} rules already mask these
 * fields per-role on most list responses, but the frontend
 * applies a defensive belt-and-braces mask for role-elevated
 * views (driver / viewer) where PII should never render in
 * the clear. The two helpers are pure functions so they are
 * trivially testable and safe to call from templates +
 * computed signals without a service dependency.
 *
 * <p>Masking format:
 * <ul>
 *   <li>{@code maskDni}: keeps the first 2 + last 2 digits,
 *       replaces the middle with {@code ***}.
 *       {@code "12345678"} → {@code "12***78"}</li>
 *   <li>{@code maskCuit}: keeps the prefix-2 + dash +
 *       last-2 + dash + check-digit, replaces the document
 *       middle with {@code ***}.
 *       {@code "20-12345678-9"} → {@code "20-***78-9"}</li>
 * </ul>
 *
 * <p>Short inputs (fewer than 4 chars) are returned
 * unmodified — there is no meaningful mask for them. Null /
 * undefined inputs return {@code null} so the helpers compose
 * cleanly with optional-chained fields.
 */
export function maskDni(dni: string | null | undefined): string | null {
  if (!dni) return null;
  if (dni.length < 4) return dni;
  return `${dni.slice(0, 2)}***${dni.slice(-2)}`;
}

export function maskCuit(cuit: string | null | undefined): string | null {
  if (!cuit) return null;
  if (cuit.length < 4) return cuit;
  return `${cuit.slice(0, 2)}-***${cuit.slice(-2)}-${cuit.slice(-1)}`;
}

/**
 * Returns true when the supplied role set indicates the
 * current viewer is in a role-elevated position that should
 * not see full PII — currently {@code COMPANY_DRIVER} and
 * {@code COMPANY_VIEWER}. ADMIN and OPERATOR see full PII by
 * default (they own the relationship with the customer).
 *
 * <p>Undef-aware: a missing or empty role list returns false
 * (defensive default — no PII masking for an unauthenticated
 * or pre-/me state, since the layout will not render the
 * data anyway).
 */
export function shouldMaskSensitiveFields(roles: string[] | undefined): boolean {
  if (!roles) return false;
  return roles.includes('COMPANY_DRIVER') || roles.includes('COMPANY_VIEWER');
}
