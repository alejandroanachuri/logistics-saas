package ar.com.logistics.shipment.service;

import ar.com.logistics.common.audit.AuditEvent;
import ar.com.logistics.common.audit.AuditLogger;
import ar.com.logistics.common.exception.BusinessRuleException;
import ar.com.logistics.shipment.domain.Branch;
import ar.com.logistics.shipment.domain.Package;
import ar.com.logistics.shipment.domain.ServiceLevel;
import ar.com.logistics.shipment.domain.Shipment;
import ar.com.logistics.shipment.domain.TrackingEvent;
import ar.com.logistics.shipment.repository.company.AddressRepository;
import ar.com.logistics.shipment.repository.company.BranchRepository;
import ar.com.logistics.shipment.repository.company.CustomerRepository;
import ar.com.logistics.shipment.repository.company.PackageRepository;
import ar.com.logistics.shipment.repository.company.ServiceLevelRepository;
import ar.com.logistics.shipment.repository.company.ShipmentRepository;
import ar.com.logistics.shipment.repository.company.TrackingEventRepository;
import ar.com.logistics.shipment.tracking.EventHashCalculator;
import ar.com.logistics.shipment.tracking.LgstGeneratorService;
import ar.com.logistics.shipment.tracking.ShipmentStatusCalculator;
import ar.com.logistics.tenant.domain.Tenant;
import ar.com.logistics.tenant.domain.TenantStatus;
import ar.com.logistics.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central service for the shipment domain (etapa-3-envios, PR-3a Chunk B).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>{@link #create}: validate tenant ACTIVE status, sender != receiver,
 *       and existence of sender/receiver/deliveryAddress. Lazy-seed a
 *       PRINCIPAL branch + STANDARD service level on the first shipment
 *       for a fresh tenant. Generate LGST via
 *       {@link LgstGeneratorService#generateAndPersist(java.util.function.Predicate)}
 *       with the retry loop built in PR-2. Create N packages with
 *       {@code qrCode=QR-{tracking_id}-{index}} and emit a
 *       {@code package_created} tracking event per package
 *       (idempotent via {@link EventHashCalculator}). Aggregate
 *       status computed via {@link ShipmentStatusCalculator}. If
 *       {@code validateNow=true} the shipment moves to the computed
 *       aggregate status and a {@code shipment_validated} event is
 *       emitted. Bump the tenant counter via
 *       {@link TenantRepository#incrementShipmentCount(UUID)}. Audit
 *       {@code SHIPMENT_CREATED}.</li>
 *   <li>{@link #list}: paginated + filterable by status, date range, and
 *       a free-text search against {@code trackingId} / {@code code}.</li>
 *   <li>{@link #get}: returns Shipment + List<Package> + the latest
 *       tracking event. Throws {@code SHIPMENT_NOT_FOUND} when the row
 *       does not belong to the caller's tenant.</li>
 *   <li>{@link #getTimeline}: full event history for the shipment's
 *       packages.</li>
 *   <li>{@link #update}: only allowed while {@code status = PRE_ALTA};
 *       any later status throws {@code INVALID_STATE_TRANSITION}.</li>
 *   <li>{@link #validate}: {@code PRE_ALTA -> CREADO} (moves the
 *       aggregate status to the calculator's output). Emits
 *       {@code shipment_validated}. Audit {@code SHIPMENT_VALIDATED}.</li>
 *   <li>{@link #reject}: {@code PRE_ALTA -> CANCELADO} (any role).
 *       Emits {@code shipment_rejected}. Audit {@code SHIPMENT_REJECTED}.</li>
 *   <li>{@link #cancel}: any non-final status {@code -> CANCELADO}
 *       (admin-only — enforced at the controller layer; this method
 *       only enforces the terminal-state guard). Emits
 *       {@code shipment_cancelled}. Audit {@code SHIPMENT_CANCELLED}.</li>
 * </ul>
 *
 * <p>Transaction boundary: every public method runs in a single
 * {@code @Transactional("companyTransactionManager")} block. Reads
 * are RLS-scoped via V16. The LGST retry loop runs inside the same
 * transaction as the shipment save (the {@code saveAttempt}
 * {@link java.util.function.Predicate} is the seam; the service hands
 * the loop a callable that performs the INSERT inside the
 * transaction).
 */
@Service
public class ShipmentService {

    private static final Logger LOG = LoggerFactory.getLogger(ShipmentService.class);

    // ------------------------------------------------------------------------
    //  Canonical wire-format error codes (GlobalExceptionHandler maps these
    //  to HTTP statuses).
    // ------------------------------------------------------------------------

    static final String CODE_SHIPMENT_NOT_FOUND = "SHIPMENT_NOT_FOUND";
    static final String CODE_COMPANY_SUSPENDED = "COMPANY_SUSPENDED";
    static final String CODE_SENDER_EQUALS_RECEIVER = "SENDER_EQUALS_RECEIVER";
    static final String CODE_CUSTOMER_NOT_FOUND = "CUSTOMER_NOT_FOUND";
    static final String CODE_ADDRESS_NOT_FOUND = "ADDRESS_NOT_FOUND";
    static final String CODE_INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";

    // ------------------------------------------------------------------------
    //  Domain constants (PRD §7.2 / §7.3 / §6.9 / §5.1).
    // ------------------------------------------------------------------------

    /** Initial shipment status before validation. */
    static final String STATUS_PRE_ALTA = "PRE_ALTA";

    /** Canonical package starting status (FSM default — see {@code PackageFsm}). */
    static final String PACKAGE_STATUS_CREADO = "CREADO";

    /** Terminal statuses — cancellation is rejected on these. */
    static final List<String> TERMINAL_STATUSES = List.of("ENTREGADO", "DEVUELTO", "CANCELADO");

    /** QR code prefix per PRD §5.2. */
    static final String QR_PREFIX = "QR-";

    /** Lazy-seed branch code (Etapa 3 placeholder — Etapa 4 will revisit). */
    static final String DEFAULT_BRANCH_CODE = "PRINCIPAL";

    /** Lazy-seed service-level code. */
    static final String DEFAULT_SERVICE_LEVEL_CODE = "STANDARD";

    /** Default branch / service-level name (display-only). */
    static final String DEFAULT_BRANCH_NAME = "Principal";

    static final String DEFAULT_SERVICE_LEVEL_NAME = "Standard";

    /** eventType strings — match the values the tracking-event service (PR-3b)
     *  will also emit. */
    static final String EVENT_PACKAGE_CREATED = "package_created";

    static final String EVENT_SHIPMENT_VALIDATED = "shipment_validated";
    static final String EVENT_SHIPMENT_REJECTED = "shipment_rejected";
    static final String EVENT_SHIPMENT_CANCELLED = "shipment_cancelled";

    /** Default event source for events emitted by this service. */
    static final String EVENT_SOURCE_SYSTEM = "SYSTEM";

    /** Default category for packages when the caller omits one. */
    static final String DEFAULT_PACKAGE_CATEGORY = "GENERAL";

    /** Default declared currency (Argentina, AFIP-aligned). */
    static final String DEFAULT_DECLARED_CURRENCY = "ARS";

    // ------------------------------------------------------------------------
    //  Dependencies.
    // ------------------------------------------------------------------------

    private final ShipmentRepository shipmentRepository;
    private final PackageRepository packageRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final BranchRepository branchRepository;
    private final ServiceLevelRepository serviceLevelRepository;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final TenantRepository tenantRepository;
    private final LgstGeneratorService lgstGeneratorService;
    private final AuditLogger auditLogger;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            PackageRepository packageRepository,
            TrackingEventRepository trackingEventRepository,
            BranchRepository branchRepository,
            ServiceLevelRepository serviceLevelRepository,
            CustomerRepository customerRepository,
            AddressRepository addressRepository,
            TenantRepository tenantRepository,
            LgstGeneratorService lgstGeneratorService,
            AuditLogger auditLogger) {
        this.shipmentRepository = shipmentRepository;
        this.packageRepository = packageRepository;
        this.trackingEventRepository = trackingEventRepository;
        this.branchRepository = branchRepository;
        this.serviceLevelRepository = serviceLevelRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.tenantRepository = tenantRepository;
        this.lgstGeneratorService = lgstGeneratorService;
        this.auditLogger = auditLogger;
    }

    // ===================================================================
    //  create
    // ===================================================================

    /**
     * Create a shipment. See class javadoc for the full pipeline.
     *
     * @param tenantId the caller's tenant (RLS scope)
     * @param adminId the acting admin user (audit + createdBy/updatedBy)
     * @param req the create payload
     * @return a {@link CreateShipmentResponse} with the persisted shipment
     *         and its packages (LGST already generated)
     * @throws BusinessRuleException {@code COMPANY_SUSPENDED},
     *         {@code SENDER_EQUALS_RECEIVER}, {@code CUSTOMER_NOT_FOUND},
     *         {@code ADDRESS_NOT_FOUND}
     * @throws IllegalStateException if the LGST retry loop exhausts
     *         (caller should map to 500)
     */
    @Transactional("companyTransactionManager")
    public CreateShipmentResponse create(UUID tenantId, UUID adminId, CreateShipmentRequest req) {
        // 1. Tenant must be ACTIVE.
        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow(() ->
                        new BusinessRuleException(CODE_COMPANY_SUSPENDED, Map.of("tenantId", tenantId.toString())));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new BusinessRuleException(
                    CODE_COMPANY_SUSPENDED,
                    Map.of("tenantId", tenantId.toString(), "status", String.valueOf(tenant.getStatus())));
        }

        // 2. Sender must not be the same as the receiver.
        if (req.senderId().equals(req.receiverId())) {
            throw new BusinessRuleException(
                    CODE_SENDER_EQUALS_RECEIVER,
                    Map.of(
                            "senderId",
                            req.senderId().toString(),
                            "receiverId",
                            req.receiverId().toString()));
        }

        // 3. Sender, receiver, and delivery address must exist in this tenant.
        requireCustomer(tenantId, req.senderId());
        requireCustomer(tenantId, req.receiverId());
        requireAddress(tenantId, req.deliveryAddressId());

        // 4. Lazy-seed PRINCIPAL branch + STANDARD service level on first
        //    shipment for the tenant.
        lazySeedBranchesAndServiceLevels(tenantId);

        UUID originBranchId = resolveBranchId(tenantId);
        UUID destinationBranchId = originBranchId; // Etapa 3 single-branch default.
        UUID serviceLevelId = resolveServiceLevelId(tenantId);

        // 5. Build the Shipment row (status = PRE_ALTA initially).
        Instant now = Instant.now();
        Shipment shipment = new Shipment();
        shipment.setId(UUID.randomUUID());
        shipment.setTenantId(tenantId);
        shipment.setShipmentType(req.shipmentType());
        shipment.setSenderId(req.senderId());
        shipment.setReceiverId(req.receiverId());
        shipment.setDeliveryAddressId(req.deliveryAddressId());
        shipment.setOriginBranchId(originBranchId);
        shipment.setDestinationBranchId(destinationBranchId);
        shipment.setServiceLevelId(serviceLevelId);
        shipment.setPaymentType(req.paymentType());
        shipment.setDeliveryMode(req.deliveryMode());
        shipment.setDeliveryInstructions(req.deliveryInstructions());
        shipment.setStatus(STATUS_PRE_ALTA);
        shipment.setSlaStatus("ON_TIME");
        shipment.setCreatedBy(adminId);
        // createdAt / updatedAt are stamped by the entity @PrePersist.

        // 6. Generate the LGST via the retry loop. The predicate receives
        //    a candidate tracking id, sets it on the shipment, attempts
        //    the save, and returns true on success / false on
        //    DataIntegrityViolationException. The retry loop lives inside
        //    LgstGeneratorService (PR-2) — this method does NOT loop
        //    itself. We use a final reference to `shipment` for the lambda
        //    capture (the variable is reassigned later if validateNow=true).
        final Shipment shipmentRef = shipment;
        String trackingId = lgstGeneratorService.generateAndPersist(candidate -> {
            shipmentRef.setTrackingId(candidate);
            try {
                shipmentRepository.save(shipmentRef);
                return true;
            } catch (DataIntegrityViolationException ex) {
                LOG.debug("LGST collision on candidate {} — retrying", candidate);
                return false;
            }
        });
        shipment.setTrackingId(trackingId);

        // 7. Create N packages + emit N package_created events.
        List<Package> packages = new ArrayList<>();
        List<String> packageStatuses = new ArrayList<>();
        int idx = 1;
        for (CreatePackageRequest pReq : req.packages()) {
            Package pkg = new Package();
            pkg.setId(UUID.randomUUID());
            pkg.setTenantId(tenantId);
            pkg.setShipmentId(shipment.getId());
            pkg.setQrCode(QR_PREFIX + trackingId + "-" + idx);
            pkg.setStatus(PACKAGE_STATUS_CREADO);
            pkg.setWeightKg(pReq.weightKg());
            pkg.setVolumeCm3(pReq.volumeCm3());
            pkg.setDimensionsCm(pReq.dimensionsCm());
            pkg.setContentDescription(pReq.contentDescription());
            pkg.setDeclaredValue(pReq.declaredValue());
            pkg.setDeclaredCurrency(
                    pReq.declaredCurrency() == null || pReq.declaredCurrency().isBlank()
                            ? DEFAULT_DECLARED_CURRENCY
                            : pReq.declaredCurrency());
            pkg.setHasInsurance(pReq.hasInsurance());
            pkg.setInsurancePremium(pReq.insurancePremium());
            pkg.setFragile(pReq.isFragile());
            pkg.setUrgent(pReq.isUrgent());
            pkg.setRequiresSignature(pReq.requiresSignature());
            pkg.setRequiresIdCheck(pReq.requiresIdCheck());
            pkg.setCategory(
                    pReq.category() == null || pReq.category().isBlank() ? DEFAULT_PACKAGE_CATEGORY : pReq.category());
            pkg = packageRepository.save(pkg);

            // Emit the package_created event. The event hash is
            // computed by EventHashCalculator; same (packageId,
            // eventType, payload, rounded timestamp) → same hash → DB
            // UNIQUE catches the duplicate.
            TrackingEvent ev = new TrackingEvent();
            ev.setId(UUID.randomUUID());
            ev.setTenantId(tenantId);
            ev.setPackageId(pkg.getId());
            ev.setEventType(EVENT_PACKAGE_CREATED);
            ev.setEventTimestamp(now);
            ev.setBranchId(originBranchId);
            ev.setUserId(adminId);
            ev.setEventSource(EVENT_SOURCE_SYSTEM);
            Map<String, Object> meta = new java.util.TreeMap<>();
            meta.put("shipmentId", shipment.getId().toString());
            meta.put("qrCode", pkg.getQrCode());
            meta.put("packageIndex", idx);
            String payloadJson = serializeMetadata(meta);
            ev.setMetadata(payloadJson);
            ev.setEventHash(EventHashCalculator.computeHash(pkg.getId(), EVENT_PACKAGE_CREATED, meta, now));
            trackingEventRepository.save(ev);

            packages.add(pkg);
            packageStatuses.add(pkg.getStatus());
            idx++;
        }

        // 8. Compute aggregate status via ShipmentStatusCalculator.
        String aggregate = ShipmentStatusCalculator.calculate(packageStatuses);

        // 9. If validateNow=true, move to the aggregate status and emit
        //    the shipment_validated event.
        if (req.validateNow()) {
            shipment.setStatus(aggregate);
            TrackingEvent validated = new TrackingEvent();
            validated.setId(UUID.randomUUID());
            validated.setTenantId(tenantId);
            validated.setPackageId(shipment.getId()); // shipment-level event anchors on shipment id
            validated.setEventType(EVENT_SHIPMENT_VALIDATED);
            validated.setEventTimestamp(now);
            validated.setBranchId(originBranchId);
            validated.setUserId(adminId);
            validated.setEventSource(EVENT_SOURCE_SYSTEM);
            Map<String, Object> meta = new java.util.TreeMap<>();
            meta.put("paymentConfirmed", true);
            meta.put("docsChecked", true);
            meta.put("validatedBy", adminId.toString());
            String payloadJson = serializeMetadata(meta);
            validated.setMetadata(payloadJson);
            validated.setEventHash(
                    EventHashCalculator.computeHash(shipment.getId(), EVENT_SHIPMENT_VALIDATED, meta, now));
            trackingEventRepository.save(validated);
            shipment = shipmentRepository.save(shipment);
        }

        // 10. Bump the tenant counter (atomic UPDATE — see TenantRepository).
        tenantRepository.incrementShipmentCount(tenantId);

        // 11. Audit SHIPMENT_CREATED.
        Map<String, Object> auditMeta = new java.util.TreeMap<>();
        auditMeta.put("shipmentId", shipment.getId().toString());
        auditMeta.put("trackingId", trackingId);
        auditMeta.put("packageCount", packages.size());
        auditMeta.put("validateNow", req.validateNow());
        auditLogger.logAsync(new AuditEvent(
                "SHIPMENT_CREATED", adminId, AuditEvent.UserScope.COMPANY, tenantId, null, null, auditMeta));

        return new CreateShipmentResponse(shipment, packages);
    }

    // ===================================================================
    //  list
    // ===================================================================

    /**
     * Paginated list with optional filters. Mirrors the in-memory filter
     * shape of {@code CustomerService.list} — small dataset per tenant,
     * no JPA {@code Specification} needed for v1.
     */
    @Transactional("companyTransactionManager")
    public Page<Shipment> list(UUID tenantId, ShipmentListFilters filters, Pageable pageable) {
        List<Shipment> all = shipmentRepository.findAll();
        List<Shipment> filtered = new ArrayList<>();
        for (Shipment s : all) {
            if (s.getTenantId() == null || !s.getTenantId().equals(tenantId)) {
                continue;
            }
            if (filters.status() != null && !filters.status().isBlank()) {
                if (!filters.status().equalsIgnoreCase(s.getStatus())) {
                    continue;
                }
            }
            if (filters.dateFrom() != null
                    && s.getCreatedAt() != null
                    && s.getCreatedAt().isBefore(filters.dateFrom())) {
                continue;
            }
            if (filters.dateTo() != null
                    && s.getCreatedAt() != null
                    && s.getCreatedAt().isAfter(filters.dateTo())) {
                continue;
            }
            if (filters.search() != null && !filters.search().isBlank()) {
                String needle = filters.search().toLowerCase();
                String hay = ((s.getTrackingId() == null ? "" : s.getTrackingId()) + " "
                                + (s.getCode() == null ? "" : s.getCode()))
                        .toLowerCase();
                if (!hay.contains(needle)) {
                    continue;
                }
            }
            filtered.add(s);
        }
        int total = filtered.size();
        int from = Math.min((int) pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        return new PageImpl<>(filtered.subList(from, to), pageable, total);
    }

    // ===================================================================
    //  get
    // ===================================================================

    /**
     * Fetch a single shipment with its packages and the latest tracking
     * event. Throws {@code SHIPMENT_NOT_FOUND} when the row is missing
     * or belongs to another tenant.
     */
    @Transactional("companyTransactionManager")
    public ShipmentDetailDto get(UUID tenantId, UUID shipmentId) {
        Shipment s = requireShipment(tenantId, shipmentId);
        List<Package> packages = findPackagesForShipment(shipmentId);
        TrackingEvent latest = findLatestEventForPackages(packages);
        return new ShipmentDetailDto(s, packages, latest);
    }

    /**
     * Full event history across the shipment's packages, oldest-first.
     */
    @Transactional("companyTransactionManager")
    public List<TrackingEvent> getTimeline(UUID tenantId, UUID shipmentId) {
        requireShipment(tenantId, shipmentId);
        List<Package> packages = findPackagesForShipment(shipmentId);
        List<TrackingEvent> events = new ArrayList<>();
        for (Package p : packages) {
            // Read each package's events via the repository — for v1 we
            // collect all events across packages and return them sorted
            // by eventTimestamp. There is no per-shipment events table
            // (events anchor on packages), so this in-memory sweep is
            // intentional until PR-3b ships the dedicated event store.
            events.addAll(trackingEventRepository.findAll().stream()
                    .filter(e -> e.getPackageId() != null && e.getPackageId().equals(p.getId()))
                    .toList());
        }
        events.sort((a, b) -> {
            Instant ta = a.getEventTimestamp();
            Instant tb = b.getEventTimestamp();
            if (ta == null && tb == null) return 0;
            if (ta == null) return -1;
            if (tb == null) return 1;
            return ta.compareTo(tb);
        });
        return events;
    }

    // ===================================================================
    //  update
    // ===================================================================

    /**
     * Partial update. Only allowed while {@code status = PRE_ALTA}. Any
     * non-null field in the request is applied; null fields are left
     * unchanged.
     */
    @Transactional("companyTransactionManager")
    public Shipment update(UUID tenantId, UUID adminId, UUID shipmentId, UpdateShipmentRequest req) {
        Shipment s = requireShipment(tenantId, shipmentId);
        if (!STATUS_PRE_ALTA.equals(s.getStatus())) {
            throw new BusinessRuleException(
                    CODE_INVALID_STATE_TRANSITION,
                    Map.of("currentStatus", String.valueOf(s.getStatus()), "from", STATUS_PRE_ALTA));
        }

        List<String> changedFields = new ArrayList<>();
        if (req.deliveryInstructions() != null) {
            s.setDeliveryInstructions(req.deliveryInstructions());
            changedFields.add("deliveryInstructions");
        }
        if (req.paymentType() != null) {
            s.setPaymentType(req.paymentType());
            changedFields.add("paymentType");
        }
        if (req.deliveryMode() != null) {
            s.setDeliveryMode(req.deliveryMode());
            changedFields.add("deliveryMode");
        }
        if (req.promisedDeliveryDate() != null) {
            s.setPromisedDeliveryDate(req.promisedDeliveryDate());
            changedFields.add("promisedDeliveryDate");
        }

        s = shipmentRepository.save(s);

        if (!changedFields.isEmpty()) {
            Map<String, Object> meta = new java.util.TreeMap<>();
            meta.put("changedFields", changedFields.toString());
            meta.put("updatedBy", adminId.toString());
            auditLogger.logAsync(new AuditEvent(
                    "SHIPMENT_UPDATED", adminId, AuditEvent.UserScope.COMPANY, tenantId, null, null, meta));
        }

        return s;
    }

    // ===================================================================
    //  validate
    // ===================================================================

    /**
     * {@code PRE_ALTA -> CREADO} (or whatever aggregate
     * {@link ShipmentStatusCalculator} returns for the current package
     * statuses — for the fresh-create case with all packages in
     * {@code CREADO} the aggregate is {@code CREADO}). Throws
     * {@code INVALID_STATE_TRANSITION} when the shipment is not in
     * {@code PRE_ALTA}.
     */
    @Transactional("companyTransactionManager")
    public ShipmentDetailDto validate(UUID tenantId, UUID adminId, UUID shipmentId) {
        Shipment s = requireShipment(tenantId, shipmentId);
        if (!STATUS_PRE_ALTA.equals(s.getStatus())) {
            throw new BusinessRuleException(
                    CODE_INVALID_STATE_TRANSITION,
                    Map.of("currentStatus", String.valueOf(s.getStatus()), "expected", STATUS_PRE_ALTA));
        }

        List<Package> packages = findPackagesForShipment(shipmentId);
        List<String> packageStatuses = new ArrayList<>();
        for (Package p : packages) {
            packageStatuses.add(p.getStatus());
        }
        String aggregate = ShipmentStatusCalculator.calculate(packageStatuses);
        if (aggregate == null) {
            // No packages — degenerate; fall back to CREADO so the DB
            // CHECK on the status column still passes.
            aggregate = PACKAGE_STATUS_CREADO;
        }
        s.setStatus(aggregate);
        s = shipmentRepository.save(s);

        // Emit shipment_validated event.
        Instant now = Instant.now();
        TrackingEvent ev = new TrackingEvent();
        ev.setId(UUID.randomUUID());
        ev.setTenantId(tenantId);
        ev.setPackageId(shipmentId);
        ev.setEventType(EVENT_SHIPMENT_VALIDATED);
        ev.setEventTimestamp(now);
        ev.setUserId(adminId);
        ev.setEventSource(EVENT_SOURCE_SYSTEM);
        Map<String, Object> meta = new java.util.TreeMap<>();
        meta.put("docsChecked", true);
        meta.put("paymentConfirmed", true);
        meta.put("validatedBy", adminId.toString());
        ev.setMetadata(serializeMetadata(meta));
        ev.setEventHash(EventHashCalculator.computeHash(shipmentId, EVENT_SHIPMENT_VALIDATED, meta, now));
        trackingEventRepository.save(ev);

        auditLogger.logAsync(new AuditEvent(
                "SHIPMENT_VALIDATED",
                adminId,
                AuditEvent.UserScope.COMPANY,
                tenantId,
                null,
                null,
                Map.of("shipmentId", shipmentId.toString(), "newStatus", aggregate)));

        return new ShipmentDetailDto(s, packages, ev);
    }

    // ===================================================================
    //  reject
    // ===================================================================

    /**
     * {@code PRE_ALTA -> CANCELADO}. Admin or operator may reject a
     * shipment before validation. Throws
     * {@code INVALID_STATE_TRANSITION} if the shipment is no longer
     * in {@code PRE_ALTA}.
     */
    @Transactional("companyTransactionManager")
    public ShipmentDetailDto reject(UUID tenantId, UUID adminId, UUID shipmentId, String reason) {
        Shipment s = requireShipment(tenantId, shipmentId);
        if (!STATUS_PRE_ALTA.equals(s.getStatus())) {
            throw new BusinessRuleException(
                    CODE_INVALID_STATE_TRANSITION,
                    Map.of("currentStatus", String.valueOf(s.getStatus()), "expected", STATUS_PRE_ALTA));
        }

        s.setStatus("CANCELADO");
        s = shipmentRepository.save(s);

        // Cascade cancellation to all packages.
        List<Package> packages = findPackagesForShipment(shipmentId);
        for (Package p : packages) {
            p.setStatus("CANCELADO");
            packageRepository.save(p);
        }

        Instant now = Instant.now();
        TrackingEvent ev = new TrackingEvent();
        ev.setId(UUID.randomUUID());
        ev.setTenantId(tenantId);
        ev.setPackageId(shipmentId);
        ev.setEventType(EVENT_SHIPMENT_REJECTED);
        ev.setEventTimestamp(now);
        ev.setUserId(adminId);
        ev.setEventSource(EVENT_SOURCE_SYSTEM);
        Map<String, Object> meta = new java.util.TreeMap<>();
        meta.put("reason", reason == null ? "" : reason);
        meta.put("rejectedBy", adminId.toString());
        ev.setMetadata(serializeMetadata(meta));
        ev.setEventHash(EventHashCalculator.computeHash(shipmentId, EVENT_SHIPMENT_REJECTED, meta, now));
        trackingEventRepository.save(ev);

        auditLogger.logAsync(new AuditEvent(
                "SHIPMENT_REJECTED",
                adminId,
                AuditEvent.UserScope.COMPANY,
                tenantId,
                null,
                null,
                Map.of("shipmentId", shipmentId.toString(), "reason", String.valueOf(reason))));

        return new ShipmentDetailDto(s, packages, ev);
    }

    // ===================================================================
    //  cancel
    // ===================================================================

    /**
     * Cancel a shipment. Allowed from any non-terminal status. Throws
     * {@code INVALID_STATE_TRANSITION} when the shipment is already
     * in a terminal state ({@code ENTREGADO}, {@code DEVUELTO},
     * {@code CANCELADO}).
     *
     * <p>Admin-only is enforced at the controller layer (the auth
     * interceptor checks the acting user's roles); this method itself
     * does not gate on the role.
     */
    @Transactional("companyTransactionManager")
    public ShipmentDetailDto cancel(UUID tenantId, UUID adminId, UUID shipmentId, String reason) {
        Shipment s = requireShipment(tenantId, shipmentId);
        if (TERMINAL_STATUSES.contains(s.getStatus())) {
            throw new BusinessRuleException(
                    CODE_INVALID_STATE_TRANSITION,
                    Map.of("currentStatus", String.valueOf(s.getStatus()), "operation", "cancel"));
        }

        s.setStatus("CANCELADO");
        s = shipmentRepository.save(s);

        List<Package> packages = findPackagesForShipment(shipmentId);
        for (Package p : packages) {
            p.setStatus("CANCELADO");
            packageRepository.save(p);
        }

        Instant now = Instant.now();
        TrackingEvent ev = new TrackingEvent();
        ev.setId(UUID.randomUUID());
        ev.setTenantId(tenantId);
        ev.setPackageId(shipmentId);
        ev.setEventType(EVENT_SHIPMENT_CANCELLED);
        ev.setEventTimestamp(now);
        ev.setUserId(adminId);
        ev.setEventSource(EVENT_SOURCE_SYSTEM);
        Map<String, Object> meta = new java.util.TreeMap<>();
        meta.put("cancelledBy", adminId.toString());
        meta.put("reason", reason == null ? "" : reason);
        ev.setMetadata(serializeMetadata(meta));
        ev.setEventHash(EventHashCalculator.computeHash(shipmentId, EVENT_SHIPMENT_CANCELLED, meta, now));
        trackingEventRepository.save(ev);

        auditLogger.logAsync(new AuditEvent(
                "SHIPMENT_CANCELLED",
                adminId,
                AuditEvent.UserScope.COMPANY,
                tenantId,
                null,
                null,
                Map.of("shipmentId", shipmentId.toString(), "reason", String.valueOf(reason))));

        return new ShipmentDetailDto(s, packages, ev);
    }

    // ===================================================================
    //  Private helpers
    // ===================================================================

    /** Look up a customer scoped to the tenant; throw on miss. */
    private void requireCustomer(UUID tenantId, UUID customerId) {
        boolean exists = customerRepository
                .findById(customerId)
                .filter(c -> c.getTenantId() != null && c.getTenantId().equals(tenantId))
                .isPresent();
        if (!exists) {
            throw new BusinessRuleException(CODE_CUSTOMER_NOT_FOUND, Map.of("customerId", customerId.toString()));
        }
    }

    /** Look up an address scoped to the tenant; throw on miss. */
    private void requireAddress(UUID tenantId, UUID addressId) {
        boolean exists = addressRepository
                .findById(addressId)
                .filter(a -> a.getTenantId() != null && a.getTenantId().equals(tenantId))
                .isPresent();
        if (!exists) {
            throw new BusinessRuleException(CODE_ADDRESS_NOT_FOUND, Map.of("addressId", addressId.toString()));
        }
    }

    /** Look up a shipment scoped to the tenant; throw on miss. */
    private Shipment requireShipment(UUID tenantId, UUID shipmentId) {
        return shipmentRepository
                .findById(shipmentId)
                .filter(s -> s.getTenantId() != null && s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessRuleException(
                        CODE_SHIPMENT_NOT_FOUND, Map.of("shipmentId", shipmentId.toString())));
    }

    /**
     * Find all packages belonging to a shipment. Uses an in-memory sweep
     * via {@link PackageRepository#findAll()} because the v1 repository
     * does not yet expose {@code findByShipmentId}; the per-tenant
     * dataset is small enough to keep this in code until the dedicated
     * derived query lands in PR-3b.
     */
    private List<Package> findPackagesForShipment(UUID shipmentId) {
        List<Package> result = new ArrayList<>();
        for (Package p : packageRepository.findAll()) {
            if (p.getShipmentId() != null && p.getShipmentId().equals(shipmentId)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Latest event across the packages of a shipment, or {@code null}
     * if none. Used by {@link #get}. The in-memory sweep is intentional
     * — see {@link #getTimeline}.
     */
    private TrackingEvent findLatestEventForPackages(List<Package> packages) {
        TrackingEvent latest = null;
        for (Package p : packages) {
            for (TrackingEvent e : trackingEventRepository.findAll()) {
                if (e.getPackageId() == null || !e.getPackageId().equals(p.getId())) {
                    continue;
                }
                if (latest == null
                        || (e.getEventTimestamp() != null
                                && latest.getEventTimestamp() != null
                                && e.getEventTimestamp().isAfter(latest.getEventTimestamp()))) {
                    latest = e;
                }
            }
        }
        return latest;
    }

    /**
     * Seed the tenant's {@code PRINCIPAL} branch and {@code STANDARD}
     * service level if they don't already exist. The seed is idempotent:
     * we check existence via {@link BranchRepository#findAll()} (the
     * dedicated derived query lands in PR-3b) and the DB UNIQUE
     * constraint on {@code (tenant_id, code)} catches any race between
     * concurrent first-shipment creates — the loser sees a
     * {@link DataIntegrityViolationException} which we swallow.
     */
    private void lazySeedBranchesAndServiceLevels(UUID tenantId) {
        boolean branchExists = false;
        for (Branch b : branchRepository.findAll()) {
            if (b.getTenantId() != null
                    && b.getTenantId().equals(tenantId)
                    && DEFAULT_BRANCH_CODE.equals(b.getCode())) {
                branchExists = true;
                break;
            }
        }
        if (!branchExists) {
            Branch seed = new Branch();
            seed.setId(UUID.randomUUID());
            seed.setTenantId(tenantId);
            seed.setCode(DEFAULT_BRANCH_CODE);
            seed.setName(DEFAULT_BRANCH_NAME);
            seed.setActive(true);
            try {
                branchRepository.save(seed);
            } catch (DataIntegrityViolationException ex) {
                // Concurrent first-shipment race; the OTHER thread won.
                // Safe to ignore.
                LOG.debug("Branch seed race for tenant {} — swallowing DIV", tenantId);
            }
        }

        boolean serviceLevelExists = false;
        for (ServiceLevel sl : serviceLevelRepository.findAll()) {
            if (sl.getTenantId() != null
                    && sl.getTenantId().equals(tenantId)
                    && DEFAULT_SERVICE_LEVEL_CODE.equals(sl.getCode())) {
                serviceLevelExists = true;
                break;
            }
        }
        if (!serviceLevelExists) {
            ServiceLevel seed = new ServiceLevel();
            seed.setId(UUID.randomUUID());
            seed.setTenantId(tenantId);
            seed.setCode(DEFAULT_SERVICE_LEVEL_CODE);
            seed.setName(DEFAULT_SERVICE_LEVEL_NAME);
            seed.setActive(true);
            try {
                serviceLevelRepository.save(seed);
            } catch (DataIntegrityViolationException ex) {
                LOG.debug("ServiceLevel seed race for tenant {} — swallowing DIV", tenantId);
            }
        }
    }

    /** Resolve the principal branch id for this tenant after lazy-seed. */
    private UUID resolveBranchId(UUID tenantId) {
        for (Branch b : branchRepository.findAll()) {
            if (b.getTenantId() != null
                    && b.getTenantId().equals(tenantId)
                    && DEFAULT_BRANCH_CODE.equals(b.getCode())) {
                return b.getId();
            }
        }
        // Should not happen — the lazy-seed step always runs before
        // this call. Defensive: return a fresh UUID so the NOT NULL
        // constraint on shipments.origin_branch_id is satisfied and
        // the bug surfaces in the verify phase rather than masking
        // itself here.
        LOG.warn("PRINCIPAL branch missing after lazy seed for tenant {} — returning sentinel UUID", tenantId);
        return UUID.randomUUID();
    }

    /** Resolve the standard service level id for this tenant after lazy-seed. */
    private UUID resolveServiceLevelId(UUID tenantId) {
        for (ServiceLevel sl : serviceLevelRepository.findAll()) {
            if (sl.getTenantId() != null
                    && sl.getTenantId().equals(tenantId)
                    && DEFAULT_SERVICE_LEVEL_CODE.equals(sl.getCode())) {
                return sl.getId();
            }
        }
        LOG.warn("STANDARD service level missing after lazy seed for tenant {} — returning sentinel UUID", tenantId);
        return UUID.randomUUID();
    }

    /** Serialise a metadata map to JSON for the {@code metadata} column. */
    private static String serializeMetadata(Map<String, Object> meta) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(meta);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "{}";
        }
    }

    // ===================================================================
    //  Records / DTOs
    // ===================================================================

    /** Request body for {@link #create}. */
    public record CreateShipmentRequest(
            UUID senderId,
            UUID receiverId,
            UUID deliveryAddressId,
            String shipmentType,
            String originBranchCodeHint, // ignored in v1; future use
            String deliveryMode,
            String deliveryInstructions,
            String paymentType,
            List<CreatePackageRequest> packages,
            boolean validateNow) {}

    /** Request body for one package within {@link CreateShipmentRequest}. */
    public record CreatePackageRequest(
            BigDecimal weightKg,
            BigDecimal volumeCm3,
            String dimensionsCm,
            String contentDescription,
            BigDecimal declaredValue,
            String declaredCurrency,
            boolean hasInsurance,
            BigDecimal insurancePremium,
            boolean isFragile,
            boolean isUrgent,
            boolean requiresSignature,
            boolean requiresIdCheck,
            String category) {}

    /** Response from {@link #create}. */
    public record CreateShipmentResponse(Shipment shipment, List<Package> packages) {}

    /** Request body for {@link #update}. Any null field is left unchanged. */
    public record UpdateShipmentRequest(
            String deliveryInstructions,
            String paymentType,
            String deliveryMode,
            java.time.LocalDate promisedDeliveryDate) {}

    /** Filters for {@link #list}. Null fields are "no filter applied". */
    public record ShipmentListFilters(String status, Instant dateFrom, Instant dateTo, String search) {}

    /** Composite DTO for {@link #get} / {@link #validate} / {@link #cancel} / {@link #reject}. */
    public record ShipmentDetailDto(Shipment shipment, List<Package> packages, TrackingEvent latestEvent) {}
}
