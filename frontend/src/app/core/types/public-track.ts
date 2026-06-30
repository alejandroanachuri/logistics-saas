/**
 * Wire shape for the public tracking portal at
 * {@code GET /api/v1/public/track/{lgstId}}.
 *
 * <p>This endpoint is OPEN (no auth, no cookies) and is the
 * shape the public-facing "track my package" page consumes
 * in etapa-3-envios. The contract deliberately omits
 * PII (sender/receiver IDs, exact addresses, declared values)
 * — only what the recipient themselves would need to confirm
 * their package is on its way.
 *
 * <p>Conventions:
 * - {@code status} is the human-readable shipment-level status
 *   code (NOT the internal {@code ShipmentStatus} enum —
 *   callers render {@code statusMessage} directly).
 * - {@code isPartial} is true when the package set is split
 *   (some delivered, some still in transit) — the UI shows a
 *   "entrega parcial" badge.
 * - {@code timeline} is a denormalised, public-safe view of
 *   the {@link TrackingEvent} history: only timestamp +
 *   human-readable message, no branch IDs or operator IDs.
 */
export interface PublicTrackResponse {
  /** Public tracking code, e.g. {@code LGST-A3K9P2RX}. */
  trackingId: string;
  /** Shipment-level status code (server-defined string set;
   * the public endpoint maps the internal {@code ShipmentStatus}
   * enum to a smaller public-safe vocabulary). */
  status: string;
  /** Human-readable Spanish description of the status. */
  statusMessage: string;
  /** True when the package set is split (some delivered, some
   * in transit) — UI shows a "entrega parcial" badge. */
  isPartial: boolean;
  packageCount: number;
  totalWeightKg: number | null;
  /** First name + last initial, e.g. {@code "Juan P."} —
   * public-safe projection of the receiver. */
  receiverName: string;
  /** Public-safe timeline: timestamp + human-readable message.
   * No branch IDs, no operator IDs, no internal metadata. */
  timeline: Array<{
    timestamp: string;
    message: string;
  }>;
}