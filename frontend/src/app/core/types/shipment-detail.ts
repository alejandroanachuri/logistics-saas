/**
 * Wire shape for shipment summary + detail projections
 * (etapa-3-envios). The full {@code FullShipmentDetailDto}
 * backend record carries FK enrichments (sender / receiver /
 * delivery address / branches / service level refs) which the
 * detail page renders directly so the UI does not need to
 * fetch each reference individually.
 *
 * <p>This file extends the base types from {@code ./shipment}
 * with the projection shapes returned by the controller. The
 * base {@link Shipment} + {@link ShipmentPackage} are the
 * canonical wire types and remain unchanged; this file is the
 * frontend's view of the joined response.
 */

/** Summary row in `GET /api/v1/shipments` list response. The
 * controller enriches the bare shipment with the sender +
 * receiver display names so the list page can render without
 * a per-row customer lookup. */
export interface ShipmentSummary {
  id: string;
  trackingId: string;
  code: string | null;
  status: string;
  senderName: string | null;
  receiverName: string | null;
  totalWeightKg: number | null;
  createdAt: string;
}

/** Reference projection for an FK on the detail response. The
 * backend joins the related customer / address / branch /
 * service-level records so the detail page renders in a single
 * round-trip. */
export interface CustomerRef {
  id: string;
  name: string;
}

export interface AddressRef {
  id: string;
  /** Human-readable display label, e.g.
   * {@code "Av. Corrientes 1234, CABA"}. */
  displayLabel: string;
}

export interface BranchRef {
  id: string;
  code: string;
  name: string;
}

export interface ServiceLevelRef {
  id: string;
  code: string;
  name: string;
}

/** Full detail shape returned by `GET /api/v1/shipments/{id}`
 * and by the create / update / validate / reject / cancel
 * responses. Mirrors the backend's
 * {@code FullShipmentDetailDto}. */
export interface ShipmentDetail {
  id: string;
  trackingId: string;
  code: string | null;
  status: string;
  sender: CustomerRef | null;
  receiver: CustomerRef | null;
  deliveryAddress: AddressRef | null;
  originBranch: BranchRef | null;
  destinationBranch: BranchRef | null;
  serviceLevel: ServiceLevelRef | null;
  paymentType: string;
  deliveryMode: string;
  deliveryInstructions: string | null;
  packages: import('./shipment').ShipmentPackage[];
  totalWeightKg: number | null;
  totalCost: number | null;
  createdAt: string;
  updatedAt: string | null;
  /** Latest tracking event recorded against any package in
   * this shipment. Null for a freshly-created PRE_ALTA
   * shipment with no recorded events yet. */
  latestEvent: import('./shipment').TrackingEvent | null;
}

/** Query params for `GET /api/v1/shipments`. All optional. */
export interface ShipmentListFilters {
  status?: string;
  dateFrom?: string;
  dateTo?: string;
  search?: string;
  page?: number;
  size?: number;
  /** Comma-separated, e.g. {@code "createdAt,desc"}. */
  sort?: string;
}

/** Body for `POST /api/v1/shipments/{id}/reject`. The reason
 * is mandatory — backend rejects with 400 VALIDATION_ERROR
 * if blank. */
export interface RejectShipmentRequest {
  rejectionReason: string;
}

/** Body for `POST /api/v1/shipments/{id}/cancel`. Reason is
 * mandatory (ADMIN-only endpoint). */
export interface CancelShipmentRequest {
  reason: string;
}

/** Body for `PATCH /api/v1/shipments/{id}` — only the
 * delivery-level fields are patchable. Backend rejects
 * updates on shipments not in PRE_ALTA status. */
export interface UpdateShipmentRequest {
  deliveryInstructions?: string;
  paymentType?: import('./shipment').PaymentType;
  deliveryMode?: import('./shipment').DeliveryMode;
  promisedDeliveryDate?: string;
}
