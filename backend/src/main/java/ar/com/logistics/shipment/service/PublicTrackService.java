package ar.com.logistics.shipment.service;

import ar.com.logistics.common.exception.BusinessRuleException;
import ar.com.logistics.shipment.domain.Customer;
import ar.com.logistics.shipment.domain.Package;
import ar.com.logistics.shipment.domain.Shipment;
import ar.com.logistics.shipment.domain.TrackingEvent;
import ar.com.logistics.shipment.fsm.PackageFsm;
import ar.com.logistics.shipment.repository.system.CustomerAdminRepository;
import ar.com.logistics.shipment.repository.system.PackageAdminRepository;
import ar.com.logistics.shipment.repository.system.ShipmentAdminRepository;
import ar.com.logistics.shipment.repository.system.TrackingEventAdminRepository;
import ar.com.logistics.shipment.tracking.ShipmentStatusCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public tracking portal service (etapa-3-envios, PR-4 Chunk C).
 *
 * <p>Single public method: {@link #get(String) get(trackingId)} —
 * looks up a shipment by its global-unique {@code tracking_id} and
 * assembles a public-safe {@link PublicTrackResponse}. This service
 * is the ONLY place in the application that reads across tenants
 * (BYPASSRLS) without a tenant context: the
 * {@code /api/v1/public/**} path is the only authenticated-free
 * route, and the tracking id is globally unique so there is no
 * ambiguity to resolve.
 *
 * <p><b>Why system-side repositories?</b> The
 * {@code ar.com.logistics.shipment.repository.company.*} repositories
 * are RLS-scoped via V16. The public endpoint has no
 * {@code TenantContext} set, so those repositories would DENY the
 * query. The system-side mirrors
 * ({@code ar.com.logistics.shipment.repository.system.*}) bind to
 * {@code systemDataSource} (BYPASSRLS) and cross tenants freely.
 *
 * <p><b>Security contract (PRD §9.3):</b> the response carries ONLY
 * public fields. Internal references — tenantId, customerId,
 * packageId, branchId, paymentType, dni, cuit — MUST NEVER cross
 * this boundary. The
 * {@code PublicTrackServiceTest.responseDoesNotLeakInternalFields}
 * test pins this by asserting the JSON wire shape contains none of
 * those keys.
 *
 * <p><b>Status message mapping (PRD §9.4):</b> the internal FSM
 * states ({@link PackageFsm}) are translated to customer-facing
 * Spanish messages by {@link #mapToCustomerMessage(String)}.
 *
 * <p><b>Timeline visibility (PRD §9.5):</b> only a curated subset of
 * {@code event_type} values is shown to the public. The
 * {@link #VISIBLE_EVENT_TYPES} set is the whitelist; anything else
 * is dropped on the floor before serialization.
 */
@Service
public class PublicTrackService {

    /** Canonical wire-format error code when the tracking id is unknown. */
    private static final String CODE_TRACKING_NOT_FOUND = "TRACKING_NOT_FOUND";

    /**
     * Whitelist of {@code event_type} values shown to the public
     * (PRD §9.5). Anything outside this set is dropped from the
     * timeline before serialization. Internal operational events
     * (e.g. {@code shipment_validated}, {@code compensating_event})
     * are filtered out — the public client never sees them.
     */
    private static final Set<String> VISIBLE_EVENT_TYPES = Set.of(
            "package_created",
            "shipment_rejected",
            // Placeholders for etapa 5+ — the repository will return
            // empty for v1 since no events of these types exist yet.
            "package_received_origin",
            "package_in_transit",
            "package_arrived_destination",
            "package_out_for_delivery",
            "package_delivered",
            "package_delivery_failed");

    private final ShipmentAdminRepository shipmentAdminRepository;
    private final PackageAdminRepository packageAdminRepository;
    private final CustomerAdminRepository customerAdminRepository;
    private final TrackingEventAdminRepository trackingEventAdminRepository;

    public PublicTrackService(
            ShipmentAdminRepository shipmentAdminRepository,
            PackageAdminRepository packageAdminRepository,
            CustomerAdminRepository customerAdminRepository,
            TrackingEventAdminRepository trackingEventAdminRepository) {
        this.shipmentAdminRepository = shipmentAdminRepository;
        this.packageAdminRepository = packageAdminRepository;
        this.customerAdminRepository = customerAdminRepository;
        this.trackingEventAdminRepository = trackingEventAdminRepository;
    }

    // ===================================================================
    //  Public response DTO (PRD §9.3)
    // ===================================================================

    /**
     * Public tracking response. The accessor surface of this record
     * IS the contract: Jackson can only serialize fields that exist
     * here, so adding a field to this record is the way to expose a
     * new public-safe attribute. Internal references (tenantId,
     * customerId, packageId, branchId, paymentType, dni, cuit, etc.)
     * are deliberately omitted — they MUST NEVER cross the
     * public-track boundary.
     *
     * @param trackingId    the {@code LGST-XXXXXXXX} code (echoed back)
     * @param status        the raw aggregate status (e.g. {@code CREADO},
     *                      {@code ENTREGADO_PARCIAL})
     * @param statusMessage customer-facing Spanish message per PRD §9.4
     * @param isPartial     true iff {@code status == ENTREGADO_PARCIAL}
     * @param packageCount  number of packages in the shipment
     * @param totalWeightKg sum of {@code packages.weight_kg}
     * @param receiverName  formatted as {@code "firstName X."} per
     *                      PRD §9.3
     * @param timeline      filtered visible events per PRD §9.5
     */
    public record PublicTrackResponse(
            String trackingId,
            String status,
            String statusMessage,
            boolean isPartial,
            int packageCount,
            BigDecimal totalWeightKg,
            String receiverName,
            List<TimelineEntry> timeline) {

        /**
         * One timeline entry — a {@code (timestamp, message)} pair.
         * The {@code message} is the customer-facing Spanish message
         * for the shipment's current status (NOT the per-event
         * status, per PRD §9.5 — the public timeline surfaces the
         * shipment's headline message at each event time).
         */
        public record TimelineEntry(Instant timestamp, String message) {}
    }

    // ===================================================================
    //  Public API
    // ===================================================================

    /**
     * Resolve a public tracking id to a {@link PublicTrackResponse}.
     *
     * <p>The lookup is cross-tenant (BYPASSRLS); the response carries
     * ONLY public-safe fields per PRD §9.3.
     *
     * @param trackingId the {@code LGST-XXXXXXXX} code from the URL
     * @return the public tracking response
     * @throws BusinessRuleException {@code TRACKING_NOT_FOUND} when no
     *         shipment matches the supplied tracking id
     */
    @Transactional("systemTransactionManager")
    public PublicTrackResponse get(String trackingId) {
        Shipment shipment = shipmentAdminRepository
                .findByTrackingId(trackingId)
                .orElseThrow(() -> new BusinessRuleException(
                        CODE_TRACKING_NOT_FOUND, Map.of("trackingId", String.valueOf(trackingId))));

        List<Package> packages = packageAdminRepository.findByShipmentId(shipment.getId());

        // Pick the first package as the timeline anchor. The visible
        // timeline is built from this package's events. In practice
        // every shipment has at least one package (the creation flow
        // requires it), but we defend against the empty case to keep
        // the response well-formed.
        UUID timelineAnchor = packages.isEmpty() ? null : packages.get(0).getId();

        // Receiver name — fallback to firstName only when lastName is null.
        String receiverName = customerAdminRepository
                .findById(shipment.getReceiverId())
                .map(PublicTrackService::formatReceiverName)
                .orElse("");

        BigDecimal totalWeight = packages.stream()
                .map(Package::getWeightKg)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean isPartial = ShipmentStatusCalculator.STATUS_ENTREGADO_PARCIAL.equals(shipment.getStatus());

        List<PublicTrackResponse.TimelineEntry> timeline = loadTimeline(timelineAnchor, shipment.getStatus());

        return new PublicTrackResponse(
                shipment.getTrackingId(),
                shipment.getStatus(),
                mapToCustomerMessage(shipment.getStatus()),
                isPartial,
                packages.size(),
                totalWeight,
                receiverName,
                timeline);
    }

    // ===================================================================
    //  Private helpers
    // ===================================================================

    /**
     * Map the internal FSM state (or the
     * {@code ENTREGADO_PARCIAL} aggregate) to a customer-facing
     * Spanish message per PRD §9.4.
     */
    static String mapToCustomerMessage(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case PackageFsm.PRE_ALTA, PackageFsm.CREADO -> "Envío registrado";
            case PackageFsm.RECIBIDO_EN_SUCURSAL_ORIGEN, PackageFsm.CLASIFICADO -> "En preparación";
            case PackageFsm.EN_TRANSITO_A_HUB,
                    PackageFsm.EN_TRANSITO_CON_ALIADO,
                    PackageFsm.EN_TRANSITO_A_DESTINO,
                    PackageFsm.EN_HUB -> "En camino";
            case PackageFsm.RECIBIDO_EN_SUCURSAL_DESTINO -> "En sucursal de destino";
            case PackageFsm.EN_REPARTO -> "En reparto";
            case PackageFsm.ENTREGADO -> "Entregado";
            case ShipmentStatusCalculator.STATUS_ENTREGADO_PARCIAL -> "Entregado parcialmente";
            case PackageFsm.ENTREGA_FALLIDA -> "Intento de entrega no exitoso";
            case PackageFsm.DEVUELTO -> "Devuelto al remitente";
            case PackageFsm.RETENIDO, PackageFsm.RETENIDO_DOCUMENTACION -> "Retenido — en gestión";
            case PackageFsm.CANCELADO -> "Cancelado";
            case PackageFsm.INCIDENTE_ACTIVO -> "Con demora — en gestión";
            case PackageFsm.DEVOLUCION_INICIADA -> "En proceso de devolución";
            default -> "Estado no disponible";
        };
    }

    /**
     * Load the visible timeline for the package anchor, filtered by
     * the {@link #VISIBLE_EVENT_TYPES} whitelist. When the shipment
     * has no packages, the timeline is empty. The
     * {@code shipmentStatus} hint lets us promote
     * {@code shipment_rejected} events to the timeline only when the
     * shipment is {@code CANCELADO} (per PRD §9.5).
     */
    private List<PublicTrackResponse.TimelineEntry> loadTimeline(UUID packageId, String shipmentStatus) {
        if (packageId == null) {
            return List.of();
        }
        List<TrackingEvent> events = trackingEventAdminRepository.findByPackageIdOrderByEventTimestampAsc(packageId);
        List<PublicTrackResponse.TimelineEntry> out = new ArrayList<>();
        for (TrackingEvent e : events) {
            String type = e.getEventType();
            if (type == null || !VISIBLE_EVENT_TYPES.contains(type)) {
                continue;
            }
            // shipment_rejected is in the whitelist but should only
            // appear when the shipment is actually CANCELADO —
            // otherwise reject on PRE_ALTA would show a confusing
            // "Cancelado" entry before any cancellation event.
            if ("shipment_rejected".equals(type) && !PackageFsm.CANCELADO.equals(shipmentStatus)) {
                continue;
            }
            out.add(new PublicTrackResponse.TimelineEntry(e.getEventTimestamp(), mapToCustomerMessage(shipmentStatus)));
        }
        return out;
    }

    /**
     * Format the receiver name as {@code "firstName X."} where
     * {@code X} is the initial of the last name. Falls back to
     * {@code firstName} alone when the customer has no last name
     * (some FISICA records are single-name in practice).
     */
    private static String formatReceiverName(Customer c) {
        String first = c.getFirstName() == null ? "" : c.getFirstName().trim();
        String last = c.getLastName() == null ? "" : c.getLastName().trim();
        if (last.isEmpty()) {
            return first;
        }
        return first + " " + Character.toUpperCase(last.charAt(0)) + ".";
    }
}
