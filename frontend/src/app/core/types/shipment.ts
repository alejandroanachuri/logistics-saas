/**
 * Wire shape for the shipment domain (etapa-3-envios).
 * Mirrors {@code ShipmentDto}, {@code ShipmentPackageDto},
 * {@code TrackingEventDto}, {@code CreateShipmentRequest}, and
 * {@code RecordEventRequest} on the backend side (PR-2/3
 * controllers + services).
 *
 * <p>The shipment domain has a dual-entity shape: a
 * {@link Shipment} is the parent (commercial / waybill unit),
 * and {@link ShipmentPackage} is each physical package under
 * it. A shipment may have 1..N packages; the FSM
 * (Finite State Machine) tracks each package's status
 * independently via {@link TrackingEvent} records.
 *
 * <p>Conventions:
 * - {@code trackingId} follows the {@code LGST-XXXXXXXX}
 *   pattern (8 uppercase alphanumerics); the backend generates
 *   it server-side and retries on collision (the LGST
 *   generator's seed space is large enough for collision to
 *   be effectively impossible at our scale).
 * - {@code status} is the {@link ShipmentStatus} enum. The
 *   status of each {@link ShipmentPackage} (also
 *   {@link ShipmentStatus}) advances independently via the
 *   FSM's {@code VALID_TRANSITIONS} map.
 * - {@code slaStatus} is computed server-side from the
 *   difference between {@code promisedDeliveryDate} and
 *   {@code now()}. The frontend does NOT compute it.
 * - All wire timestamps use ISO-8601 strings.
 */

/**
 * The full lifecycle of a shipment / package. The same enum
 * is used at both levels because a shipment's overall status
 * is the "most advanced" status across its packages (computed
 * server-side by {@code ShipmentStatusCalculator}). This
 * prevents subtle bugs where the UI has to reconcile
 * divergent enums.
 */
export type ShipmentStatus =
  | 'PRE_ALTA'
  | 'ESPERANDO_APROBACION_RUTA'
  | 'CREADO'
  | 'RECIBIDO_EN_SUCURSAL_ORIGEN'
  | 'RETENIDO_DOCUMENTACION'
  | 'CLASIFICADO'
  | 'EN_TRANSITO_A_HUB'
  | 'EN_HUB'
  | 'EN_TRANSITO_CON_ALIADO'
  | 'EN_TRANSITO_A_DESTINO'
  | 'RECIBIDO_EN_SUCURSAL_DESTINO'
  | 'EN_REPARTO'
  | 'ENTREGA_FALLIDA'
  | 'INCIDENTE_ACTIVO'
  | 'DEVOLUCION_INICIADA'
  | 'RETENIDO'
  | 'ENTREGADO'
  | 'DEVUELTO'
  | 'CANCELADO'
  | 'ENTREGADO_PARCIAL';

/**
 * The status of an individual {@link ShipmentPackage}. Same
 * enum as {@link ShipmentStatus} — we type-alias it so call
 * sites can signal intent (a package status vs the
 * shipment-level rollup).
 */
export type PackageStatus = ShipmentStatus;

export type ShipmentType = 'NORMAL' | 'RETURN';

export type PaymentType = 'PAGO_ORIGEN' | 'PAGO_DESTINO' | 'CUENTA_CORRIENTE';

export type DeliveryMode = 'DOMICILIO' | 'RETIRO_SUCURSAL';

export type SlaStatus = 'EN_PLAZO' | 'EN_RIESGO' | 'VENCIDO' | 'CUMPLIDO';

/**
 * Detail view in `GET /api/v1/shipments/{id}` and embedded
 * in {@link CreateShipmentResponse}. Commercial + waybill
 * record. Note that `totalWeightKg` and `totalCost` are
 * server-computed rollups of the packages underneath —
 * callers MUST NOT compute them client-side.
 */
export interface Shipment {
  id: string;
  /** Public-facing tracking code, e.g. {@code LGST-A3K9P2RX}. */
  trackingId: string;
  /** Optional customer-supplied reference (e.g. PO number).
   * Nullable because most shipments do not have one. */
  code: string | null;
  shipmentType: ShipmentType;
  senderId: string;
  receiverId: string;
  deliveryAddressId: string;
  originBranchId: string;
  destinationBranchId: string;
  serviceLevelId: string;
  paymentType: PaymentType;
  deliveryMode: DeliveryMode;
  deliveryInstructions: string | null;
  status: ShipmentStatus;
  promisedDeliveryDate: string | null;
  slaStatus: SlaStatus;
  totalWeightKg: number | null;
  totalCost: number | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string | null;
}

/**
 * A single physical package within a {@link Shipment}. The
 * package is the FSM-tracked unit — the shipment's status is
 * a rollup. {@code qrCode} is the human-scannable label; it
 * mirrors {@code trackingId} but is also printable on the
 * waybill.
 */
export interface ShipmentPackage {
  id: string;
  shipmentId: string;
  qrCode: string;
  /** Previous status — used by the timeline UI to render the
   * "from → to" transition. Null for the initial creation
   * event. */
  previousStatus: PackageStatus | null;
  status: PackageStatus;
  weightKg: number;
  volumeCm3: number | null;
  /** Free-form dimensions string, e.g. {@code "30x20x15"}. */
  dimensionsCm: string | null;
  contentDescription: string;
  declaredValue: number | null;
  /** ISO-4217 currency code. Defaults server-side to
   * {@code "ARS"} for domestic shipments. */
  declaredCurrency: string;
  hasInsurance: boolean;
  isFragile: boolean;
  isUrgent: boolean;
  requiresSignature: boolean;
  requiresIdCheck: boolean;
  category:
    | 'GENERAL'
    | 'DOCUMENTOS'
    | 'ELECTRONICA'
    | 'ALIMENTOS'
    | 'MEDICAMENTOS'
    | 'PELIGROSO';
  receptionCondition: 'BUENO' | 'DAÑADO_EXTERNO' | 'ABIERTO';
  receptionNotes: string | null;
}

/**
 * Body for `POST /api/v1/shipments`. Always requires at
 * least one package. When {@code validateNow} is true, the
 * backend runs the FSM validate pass inline (rejecting
 * illegal transitions) and returns 422 with
 * {@code INVALID_STATE_TRANSITION} on failure. When false
 * (default), the shipment is created in {@code PRE_ALTA} and
 * the operator triggers validation explicitly.
 */
export interface CreateShipmentRequest {
  code?: string;
  senderId: string;
  receiverId: string;
  deliveryAddressId: string;
  originBranchId?: string;
  destinationBranchId?: string;
  serviceLevelId?: string;
  paymentType: PaymentType;
  deliveryMode?: DeliveryMode;
  deliveryInstructions?: string;
  promisedDeliveryDate?: string;
  validateNow?: boolean;
  packages: Array<{
    weightKg: number;
    volumeCm3?: number;
    dimensionsCm?: string;
    contentDescription: string;
    declaredValue?: number;
    declaredCurrency?: string;
    category?:
      | 'GENERAL'
      | 'DOCUMENTOS'
      | 'ELECTRONICA'
      | 'ALIMENTOS'
      | 'MEDICAMENTOS'
      | 'PELIGROSO';
    isFragile?: boolean;
    isUrgent?: boolean;
    requiresSignature?: boolean;
    requiresIdCheck?: boolean;
  }>;
}

/** Response from `POST /api/v1/shipments`. Returns the
 * freshly-minted shipment + its packages so the UI can route
 * directly to the detail page without a refetch. */
export interface CreateShipmentResponse {
  shipment: Shipment;
  packages: ShipmentPackage[];
}

/**
 * A single tracking event in a package's history. The
 * {@code eventSource} discriminator tells the UI where the
 * event came from (operator at a branch, system automation,
 * a partner / aliado, or the customer via the public portal).
 * Events are append-only — the FSM derives current status
 * from the latest event in the timeline.
 */
export interface TrackingEvent {
  id: string;
  packageId: string;
  eventType: string;
  eventTimestamp: string;
  branchId: string | null;
  userId: string;
  eventSource: 'OPERADOR_SUCURSAL' | 'SISTEMA' | 'ALIADO' | 'CLIENTE';
  metadata: Record<string, unknown> | null;
  createdAt: string;
}

/** Body for `POST /api/v1/shipments/{id}/events` (the
 * operator "record event" flow). The backend computes
 * {@code eventHash} server-side for idempotency; clients
 * MUST NOT supply it. */
export interface RecordEventRequest {
  eventType: string;
  eventTimestamp?: string;
  branchId?: string;
  metadata?: Record<string, unknown>;
}