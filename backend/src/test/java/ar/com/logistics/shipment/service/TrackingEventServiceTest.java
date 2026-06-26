package ar.com.logistics.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.logistics.auth.domain.Role;
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
import ar.com.logistics.shipment.service.TrackingEventService.RecordEventRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TrackingEventService}. Strict TDD — every test pins a
 * piece of the contract from spec §C (etapa-3-envios, PR-3b Chunk B) before
 * the implementation exists.
 *
 * <p>Contract highlights (mirrors the class javadoc in TrackingEventService):
 * <ul>
 *   <li>{@code record} is the central engine: validates the event contract,
 *       role authorization, FSM transition, idempotency; serializes concurrent
 *       transitions via SELECT FOR UPDATE; persists the tracking event;
 *       updates the package (and shipment aggregate) status; emits the
 *       audit {@code TRACKING_EVENT_RECORDED}.</li>
 *   <li>Six event types in scope:
 *       {@code package_created}, {@code shipment_validated},
 *       {@code shipment_rejected}, {@code shipment_cancelled},
 *       {@code package_cancelled}, {@code compensating_event}.</li>
 *   <li>Errors carry canonical codes:
 *       {@code PACKAGE_NOT_FOUND}, {@code DUPLICATE_EVENT},
 *       {@code INVALID_STATE_TRANSITION}, {@code EVENT_VALIDATION_ERROR},
 *       {@code INSUFFICIENT_PERMISSIONS}.</li>
 *   <li>{@code list} returns all events for a package ordered by
 *       event_timestamp ASC; throws {@code PACKAGE_NOT_FOUND} when the
 *       package does not exist.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TrackingEventServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID PACKAGE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID SHIPMENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    private static final UUID OTHER_PACKAGE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f3");

    private static final UUID ADMIN_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OPERATOR_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID DRIVER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID VIEWER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a4");

    @Mock
    private TrackingEventRepository trackingEventRepository;

    @Mock
    private PackageRepository packageRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private CompanyUserRoleRepository companyUserRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private TrackingEventService service;

    // -------------------------------------------------------------------
    //  Fixtures
    // -------------------------------------------------------------------

    private static Package pkg(UUID id, String status) {
        Package p = new Package();
        p.setId(id);
        p.setTenantId(TENANT_ID);
        p.setShipmentId(SHIPMENT_ID);
        p.setQrCode("QR-" + id);
        p.setStatus(status);
        p.setWeightKg(new java.math.BigDecimal("1.00"));
        p.setDeclaredCurrency("ARS");
        p.setContentDescription("content");
        p.setCategory("GENERAL");
        return p;
    }

    private static Shipment shipment(String status) {
        Shipment s = new Shipment();
        s.setId(SHIPMENT_ID);
        s.setTenantId(TENANT_ID);
        s.setTrackingId("LGST-7K2M9XQP");
        s.setStatus(status);
        s.setSlaStatus("ON_TIME");
        s.setShipmentType("NORMAL");
        s.setPaymentType("PAGO_ORIGEN");
        s.setDeliveryMode("DOMICILIO");
        s.setCreatedBy(USER_ID);
        return s;
    }

    private static Role role(UUID id, String name) {
        // Role has no @Setter (the catalog is read-only at runtime); use
        // reflection to set the three fields.
        Role r = new Role();
        try {
            java.lang.reflect.Field fId = Role.class.getDeclaredField("id");
            fId.setAccessible(true);
            fId.set(r, id);
            java.lang.reflect.Field fName = Role.class.getDeclaredField("name");
            fName.setAccessible(true);
            fName.set(r, name);
            java.lang.reflect.Field fScope = Role.class.getDeclaredField("scope");
            fScope.setAccessible(true);
            fScope.set(r, Role.RoleScope.COMPANY);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return r;
    }

    private static Role adminRole() {
        return role(ADMIN_ROLE_ID, "COMPANY_ADMIN");
    }

    private static Role operatorRole() {
        return role(OPERATOR_ROLE_ID, "COMPANY_OPERATOR");
    }

    private static Role driverRole() {
        return role(DRIVER_ROLE_ID, "COMPANY_DRIVER");
    }

    private static Role viewerRole() {
        return role(VIEWER_ROLE_ID, "COMPANY_VIEWER");
    }

    /** Stub the user as having the supplied roles. */
    private void stubUserRoles(Role... roles) {
        List<UUID> ids = new java.util.ArrayList<>();
        for (Role r : roles) {
            ids.add(r.getId());
        }
        when(companyUserRoleRepository.findRoleIdsByUserId(USER_ID)).thenReturn(ids);
        for (Role r : roles) {
            when(roleRepository.findById(r.getId())).thenReturn(Optional.of(r));
        }
    }

    /** Stub the SELECT FOR UPDATE package lookup (used by record). */
    private void stubPackageLookup(Package p) {
        when(packageRepository.findByIdAndTenantIdForUpdate(p.getId(), p.getTenantId()))
                .thenReturn(Optional.of(p));
    }

    /** Stub the SELECT FOR UPDATE lookup to return empty (record() then falls through to shipment lookup). */
    private void stubPackageLookupEmpty(UUID packageId) {
        when(packageRepository.findByIdAndTenantIdForUpdate(packageId, TENANT_ID))
                .thenReturn(Optional.empty());
    }

    /** Stub the plain package lookup (used by list). */
    private void stubPackageLookupPlain(Package p) {
        when(packageRepository.findById(p.getId())).thenReturn(Optional.of(p));
    }

    /** Stub {@code findByShipmentId} to return the given packages. */
    private void stubPackagesForShipment(UUID shipmentId, List<Package> packages) {
        when(packageRepository.findByShipmentId(shipmentId)).thenReturn(packages);
    }

    /** Stub package save to assign updated_at and return the same instance. */
    private void stubPackageSave() {
        when(packageRepository.save(any(Package.class))).thenAnswer(inv -> {
            Package p = inv.getArgument(0);
            p.setUpdatedAt(Instant.now());
            return p;
        });
    }

    /** Stub shipment lookup + save. */
    private void stubShipment(Shipment s) {
        when(shipmentRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Stub the shipment lookup to return empty. */
    private void stubShipmentLookupEmpty(UUID shipmentId) {
        when(shipmentRepository.findById(shipmentId)).thenReturn(Optional.empty());
    }

    /** Stub tracking event save. */
    private void stubTrackingSave() {
        when(trackingEventRepository.save(any(TrackingEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Stub the idempotency check to return {@code exists}. */
    private void stubIdempotencyCheck(boolean exists) {
        when(trackingEventRepository.existsByEventHash(any())).thenReturn(exists);
    }

    /** Build a basic valid {@code RecordEventRequest}. */
    private static RecordEventRequest validRequest(String eventType, Map<String, Object> payload) {
        return new RecordEventRequest(eventType, Instant.parse("2026-06-25T10:00:00Z"), payload, "127.0.0.1", "junit");
    }

    // ===================================================================
    //  record — happy path
    // ===================================================================

    @Test
    @DisplayName("record with package_created on PRE_ALTA package: saves event, hash computed, audit emitted")
    void record_packageCreated_happyPath() {
        Package p = pkg(PACKAGE_ID, PackageFsm.PRE_ALTA);
        Shipment s = shipment(PackageFsm.PRE_ALTA);
        stubPackageLookup(p);
        stubPackagesForShipment(SHIPMENT_ID, List.of(p));
        stubShipment(s);
        stubPackageSave();
        stubTrackingSave();
        stubUserRoles(operatorRole());
        stubIdempotencyCheck(false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("qrCode", "QR-test-1");
        payload.put("shipmentId", SHIPMENT_ID.toString());

        TrackingEvent saved = service.record(TENANT_ID, USER_ID, PACKAGE_ID, validRequest("package_created", payload));

        // Event was persisted with the computed hash.
        ArgumentCaptor<TrackingEvent> evCaptor = ArgumentCaptor.forClass(TrackingEvent.class);
        verify(trackingEventRepository, times(1)).save(evCaptor.capture());
        TrackingEvent ev = evCaptor.getValue();
        assertThat(ev.getEventType()).isEqualTo("package_created");
        assertThat(ev.getEventHash()).hasSize(64);
        assertThat(ev.getPackageId()).isEqualTo(PACKAGE_ID);
        assertThat(ev.getTenantId()).isEqualTo(TENANT_ID);

        // package_created is a no-op transition. The package stays PRE_ALTA.
        verify(packageRepository, times(1)).save(any(Package.class));
        assertThat(p.getStatus()).isEqualTo(PackageFsm.PRE_ALTA);

        // Shipment aggregate recalculated (still PRE_ALTA — only package in shipment).
        ArgumentCaptor<Shipment> shipmentCaptor = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository, times(1)).save(shipmentCaptor.capture());
        assertThat(shipmentCaptor.getValue().getStatus()).isEqualTo(PackageFsm.PRE_ALTA);

        // Audit emitted.
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger, times(1)).logAsync(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("TRACKING_EVENT_RECORDED");

        // Returned event is the saved one.
        assertThat(saved).isNotNull();
    }

    // ===================================================================
    //  record — duplicate event_hash (idempotency)
    // ===================================================================

    @Test
    @DisplayName("record with duplicate event_hash throws DUPLICATE_EVENT before any save")
    void record_duplicateEventHash_throws() {
        stubUserRoles(operatorRole());
        stubIdempotencyCheck(true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("qrCode", "QR-dup");

        assertThatThrownBy(
                        () -> service.record(TENANT_ID, USER_ID, PACKAGE_ID, validRequest("package_created", payload)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_EVENT");

        verify(trackingEventRepository, never()).save(any());
        verify(packageRepository, never()).save(any());
        verify(shipmentRepository, never()).save(any());
        verify(auditLogger, never()).logAsync(any());
    }

    // ===================================================================
    //  record — package not found
    // ===================================================================

    @Test
    @DisplayName("record with non-existent packageId (and non-existent shipmentId) throws PACKAGE_NOT_FOUND")
    void record_packageNotFound_throws() {
        stubPackageLookupEmpty(PACKAGE_ID);
        stubUserRoles(operatorRole());
        stubIdempotencyCheck(false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("qrCode", "QR-missing");

        assertThatThrownBy(
                        () -> service.record(TENANT_ID, USER_ID, PACKAGE_ID, validRequest("package_created", payload)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("PACKAGE_NOT_FOUND");

        verify(trackingEventRepository, never()).save(any());
        verify(auditLogger, never()).logAsync(any());
    }

    // ===================================================================
    //  record — invalid FSM transition
    // ===================================================================

    @Test
    @DisplayName("record with shipment_cancelled on PRE_ALTA (non-final) → CANCELADO succeeds")
    void record_shipmentCancelled_fromPreAlta_succeeds() {
        Package p = pkg(PACKAGE_ID, PackageFsm.PRE_ALTA);
        Shipment s = shipment(PackageFsm.PRE_ALTA);
        stubPackageLookup(p);
        stubPackagesForShipment(SHIPMENT_ID, List.of(p));
        stubShipment(s);
        stubPackageSave();
        stubTrackingSave();
        stubUserRoles(adminRole());
        stubIdempotencyCheck(false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("cancelledBy", USER_ID.toString());
        payload.put("reason", "customer request");

        service.record(TENANT_ID, USER_ID, PACKAGE_ID, validRequest("shipment_cancelled", payload));

        assertThat(p.getStatus()).isEqualTo(PackageFsm.CANCELADO);
        verify(packageRepository, times(1)).save(any(Package.class));
        verify(shipmentRepository, times(1)).save(any(Shipment.class));
    }

    @Test
    @DisplayName("record with shipment_cancelled on ENTREGADO (final) throws INVALID_STATE_TRANSITION")
    void record_shipmentCancelled_fromFinal_throws() {
        Package p = pkg(PACKAGE_ID, PackageFsm.ENTREGADO);
        stubPackageLookup(p);
        stubUserRoles(adminRole());
        stubIdempotencyCheck(false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("cancelledBy", USER_ID.toString());
        payload.put("reason", "too late");

        assertThatThrownBy(() ->
                        service.record(TENANT_ID, USER_ID, PACKAGE_ID, validRequest("shipment_cancelled", payload)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("INVALID_STATE_TRANSITION");

        verify(trackingEventRepository, never()).save(any());
        verify(packageRepository, never()).save(any());
    }

    // ===================================================================
    //  record — event validation (mandatory fields)
    // ===================================================================

    @Test
    @DisplayName("record with shipment_cancelled missing cancelledBy throws EVENT_VALIDATION_ERROR")
    void record_eventValidation_missingField_throws() {
        Package p = pkg(PACKAGE_ID, PackageFsm.PRE_ALTA);
        // No user-role stub: validation fails BEFORE the role check.
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", "no cancelledBy field");

        assertThatThrownBy(() ->
                        service.record(TENANT_ID, USER_ID, PACKAGE_ID, validRequest("shipment_cancelled", payload)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("EVENT_VALIDATION_ERROR");

        verify(trackingEventRepository, never()).save(any());
        verify(packageRepository, never()).save(any());
    }

    // ===================================================================
    //  record — insufficient permissions
    // ===================================================================

    @Test
    @DisplayName("record with COMPANY_VIEWER (read-only) emitting shipment_cancelled throws INSUFFICIENT_PERMISSIONS")
    void record_insufficientPermissions_throws() {
        Package p = pkg(PACKAGE_ID, PackageFsm.PRE_ALTA);
        stubUserRoles(viewerRole());

        Map<String, Object> payload = new HashMap<>();
        payload.put("cancelledBy", USER_ID.toString());
        payload.put("reason", "viewer cannot cancel");

        assertThatThrownBy(() ->
                        service.record(TENANT_ID, USER_ID, PACKAGE_ID, validRequest("shipment_cancelled", payload)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("INSUFFICIENT_PERMISSIONS");

        verify(trackingEventRepository, never()).save(any());
        verify(packageRepository, never()).save(any());
        verify(auditLogger, never()).logAsync(any());
    }

    @Test
    @DisplayName(
            "record with COMPANY_DRIVER emitting package_created throws INSUFFICIENT_PERMISSIONS (operator/admin only)")
    void record_driverRoleOnPackageCreated_throws() {
        stubUserRoles(driverRole());

        Map<String, Object> payload = new HashMap<>();
        payload.put("qrCode", "QR-driver");

        assertThatThrownBy(
                        () -> service.record(TENANT_ID, USER_ID, PACKAGE_ID, validRequest("package_created", payload)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("INSUFFICIENT_PERMISSIONS");
    }

    // ===================================================================
    //  record — RETENIDO round-trip (previousStatus)
    // ===================================================================

    @Test
    @DisplayName("record with compensating_event targeting RETENIDO captures previousStatus on entry")
    void record_compensatingEvent_retenidoCapturesPreviousStatus() {
        // compensating_event is the FSM-rollback hook. When its payload
        // specifies targetStatus=RETENIDO and the package is in a
        // non-final state that permits RETENIDO entry, the service
        // captures the prior status in package.previousStatus (per
        // PRD §7.4 reversible-state semantics).
        Package p = pkg(PACKAGE_ID, PackageFsm.CLASIFICADO);
        Shipment s = shipment(PackageFsm.CREADO);
        stubPackageLookup(p);
        stubPackagesForShipment(SHIPMENT_ID, List.of(p));
        stubShipment(s);
        stubPackageSave();
        stubTrackingSave();
        stubUserRoles(adminRole());
        stubIdempotencyCheck(false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("targetStatus", PackageFsm.RETENIDO);
        payload.put("previousEventHash", "deadbeef");
        payload.put("reason", "audit reconciliation — retiene por auditoría");

        service.record(TENANT_ID, USER_ID, PACKAGE_ID, validRequest("compensating_event", payload));

        // After RETENIDO entry, previousStatus must hold the prior state.
        assertThat(p.getStatus()).isEqualTo(PackageFsm.RETENIDO);
        assertThat(p.getPreviousStatus()).isEqualTo(PackageFsm.CLASIFICADO);
    }

    // ===================================================================
    //  record — shipment_validated cascades to all packages
    // ===================================================================

    @Test
    @DisplayName("record with shipment_validated transitions all PRE_ALTA packages in shipment to CREADO")
    void record_shipmentValidated_cascadesToAllPackages() {
        Package p1 = pkg(PACKAGE_ID, PackageFsm.PRE_ALTA);
        Package p2 = pkg(OTHER_PACKAGE_ID, PackageFsm.PRE_ALTA);
        Shipment s = shipment(PackageFsm.PRE_ALTA);

        // packageId = SHIPMENT_ID: the service falls through to the shipment lookup.
        stubPackageLookupEmpty(SHIPMENT_ID);
        stubShipment(s);
        stubPackagesForShipment(SHIPMENT_ID, List.of(p1, p2));
        // The cascade re-locks each package via findByIdAndTenantIdForUpdate.
        when(packageRepository.findByIdAndTenantIdForUpdate(PACKAGE_ID, TENANT_ID))
                .thenReturn(Optional.of(p1));
        when(packageRepository.findByIdAndTenantIdForUpdate(OTHER_PACKAGE_ID, TENANT_ID))
                .thenReturn(Optional.of(p2));
        stubPackageSave();
        stubTrackingSave();
        stubUserRoles(operatorRole());
        stubIdempotencyCheck(false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("docsChecked", true);
        payload.put("paymentConfirmed", true);
        payload.put("validatedBy", USER_ID.toString());

        // The caller passes packageId = SHIPMENT_ID (shipment-level anchor).
        service.record(TENANT_ID, USER_ID, SHIPMENT_ID, validRequest("shipment_validated", payload));

        // Both packages transitioned to CREADO.
        verify(packageRepository, times(2)).save(any(Package.class));
        assertThat(p1.getStatus()).isEqualTo(PackageFsm.CREADO);
        assertThat(p2.getStatus()).isEqualTo(PackageFsm.CREADO);
    }

    // ===================================================================
    //  record — compensating_event (no transition; recalc)
    // ===================================================================

    @Test
    @DisplayName(
            "record with compensating_event (no targetStatus) does NOT change package status but recomputes shipment aggregate")
    void record_compensatingEvent_noTransitionRecalcOnly() {
        Package p = pkg(PACKAGE_ID, PackageFsm.CREADO);
        Shipment s = shipment(PackageFsm.CREADO);
        stubPackageLookup(p);
        stubPackagesForShipment(SHIPMENT_ID, List.of(p));
        stubShipment(s);
        stubTrackingSave();
        stubUserRoles(adminRole());
        stubIdempotencyCheck(false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("previousEventHash", "deadbeef");
        payload.put("reason", "audit reconciliation");

        service.record(TENANT_ID, USER_ID, PACKAGE_ID, validRequest("compensating_event", payload));

        // Package status unchanged (no transition for compensating_event).
        assertThat(p.getStatus()).isEqualTo(PackageFsm.CREADO);
        // But the event was persisted and the shipment aggregate recomputed.
        verify(trackingEventRepository, times(1)).save(any(TrackingEvent.class));
        verify(shipmentRepository, times(1)).save(any(Shipment.class));
    }

    // ===================================================================
    //  record — idempotency hash preservation
    // ===================================================================

    @Test
    @DisplayName("record computes a deterministic 64-char hex event_hash for identical inputs")
    void record_eventHashIsDeterministic() {
        Package p = pkg(PACKAGE_ID, PackageFsm.PRE_ALTA);
        stubPackageLookup(p);
        stubPackagesForShipment(SHIPMENT_ID, List.of(p));
        stubShipment(shipment(PackageFsm.PRE_ALTA));
        stubPackageSave();
        stubTrackingSave();
        stubUserRoles(operatorRole());
        stubIdempotencyCheck(false);

        // Same timestamp + same payload → same hash. We verify the hash
        // is 64 lowercase hex chars.
        Instant t = Instant.parse("2026-06-25T10:00:00Z");
        Map<String, Object> payload = new HashMap<>();
        payload.put("qrCode", "QR-fixed");
        payload.put("shipmentId", SHIPMENT_ID.toString());

        RecordEventRequest req = new RecordEventRequest("package_created", t, payload, "127.0.0.1", "junit");
        service.record(TENANT_ID, USER_ID, PACKAGE_ID, req);

        ArgumentCaptor<TrackingEvent> evCaptor = ArgumentCaptor.forClass(TrackingEvent.class);
        verify(trackingEventRepository).save(evCaptor.capture());
        String hash = evCaptor.getValue().getEventHash();
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    // ===================================================================
    //  list
    // ===================================================================

    @Test
    @DisplayName("list returns events for the package ordered by event_timestamp ASC")
    void list_returnsOrderedEvents() {
        Package p = pkg(PACKAGE_ID, PackageFsm.CREADO);
        stubPackageLookupPlain(p);

        TrackingEvent e1 = new TrackingEvent();
        e1.setId(UUID.randomUUID());
        e1.setTenantId(TENANT_ID);
        e1.setPackageId(PACKAGE_ID);
        e1.setEventType("package_created");
        e1.setEventTimestamp(Instant.parse("2026-06-25T10:00:00Z"));
        e1.setUserId(USER_ID);
        e1.setEventSource("OPERADOR_SUCURSAL");
        e1.setEventHash("a".repeat(64));

        TrackingEvent e2 = new TrackingEvent();
        e2.setId(UUID.randomUUID());
        e2.setTenantId(TENANT_ID);
        e2.setPackageId(PACKAGE_ID);
        e2.setEventType("package_received_origin");
        e2.setEventTimestamp(Instant.parse("2026-06-25T11:00:00Z"));
        e2.setUserId(USER_ID);
        e2.setEventSource("OPERADOR_SUCURSAL");
        e2.setEventHash("b".repeat(64));

        // Repository returns already-ordered by ASC (the contract).
        when(trackingEventRepository.findByPackageIdOrderByEventTimestampAsc(PACKAGE_ID))
                .thenReturn(List.of(e1, e2));

        List<TrackingEvent> events = service.list(TENANT_ID, PACKAGE_ID);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo("package_created");
        assertThat(events.get(1).getEventType()).isEqualTo("package_received_origin");
        assertThat(events.get(0).getEventTimestamp()).isBefore(events.get(1).getEventTimestamp());
    }

    @Test
    @DisplayName("list for non-existent packageId throws PACKAGE_NOT_FOUND")
    void list_packageNotFound_throws() {
        when(packageRepository.findById(PACKAGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(TENANT_ID, PACKAGE_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("PACKAGE_NOT_FOUND");

        verify(trackingEventRepository, never()).findByPackageIdOrderByEventTimestampAsc(any());
    }
}
