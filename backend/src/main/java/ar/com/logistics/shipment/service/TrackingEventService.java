package ar.com.logistics.shipment.service;

import ar.com.logistics.auth.repository.company.CompanyUserRoleRepository;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.common.audit.AuditEvent;
import ar.com.logistics.common.audit.AuditLogger;
import ar.com.logistics.common.exception.BusinessRuleException;
import ar.com.logistics.shipment.domain.Package;
import ar.com.logistics.shipment.domain.Shipment;
import ar.com.logistics.shipment.domain.TrackingEvent;
import ar.com.logistics.shipment.fsm.PackageFsm;
import ar.com.logistics.shipment.repository.company.PackageRepository;
import ar.com.logistics.shipment.repository.company.ShipmentRepository;
import ar.com.logistics.shipment.repository.company.TrackingEventRepository;
import ar.com.logistics.shipment.tracking.EventHashCalculator;
import ar.com.logistics.shipment.tracking.ShipmentStatusCalculator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central engine that drives the package FSM (etapa-3-envios, PR-3b Chunk B).
 *
 * <p>Every call to {@link #record(UUID, UUID, UUID, RecordEventRequest)}
 * runs the same eight-step pipeline:
 *
 * <ol>
 *   <li>Look up the event contract for {@code req.eventType()} (mandatory
 *       fields, allowed roles, target transition). The contract is private
 *       static data (see {@link EventContract}). Unknown event types throw
 *       {@code EVENT_VALIDATION_ERROR} (no silent fall-through).</li>
 *   <li>Validate every mandatory field is present in {@code req.payload()}
 *       — missing or null fields raise {@code EVENT_VALIDATION_ERROR}.</li>
 *   <li>Load the user's roles via
 *       {@link CompanyUserRoleRepository#findRoleIdsByUserId(UUID)} +
 *       {@link RoleRepository#findById(java.util.UUID)} and check the
 *       intersection with the contract's {@code allowedRoles}. No overlap
 *       raises {@code INSUFFICIENT_PERMISSIONS}.</li>
 *   <li>Compute {@code event_hash} via
 *       {@link EventHashCalculator#computeHash(UUID, String, Map, Instant)};
 *       check {@link TrackingEventRepository#existsByEventHash(String)} for
 *       idempotency. Existing hash raises {@code DUPLICATE_EVENT}.</li>
 *   <li>Resolve the target row(s):
 *       <ul>
 *         <li>If {@code packageId} matches a package, the event is
 *             package-level — apply the transition to that one package.</li>
 *         <li>If {@code packageId} does NOT match a package but matches a
 *             shipment, the event is shipment-level (e.g.
 *             {@code shipment_validated}) — fan the transition out to
 *             every package in the shipment.</li>
 *         <li>Otherwise raise {@code PACKAGE_NOT_FOUND}.</li>
 *       </ul>
 *       Per-package lookups go through
 *       {@link PackageRepository#findByIdAndTenantIdForUpdate(UUID, UUID)}
 *       to acquire {@code SELECT ... FOR UPDATE} on each row, so
 *       concurrent {@code record()} calls for the same package serialize
 *       at the DB level. The FSM transition check runs INSIDE this lock.</li>
 *   <li>Validate each {@code PackageFsm.isValidTransition(from, to)} — an
 *       invalid transition raises {@code INVALID_STATE_TRANSITION}.</li>
 *   <li>For each package transitioned into {@code RETENIDO}, capture the
 *       prior state on {@link Package#setPreviousStatus(String)} (per PRD
 *       §7.4 reversible-state semantics). All packages are saved with
 *       updated status (and {@code updated_at} bumped via
 *       {@code @PreUpdate}).</li>
 *   <li>Recompute the shipment aggregate status via
 *       {@link ShipmentStatusCalculator#calculate(List)} and persist.
 *       Emit ONE {@code TrackingEvent} per {@code record()} call (the
 *       per-package cascade only updates {@code packages.status}, not
 *       the event log — the event log is APPEND-ONLY and the anchor
 *       row is the shipment id for shipment-level events). Audit
 *       {@code TRACKING_EVENT_RECORDED}.</li>
 * </ol>
 *
 * <p><b>Event types in scope (etapa 3 — PR-3b):</b>
 * <ul>
 *   <li>{@code package_created} — initial-state marker, no transition.
 *       Emitted by {@code ShipmentService.create}. Mandatory fields: none.
 *       Allowed roles: COMPANY_ADMIN, COMPANY_OPERATOR.</li>
 *   <li>{@code shipment_validated} — fan-out: every package in the
 *       shipment {@code PRE_ALTA → CREADO}. Mandatory:
 *       {@code docsChecked}, {@code paymentConfirmed},
 *       {@code validatedBy}. Allowed roles: COMPANY_ADMIN,
 *       COMPANY_OPERATOR.</li>
 *   <li>{@code shipment_rejected} — fan-out: every package
 *       {@code PRE_ALTA → CANCELADO}. Mandatory: {@code reason},
 *       {@code rejectedBy}. Allowed roles: COMPANY_ADMIN,
 *       COMPANY_OPERATOR.</li>
 *   <li>{@code shipment_cancelled} — this package
 *       {@code current → CANCELADO} (must be non-final per FSM).
 *       Mandatory: {@code cancelledBy}, {@code reason}. Allowed
 *       roles: COMPANY_ADMIN.</li>
 *   <li>{@code package_cancelled} — this package
 *       {@code current → CANCELADO}. Mandatory: {@code cancelledBy},
 *       {@code reason}. Allowed roles: COMPANY_ADMIN,
 *       COMPANY_OPERATOR.</li>
 *   <li>{@code compensating_event} — no transition by default;
 *       optional {@code targetStatus} payload field drives an explicit
 *       FSM transition (used for audit roll-back). Mandatory:
 *       {@code previousEventHash}, {@code reason}. Allowed roles:
 *       COMPANY_ADMIN.</li>
 * </ul>
 *
 * <p>Future event types (etapa 5-7 — package_in_transit,
 * package_received_origin, package_arrived_destination,
 * package_out_for_delivery, package_delivered, package_delivery_failed)
 * plug into the same {@link EventContract} map without changing this
 * class's surface.
 *
 * <p><b>Transaction boundary:</b> every public method runs in a single
 * {@code @Transactional("companyTransactionManager")} block. The
 * pessimistic-write lock acquired by
 * {@code PackageRepository.findByIdAndTenantIdForUpdate} is held until
 * commit / rollback, so the FSM transition check, the
 * {@code packages} UPDATE, the {@code tracking_events} INSERT, the
 * {@code shipments} UPDATE, and the audit emission all complete
 * atomically — or none do.
 */
@Service
public class TrackingEventService {

    private static final Logger LOG = LoggerFactory.getLogger(TrackingEventService.class);

    // ------------------------------------------------------------------------
    //  Canonical wire-format error codes (GlobalExceptionHandler maps these
    //  to HTTP statuses).
    // ------------------------------------------------------------------------

    static final String CODE_PACKAGE_NOT_FOUND = "PACKAGE_NOT_FOUND";
    static final String CODE_DUPLICATE_EVENT = "DUPLICATE_EVENT";
    static final String CODE_INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";
    static final String CODE_EVENT_VALIDATION_ERROR = "EVENT_VALIDATION_ERROR";
    static final String CODE_INSUFFICIENT_PERMISSIONS = "INSUFFICIENT_PERMISSIONS";

    // ------------------------------------------------------------------------
    //  Event-type string constants.
    // ------------------------------------------------------------------------

    static final String EVENT_PACKAGE_CREATED = "package_created";
    static final String EVENT_SHIPMENT_VALIDATED = "shipment_validated";
    static final String EVENT_SHIPMENT_REJECTED = "shipment_rejected";
    static final String EVENT_SHIPMENT_CANCELLED = "shipment_cancelled";
    static final String EVENT_PACKAGE_CANCELLED = "package_cancelled";
    static final String EVENT_COMPENSATING = "compensating_event";

    // ------------------------------------------------------------------------
    //  Role names (read-only catalog, V7).
    // ------------------------------------------------------------------------

    private static final String ROLE_ADMIN = "COMPANY_ADMIN";
    private static final String ROLE_OPERATOR = "COMPANY_OPERATOR";

    /** Audit event type emitted on every successful {@code record()} call. */
    private static final String AUDIT_EVENT_RECORDED = "TRACKING_EVENT_RECORDED";

    /** Default event source for events emitted by this service. */
    private static final String EVENT_SOURCE_OPERADOR_SUCURSAL = "OPERADOR_SUCURSAL";

    /** Sentinel key inside {@code payload} that lets {@code compensating_event} request an explicit target state. */
    private static final String KEY_TARGET_STATUS = "targetStatus";

    // ------------------------------------------------------------------------
    //  Event contracts (PRD §8.2 + spec C / etapа-3-envios PR-3b).
    // ------------------------------------------------------------------------

    /**
     * Static contract for a tracking-event type. Read-only at runtime;
     * the service consults {@link #CONTRACTS} by event type before any
     * side effect.
     *
     * @param eventType the wire-format event_type
     * @param mandatoryFields every key listed here must be present and
     *        non-null in the request payload
     * @param allowedRoles the role names that may emit this event
     * @param perPackageTargetStatus the target package state for
     *        per-package events (null when the event is no-op, or when
     *        the target is supplied via payload — compensating_event)
     * @param cascadeShipmentLevel true iff the event is anchored on a
     *        shipment id and fans the transition out to every package
     *        in the shipment
     */
    private record EventContract(
            String eventType,
            List<String> mandatoryFields,
            List<String> allowedRoles,
            String perPackageTargetStatus,
            boolean cascadeShipmentLevel) {}

    /**
     * The event-type contract table. Built eagerly in {@code <clinit>}
     * for fast lookup. Adding a new event type is a one-line addition
     * here (no service-method changes needed).
     */
    private static final Map<String, EventContract> CONTRACTS = buildContracts();

    private static Map<String, EventContract> buildContracts() {
        Map<String, EventContract> m = new HashMap<>();
        m.put(
                EVENT_PACKAGE_CREATED,
                new EventContract(EVENT_PACKAGE_CREATED, List.of(), List.of(ROLE_ADMIN, ROLE_OPERATOR), null, false));
        m.put(
                EVENT_SHIPMENT_VALIDATED,
                new EventContract(
                        EVENT_SHIPMENT_VALIDATED,
                        List.of("docsChecked", "paymentConfirmed", "validatedBy"),
                        List.of(ROLE_ADMIN, ROLE_OPERATOR),
                        PackageFsm.CREADO,
                        true));
        m.put(
                EVENT_SHIPMENT_REJECTED,
                new EventContract(
                        EVENT_SHIPMENT_REJECTED,
                        List.of("reason", "rejectedBy"),
                        List.of(ROLE_ADMIN, ROLE_OPERATOR),
                        PackageFsm.CANCELADO,
                        true));
        m.put(
                EVENT_SHIPMENT_CANCELLED,
                new EventContract(
                        EVENT_SHIPMENT_CANCELLED,
                        List.of("cancelledBy", "reason"),
                        List.of(ROLE_ADMIN),
                        PackageFsm.CANCELADO,
                        false));
        m.put(
                EVENT_PACKAGE_CANCELLED,
                new EventContract(
                        EVENT_PACKAGE_CANCELLED,
                        List.of("cancelledBy", "reason"),
                        List.of(ROLE_ADMIN, ROLE_OPERATOR),
                        PackageFsm.CANCELADO,
                        false));
        m.put(
                EVENT_COMPENSATING,
                new EventContract(
                        EVENT_COMPENSATING, List.of("previousEventHash", "reason"), List.of(ROLE_ADMIN), null, false));
        return Map.copyOf(m);
    }

    // ------------------------------------------------------------------------
    //  Dependencies.
    // ------------------------------------------------------------------------

    private final TrackingEventRepository trackingEventRepository;
    private final PackageRepository packageRepository;
    private final ShipmentRepository shipmentRepository;
    private final CompanyUserRoleRepository companyUserRoleRepository;
    private final RoleRepository roleRepository;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    public TrackingEventService(
            TrackingEventRepository trackingEventRepository,
            PackageRepository packageRepository,
            ShipmentRepository shipmentRepository,
            CompanyUserRoleRepository companyUserRoleRepository,
            RoleRepository roleRepository,
            AuditLogger auditLogger) {
        this.trackingEventRepository = trackingEventRepository;
        this.packageRepository = packageRepository;
        this.shipmentRepository = shipmentRepository;
        this.companyUserRoleRepository = companyUserRoleRepository;
        this.roleRepository = roleRepository;
        this.auditLogger = auditLogger;
        this.objectMapper = new ObjectMapper();
    }

    // ===================================================================
    //  record
    // ===================================================================

    /**
     * Record a tracking event for a package (or a shipment, when the
     * event type cascades). See class javadoc for the full pipeline.
     *
     * @param tenantId the caller's tenant (RLS scope)
     * @param userId the acting user (audit + event.userId)
     * @param packageId the target. For package-level events this is a
     *        real {@code packages.id}; for shipment-level events
     *        ({@code shipment_validated}, {@code shipment_rejected}) the
     *        caller passes the {@code shipments.id} and the service
     *        fans the transition out.
     * @param req the event payload (event_type, event_timestamp,
     *        metadata, source_ip, user_agent)
     * @return the persisted {@link TrackingEvent}
     * @throws BusinessRuleException {@code PACKAGE_NOT_FOUND},
     *         {@code DUPLICATE_EVENT},
     *         {@code INVALID_STATE_TRANSITION},
     *         {@code EVENT_VALIDATION_ERROR},
     *         {@code INSUFFICIENT_PERMISSIONS}
     */
    @Transactional("companyTransactionManager")
    public TrackingEvent record(UUID tenantId, UUID userId, UUID packageId, RecordEventRequest req) {
        // 1. Event contract lookup.
        EventContract contract = getEventContract(req.eventType());

        // 2. Mandatory-field validation.
        validateMandatoryFields(req.payload(), contract.mandatoryFields());

        // 3. Role authorization.
        Set<String> userRoles = resolveUserRoleNames(userId);
        validateRoleAuthorization(userRoles, contract.allowedRoles());

        // 4. Event hash + idempotency check.
        Instant eventTimestamp = req.eventTimestamp() == null ? Instant.now() : req.eventTimestamp();
        Map<String, Object> canonicalPayload = canonicalize(req.payload());
        String eventHash =
                EventHashCalculator.computeHash(packageId, req.eventType(), canonicalPayload, eventTimestamp);
        if (trackingEventRepository.existsByEventHash(eventHash)) {
            throw new BusinessRuleException(
                    CODE_DUPLICATE_EVENT,
                    Map.of("eventHash", eventHash, "packageId", packageId.toString(), "eventType", req.eventType()));
        }

        // 5. Resolve target row(s) and apply FSM transition(s).
        Optional<Package> directPackage = packageRepository.findByIdAndTenantIdForUpdate(packageId, tenantId);
        if (directPackage.isPresent()) {
            return recordPackageLevelEvent(
                    tenantId, userId, directPackage.get(), req, contract, eventTimestamp, canonicalPayload, eventHash);
        }
        return recordShipmentLevelEvent(
                tenantId, userId, packageId, req, contract, eventTimestamp, canonicalPayload, eventHash);
    }

    // ===================================================================
    //  list
    // ===================================================================

    /**
     * Full event history for a package, oldest-first. Throws
     * {@code PACKAGE_NOT_FOUND} when the row is missing or belongs to
     * another tenant.
     */
    @Transactional("companyTransactionManager")
    public List<TrackingEvent> list(UUID tenantId, UUID packageId) {
        Package p = packageRepository
                .findById(packageId)
                .filter(pkg -> pkg.getTenantId() != null && pkg.getTenantId().equals(tenantId))
                .orElseThrow(() ->
                        new BusinessRuleException(CODE_PACKAGE_NOT_FOUND, Map.of("packageId", packageId.toString())));
        return trackingEventRepository.findByPackageIdOrderByEventTimestampAsc(p.getId());
    }

    // ===================================================================
    //  Private helpers
    // ===================================================================

    /**
     * Look up the contract for an event type. Throws
     * {@code EVENT_VALIDATION_ERROR} when the event type is not in the
     * catalog — no silent fall-through.
     */
    private EventContract getEventContract(String eventType) {
        EventContract contract = CONTRACTS.get(eventType);
        if (contract == null) {
            throw new BusinessRuleException(
                    CODE_EVENT_VALIDATION_ERROR,
                    Map.of("field", "eventType", "value", String.valueOf(eventType), "reason", "unknown event_type"));
        }
        return contract;
    }

    /**
     * Verify every mandatory key is present and non-null in
     * {@code payload}. Throws {@code EVENT_VALIDATION_ERROR} on the
     * first missing key.
     */
    private void validateMandatoryFields(Map<String, Object> payload, List<String> mandatory) {
        if (payload == null) {
            throw new BusinessRuleException(CODE_EVENT_VALIDATION_ERROR, Map.of("reason", "payload is required"));
        }
        for (String key : mandatory) {
            Object value = payload.get(key);
            if (value == null || (value instanceof String s && s.isBlank())) {
                throw new BusinessRuleException(
                        CODE_EVENT_VALIDATION_ERROR,
                        Map.of("field", key, "reason", "mandatory field missing or blank"));
            }
        }
    }

    /**
     * Verify the user holds at least one role in
     * {@code allowedRoles}. Throws {@code INSUFFICIENT_PERMISSIONS} on
     * no overlap.
     */
    private void validateRoleAuthorization(Set<String> userRoles, List<String> allowedRoles) {
        boolean ok = allowedRoles.stream().anyMatch(userRoles::contains);
        if (!ok) {
            throw new BusinessRuleException(
                    CODE_INSUFFICIENT_PERMISSIONS,
                    Map.of("userRoles", userRoles.toString(), "requiredOneOf", allowedRoles.toString()));
        }
    }

    /**
     * Resolve the acting user's role ids to names via the role catalog.
     * Returns the empty set when the user has no roles — callers should
     * treat empty as "no permission to do anything".
     */
    private Set<String> resolveUserRoleNames(UUID userId) {
        List<UUID> roleIds = companyUserRoleRepository.findRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        return roleIds.stream()
                .map(roleRepository::findById)
                .filter(Optional::isPresent)
                .map(opt -> opt.get().getName())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Apply the FSM transition implied by the event contract (or the
     * optional {@code targetStatus} payload field on
     * {@code compensating_event}). No-op when the contract has no
     * fixed target and the payload does not specify one.
     *
     * <p>When the target is {@code RETENIDO}, the package's prior
     * status is captured in {@code previousStatus} per PRD §7.4
     * reversible-state semantics — this is what allows a future
     * compensating_event to roll back to the original state.
     */
    private void applyTransitionIfAny(EventContract contract, Map<String, Object> payload, Package pkg) {
        String from = pkg.getStatus();
        String to = contract.perPackageTargetStatus();
        if (to == null && contract.eventType().equals(EVENT_COMPENSATING) && payload != null) {
            Object supplied = payload.get(KEY_TARGET_STATUS);
            if (supplied instanceof String s && !s.isBlank()) {
                to = s;
            }
        }
        if (to == null) {
            // No transition requested (e.g. package_created, or
            // compensating_event without a targetStatus).
            return;
        }
        if (!PackageFsm.isValidTransition(from, to)) {
            throw new BusinessRuleException(
                    CODE_INVALID_STATE_TRANSITION,
                    Map.of(
                            "packageId",
                            pkg.getId().toString(),
                            "from",
                            from,
                            "to",
                            to,
                            "eventType",
                            contract.eventType()));
        }
        if (PackageFsm.RETENIDO.equals(to) && !PackageFsm.RETENIDO.equals(from)) {
            pkg.setPreviousStatus(from);
        }
        pkg.setStatus(to);
    }

    /**
     * Apply the package-level event pipeline: lock + transition +
     * persist + recalc + audit. Returns the saved event.
     */
    private TrackingEvent recordPackageLevelEvent(
            UUID tenantId,
            UUID userId,
            Package pkg,
            RecordEventRequest req,
            EventContract contract,
            Instant eventTimestamp,
            Map<String, Object> canonicalPayload,
            String eventHash) {
        applyTransitionIfAny(contract, req.payload(), pkg);
        packageRepository.save(pkg);
        TrackingEvent saved = persistTrackingEvent(
                tenantId, userId, pkg.getId(), contract.eventType(), eventTimestamp, canonicalPayload, eventHash, req);
        recalcShipmentAggregate(tenantId, pkg.getShipmentId());
        emitPackageAudit(tenantId, userId, pkg, contract, eventHash);
        return saved;
    }

    /**
     * Apply the shipment-level event pipeline: load shipment, fan the
     * transition out to every package (re-locking each), recalc +
     * persist + audit. Returns the saved event (anchored on the
     * shipment id per {@code ShipmentService} convention).
     */
    private TrackingEvent recordShipmentLevelEvent(
            UUID tenantId,
            UUID userId,
            UUID shipmentId,
            RecordEventRequest req,
            EventContract contract,
            Instant eventTimestamp,
            Map<String, Object> canonicalPayload,
            String eventHash) {
        if (!contract.cascadeShipmentLevel()) {
            // Caller asked for a package-level event with a shipment id —
            // refuse. We already established no package matches (the
            // direct-package lookup above returned empty).
            throw new BusinessRuleException(
                    CODE_PACKAGE_NOT_FOUND,
                    Map.of(
                            "packageId",
                            shipmentId.toString(),
                            "eventType",
                            req.eventType(),
                            "reason",
                            "event is not a shipment-level event"));
        }

        Shipment shipment = shipmentRepository
                .findById(shipmentId)
                .filter(s -> s.getTenantId() != null && s.getTenantId().equals(tenantId))
                .orElseThrow(() ->
                        new BusinessRuleException(CODE_PACKAGE_NOT_FOUND, Map.of("packageId", shipmentId.toString())));

        List<Package> packages = packageRepository.findByShipmentId(shipment.getId());
        for (Package p : packages) {
            // Re-load with the lock so concurrent callers serialize.
            Package locked = packageRepository
                    .findByIdAndTenantIdForUpdate(p.getId(), tenantId)
                    .orElseThrow(() -> new BusinessRuleException(
                            CODE_PACKAGE_NOT_FOUND,
                            Map.of("packageId", p.getId().toString())));
            applyTransitionIfAny(contract, req.payload(), locked);
            packageRepository.save(locked);
        }

        recalcShipmentAggregate(tenantId, shipment.getId());
        // Reload the shipment to pick up the new aggregate status for the audit
        // and the packageId-of-record anchor.
        Shipment reloaded = shipmentRepository.findById(shipment.getId()).orElse(shipment);

        TrackingEvent saved = persistTrackingEvent(
                tenantId,
                userId,
                reloaded.getId(),
                contract.eventType(),
                eventTimestamp,
                canonicalPayload,
                eventHash,
                req);
        emitShipmentAudit(tenantId, userId, reloaded, contract, eventHash);
        return saved;
    }

    /**
     * Persist a single {@link TrackingEvent}. The anchor row
     * ({@code packageId}) is supplied by the caller — for package-level
     * events it is the package id, for shipment-level events it is the
     * shipment id (per {@code ShipmentService} convention).
     */
    private TrackingEvent persistTrackingEvent(
            UUID tenantId,
            UUID userId,
            UUID anchorId,
            String eventType,
            Instant eventTimestamp,
            Map<String, Object> canonicalPayload,
            String eventHash,
            RecordEventRequest req) {
        TrackingEvent ev = new TrackingEvent();
        ev.setId(UUID.randomUUID());
        ev.setTenantId(tenantId);
        ev.setPackageId(anchorId);
        ev.setEventType(eventType);
        ev.setEventTimestamp(eventTimestamp);
        ev.setUserId(userId);
        ev.setEventSource(EVENT_SOURCE_OPERADOR_SUCURSAL);
        ev.setMetadata(serializeMetadata(canonicalPayload));
        ev.setEventHash(eventHash);
        ev.setSourceIp(req.sourceIp());
        ev.setUserAgent(truncate(req.userAgent(), 500));
        return trackingEventRepository.save(ev);
    }

    /**
     * Recompute the {@code shipments.status} aggregate from the
     * current package statuses and persist. No-op when the shipment
     * is missing for this tenant (defensive — the FSM updates should
     * never leave a package orphaned of its shipment).
     */
    private void recalcShipmentAggregate(UUID tenantId, UUID shipmentId) {
        Shipment shipment = shipmentRepository
                .findById(shipmentId)
                .filter(s -> s.getTenantId() != null && s.getTenantId().equals(tenantId))
                .orElse(null);
        if (shipment == null) {
            return;
        }
        List<String> statuses = new ArrayList<>();
        for (Package p : packageRepository.findByShipmentId(shipment.getId())) {
            statuses.add(p.getStatus());
        }
        String aggregate = ShipmentStatusCalculator.calculate(statuses);
        if (aggregate == null) {
            aggregate = PackageFsm.CREADO;
        }
        shipment.setStatus(aggregate);
        shipmentRepository.save(shipment);
    }

    /**
     * Build a canonicalized payload map for hashing + JSON storage.
     * Always non-null; callers that pass null get an empty map.
     */
    private Map<String, Object> canonicalize(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return new HashMap<>();
        }
        // TreeMap ensures deterministic key ordering — EventHashCalculator
        // would do the same internally, but doing it here also makes the
        // stored metadata deterministic.
        return new java.util.TreeMap<>(payload);
    }

    private String serializeMetadata(Map<String, Object> meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to serialize event metadata — falling back to empty", e);
            return "{}";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private void emitPackageAudit(UUID tenantId, UUID userId, Package pkg, EventContract contract, String eventHash) {
        Map<String, Object> meta = new java.util.TreeMap<>();
        meta.put("packageId", pkg.getId().toString());
        meta.put("shipmentId", String.valueOf(pkg.getShipmentId()));
        meta.put("eventType", contract.eventType());
        meta.put("newStatus", pkg.getStatus());
        meta.put("previousStatus", pkg.getPreviousStatus());
        meta.put("eventHash", eventHash);
        auditLogger.logAsync(
                new AuditEvent(AUDIT_EVENT_RECORDED, userId, AuditEvent.UserScope.COMPANY, tenantId, null, null, meta));
    }

    private void emitShipmentAudit(
            UUID tenantId, UUID userId, Shipment shipment, EventContract contract, String eventHash) {
        Map<String, Object> meta = new java.util.TreeMap<>();
        meta.put("shipmentId", shipment.getId().toString());
        meta.put("eventType", contract.eventType());
        meta.put("newStatus", shipment.getStatus());
        meta.put("eventHash", eventHash);
        auditLogger.logAsync(
                new AuditEvent(AUDIT_EVENT_RECORDED, userId, AuditEvent.UserScope.COMPANY, tenantId, null, null, meta));
    }

    // ===================================================================
    //  Records / DTOs
    // ===================================================================

    /**
     * Request body for {@link #record}.
     *
     * @param eventType the wire-format event_type (must be in the
     *        {@link EventContract} catalog)
     * @param eventTimestamp when the event occurred; defaults to
     *        {@link Instant#now()} when null
     * @param payload event metadata; mandatory keys per contract
     * @param sourceIp optional client IP (audit)
     * @param userAgent optional client UA (audit, max 500 chars)
     */
    public record RecordEventRequest(
            String eventType, Instant eventTimestamp, Map<String, Object> payload, String sourceIp, String userAgent) {}
}
