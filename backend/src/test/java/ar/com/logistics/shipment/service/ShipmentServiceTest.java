package ar.com.logistics.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.logistics.common.audit.AuditEvent;
import ar.com.logistics.common.audit.AuditLogger;
import ar.com.logistics.common.exception.BusinessRuleException;
import ar.com.logistics.shipment.domain.Address;
import ar.com.logistics.shipment.domain.Branch;
import ar.com.logistics.shipment.domain.Customer;
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
import ar.com.logistics.shipment.service.ShipmentService.CreatePackageRequest;
import ar.com.logistics.shipment.service.ShipmentService.CreateShipmentRequest;
import ar.com.logistics.shipment.service.ShipmentService.CreateShipmentResponse;
import ar.com.logistics.shipment.service.ShipmentService.ShipmentDetailDto;
import ar.com.logistics.shipment.service.ShipmentService.ShipmentListFilters;
import ar.com.logistics.shipment.service.ShipmentService.UpdateShipmentRequest;
import ar.com.logistics.shipment.tracking.LgstGeneratorService;
import ar.com.logistics.shipment.tracking.ShipmentStatusCalculator;
import ar.com.logistics.tenant.domain.Tenant;
import ar.com.logistics.tenant.domain.TenantStatus;
import ar.com.logistics.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link ShipmentService}. Strict TDD — every test pins a
 * piece of the contract from spec §C (etapa-3-envios, PR-3a Chunk B) before
 * the implementation exists.
 *
 * <p>Contract highlights:
 * <ul>
 *   <li>{@code create} validates tenant ACTIVE status, sender != receiver,
 *       and existence of sender/receiver/deliveryAddress.</li>
 *   <li>On first shipment for a tenant, the service lazy-seeds a PRINCIPAL
 *       branch and STANDARD service level (idempotent on
 *       UNIQUE(tenant_id, code)).</li>
 *   <li>LGST generation delegates to {@link LgstGeneratorService} with the
 *       retry loop built in PR-2.</li>
 *   <li>For each package, the service emits a {@code package_created}
 *       tracking event whose {@code eventHash} comes from
 *       {@link ar.com.logistics.shipment.tracking.EventHashCalculator}.</li>
 *   <li>If {@code validateNow=true}, the shipment is moved from
 *       {@code PRE_ALTA} to the aggregate status computed by
 *       {@link ShipmentStatusCalculator}, and a
 *       {@code shipment_validated} event is emitted.</li>
 *   <li>On success the tenant counter is bumped via
 *       {@link TenantRepository#incrementShipmentCount(UUID)}.</li>
 *   <li>{@code update} is only allowed in {@code PRE_ALTA}.</li>
 *   <li>{@code validate} moves {@code PRE_ALTA -> CREADO}.</li>
 *   <li>{@code cancel} moves any non-final state to {@code CANCELADO} and
 *       throws on terminal states (ENTREGADO, DEVUELTO, CANCELADO).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SENDER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID RECEIVER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID ADDRESS_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID SERVICE_LEVEL_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID SHIPMENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final String LGST_VALID = "LGST-7K2M9XQP";

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private PackageRepository packageRepository;

    @Mock
    private TrackingEventRepository trackingEventRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private ServiceLevelRepository serviceLevelRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private LgstGeneratorService lgstGeneratorService;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private ShipmentService service;

    // -------------------------------------------------------------------
    //  Test fixtures
    // -------------------------------------------------------------------

    private static Tenant activeTenant() {
        return buildTenant(TenantStatus.ACTIVE);
    }

    /**
     * Build a Tenant via the static factory. Tenant has no setters
     * ({@code @Getter} only on the class), and {@code status} is set by
     * the factory to {@code ACTIVE} by default — to test SUSPENDED we
     * need a small helper that uses the package-private {@code id}
     * field. We solve this by going through the factory once for ACTIVE
     * and switching {@code status} via reflection.
     */
    private static Tenant buildTenant(TenantStatus status) {
        Tenant t = Tenant.create(
                "slug",
                "Acme SRL",
                "Acme",
                "20123456789",
                ar.com.logistics.tenant.domain.TaxType.RESPONSABLE_INSCRIPTO,
                "ops@acme.com",
                "+5491155554444",
                "AR",
                "Buenos Aires",
                "CABA",
                "Av Corrientes",
                "1234",
                "5",
                "B",
                "C1043");
        // Tenant.status has no setter — switch via reflection.
        try {
            java.lang.reflect.Field f = Tenant.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(t, status);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return t;
    }

    private static CreateShipmentRequest validCreateRequest(boolean validateNow) {
        List<CreatePackageRequest> pkgs = new ArrayList<>();
        pkgs.add(new CreatePackageRequest(
                new BigDecimal("2.50"),
                new BigDecimal("5000.00"),
                "30x20x10",
                "Laptop Dell",
                new BigDecimal("1500.00"),
                "ARS",
                false,
                BigDecimal.ZERO,
                false,
                false,
                true,
                true,
                "ELECTRONICS"));
        return new CreateShipmentRequest(
                SENDER_ID,
                RECEIVER_ID,
                ADDRESS_ID,
                "NORMAL",
                "ORIGIN",
                "DELIVERY",
                null, // deliveryInstructions
                "PAID",
                pkgs,
                validateNow);
    }

    private static CreatePackageRequest packageRequest() {
        return new CreatePackageRequest(
                new BigDecimal("1.00"),
                new BigDecimal("1000.00"),
                "10x10x10",
                "Documentos",
                new BigDecimal("100.00"),
                "ARS",
                false,
                BigDecimal.ZERO,
                false,
                false,
                true,
                true,
                "DOCS");
    }

    /** Stub the happy-path customer/address lookups. */
    private void stubCustomerLookups_ok() {
        Customer sender = new Customer();
        sender.setId(SENDER_ID);
        sender.setTenantId(TENANT_ID);
        Customer receiver = new Customer();
        receiver.setId(RECEIVER_ID);
        receiver.setTenantId(TENANT_ID);
        Address addr = new Address();
        addr.setId(ADDRESS_ID);
        addr.setTenantId(TENANT_ID);

        when(customerRepository.findById(SENDER_ID)).thenReturn(Optional.of(sender));
        when(customerRepository.findById(RECEIVER_ID)).thenReturn(Optional.of(receiver));
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(addr));
    }

    /** Stub tenant + branches + service levels for the happy path. */
    private void stubLazySeed_ok() {
        // Branches already seeded.
        Branch b = new Branch();
        b.setId(BRANCH_ID);
        b.setTenantId(TENANT_ID);
        b.setCode("PRINCIPAL");
        when(branchRepository.findAll()).thenReturn(List.of(b));

        ServiceLevel sl = new ServiceLevel();
        sl.setId(SERVICE_LEVEL_ID);
        sl.setTenantId(TENANT_ID);
        sl.setCode("STANDARD");
        when(serviceLevelRepository.findAll()).thenReturn(List.of(sl));
    }

    /** Stub the active tenant. */
    private void stubTenant_active() {
        Tenant t = buildTenant(TenantStatus.ACTIVE);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(t));
    }

    /** Stub LGST generation to invoke the predicate and accept on the first attempt. */
    private void stubLgst_firstAttemptSucceeds() {
        when(lgstGeneratorService.generateAndPersist(any())).thenAnswer(inv -> {
            java.util.function.Predicate<String> pred = inv.getArgument(0);
            // The real service invokes pred(candidate) until true. We
            // simulate the first-attempt success path.
            pred.test(LGST_VALID);
            return LGST_VALID;
        });
    }

    /** Stub shipment save to assign an id and timestamps. */
    private void stubShipmentSave() {
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> {
            Shipment s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(SHIPMENT_ID);
            }
            if (s.getCreatedAt() == null) {
                s.setCreatedAt(Instant.now());
            }
            s.setUpdatedAt(Instant.now());
            return s;
        });
    }

    /** Stub package save to assign a random id. */
    private void stubPackageSave() {
        when(packageRepository.save(any(Package.class))).thenAnswer(inv -> {
            Package p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            if (p.getCreatedAt() == null) {
                p.setCreatedAt(Instant.now());
            }
            p.setUpdatedAt(Instant.now());
            return p;
        });
    }

    // ===================================================================
    //  create — happy paths
    // ===================================================================

    @Test
    @DisplayName("create with validateNow=true: status=CREADO, packages saved, events emitted, counter incremented")
    void create_validateNow_true_happyPath() {
        CreateShipmentRequest req = validCreateRequest(true);
        stubTenant_active();
        stubCustomerLookups_ok();
        stubLazySeed_ok();
        stubLgst_firstAttemptSucceeds();
        stubShipmentSave();
        stubPackageSave();

        CreateShipmentResponse response = service.create(TENANT_ID, ADMIN_ID, req);

        assertThat(response.shipment()).isNotNull();
        assertThat(response.shipment().getTrackingId()).isEqualTo(LGST_VALID);
        // validateNow=true moves the aggregate status to CREADO.
        assertThat(response.shipment().getStatus()).isEqualTo("CREADO");
        assertThat(response.packages()).hasSize(1);
        assertThat(response.packages().get(0).getStatus()).isEqualTo("CREADO");
        // QR format: QR-{tracking}-{index}
        assertThat(response.packages().get(0).getQrCode()).isEqualTo("QR-" + LGST_VALID + "-1");

        // 1 package_created event per package + 1 shipment_validated event.
        verify(trackingEventRepository, times(2)).save(any(TrackingEvent.class));

        // Audit SHIPMENT_CREATED was emitted.
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger, times(1)).logAsync(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("SHIPMENT_CREATED");

        // Counter bumped exactly once.
        verify(tenantRepository, times(1)).incrementShipmentCount(TENANT_ID);
    }

    @Test
    @DisplayName("create with validateNow=false: status=PRE_ALTA, no shipment_validated event")
    void create_validateNow_false_preAlta() {
        CreateShipmentRequest req = validCreateRequest(false);
        stubTenant_active();
        stubCustomerLookups_ok();
        stubLazySeed_ok();
        stubLgst_firstAttemptSucceeds();
        stubShipmentSave();
        stubPackageSave();

        CreateShipmentResponse response = service.create(TENANT_ID, ADMIN_ID, req);

        // Initial state is PRE_ALTA (validateNow=false).
        assertThat(response.shipment().getStatus()).isEqualTo("PRE_ALTA");
        // Only 1 package_created event (no shipment_validated).
        verify(trackingEventRepository, times(1)).save(any(TrackingEvent.class));

        // Audit still emitted (SHIPMENT_CREATED).
        verify(auditLogger, times(1)).logAsync(any(AuditEvent.class));
        verify(tenantRepository, times(1)).incrementShipmentCount(TENANT_ID);
    }

    // ===================================================================
    //  create — pre-flight rejections
    // ===================================================================

    @Test
    @DisplayName("create with SUSPENDED tenant throws COMPANY_SUSPENDED before any save")
    void create_suspendedTenant_throws() {
        CreateShipmentRequest req = validCreateRequest(true);
        Tenant t = buildTenant(TenantStatus.SUSPENDED);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.create(TENANT_ID, ADMIN_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("COMPANY_SUSPENDED");

        verify(shipmentRepository, never()).save(any());
        verify(packageRepository, never()).save(any());
        verify(tenantRepository, never()).incrementShipmentCount(any());
    }

    @Test
    @DisplayName("create with sender == receiver throws SENDER_EQUALS_RECEIVER")
    void create_senderEqualsReceiver_throws() {
        // Build request with sender == receiver.
        List<CreatePackageRequest> pkgs = List.of(packageRequest());
        CreateShipmentRequest req = new CreateShipmentRequest(
                SENDER_ID, SENDER_ID, ADDRESS_ID, "NORMAL", "ORIGIN", "DELIVERY", null, "PAID", pkgs, true);

        stubTenant_active();

        assertThatThrownBy(() -> service.create(TENANT_ID, ADMIN_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("SENDER_EQUALS_RECEIVER");

        verify(customerRepository, never()).findById(any());
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("create with non-existent senderId throws CUSTOMER_NOT_FOUND")
    void create_senderNotFound_throws() {
        CreateShipmentRequest req = validCreateRequest(true);
        stubTenant_active();
        when(customerRepository.findById(SENDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(TENANT_ID, ADMIN_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("CUSTOMER_NOT_FOUND");

        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("create with non-existent receiverId throws CUSTOMER_NOT_FOUND")
    void create_receiverNotFound_throws() {
        CreateShipmentRequest req = validCreateRequest(true);
        stubTenant_active();
        Customer sender = new Customer();
        sender.setId(SENDER_ID);
        sender.setTenantId(TENANT_ID);
        when(customerRepository.findById(SENDER_ID)).thenReturn(Optional.of(sender));
        when(customerRepository.findById(RECEIVER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(TENANT_ID, ADMIN_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("CUSTOMER_NOT_FOUND");
    }

    @Test
    @DisplayName("create with non-existent deliveryAddressId throws ADDRESS_NOT_FOUND")
    void create_addressNotFound_throws() {
        CreateShipmentRequest req = validCreateRequest(true);
        stubTenant_active();
        Customer sender = new Customer();
        sender.setId(SENDER_ID);
        sender.setTenantId(TENANT_ID);
        Customer receiver = new Customer();
        receiver.setId(RECEIVER_ID);
        receiver.setTenantId(TENANT_ID);
        when(customerRepository.findById(SENDER_ID)).thenReturn(Optional.of(sender));
        when(customerRepository.findById(RECEIVER_ID)).thenReturn(Optional.of(receiver));
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(TENANT_ID, ADMIN_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("ADDRESS_NOT_FOUND");
    }

    // ===================================================================
    //  create — lazy seed
    // ===================================================================

    @Test
    @DisplayName("create triggers lazy seed when no branches exist for the tenant")
    void create_lazySeed_branchesAndServiceLevels() {
        CreateShipmentRequest req = validCreateRequest(true);
        stubTenant_active();
        stubCustomerLookups_ok();

        // Two-phase mock: first findAll() returns empty (lazy seed
        // triggers), after save() the seeded entity shows up in
        // subsequent findAll() calls (resolveBranchId / resolveServiceLevelId).
        java.util.List<Branch> branchList = new java.util.ArrayList<>();
        java.util.List<ServiceLevel> slList = new java.util.ArrayList<>();
        when(branchRepository.findAll()).thenAnswer(inv -> branchList);
        when(serviceLevelRepository.findAll()).thenAnswer(inv -> slList);
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> {
            Branch b = inv.getArgument(0);
            b.setId(BRANCH_ID);
            branchList.add(b);
            return b;
        });
        when(serviceLevelRepository.save(any(ServiceLevel.class))).thenAnswer(inv -> {
            ServiceLevel sl = inv.getArgument(0);
            sl.setId(SERVICE_LEVEL_ID);
            slList.add(sl);
            return sl;
        });
        stubLgst_firstAttemptSucceeds();
        stubShipmentSave();
        stubPackageSave();

        CreateShipmentResponse response = service.create(TENANT_ID, ADMIN_ID, req);

        // Seed ran once for each entity.
        verify(branchRepository, times(1)).save(any(Branch.class));
        verify(serviceLevelRepository, times(1)).save(any(ServiceLevel.class));
        // Shipment references the seeded IDs.
        assertThat(response.shipment().getOriginBranchId()).isEqualTo(BRANCH_ID);
        assertThat(response.shipment().getServiceLevelId()).isEqualTo(SERVICE_LEVEL_ID);
    }

    // ===================================================================
    //  create — LGST collision
    // ===================================================================

    @Test
    @DisplayName("create with LGST collision retries via LgstGeneratorService.generateAndPersist")
    void create_lgstCollision_retrySucceeds() {
        CreateShipmentRequest req = validCreateRequest(true);
        stubTenant_active();
        stubCustomerLookups_ok();
        stubLazySeed_ok();

        // Simulate: service calls generateAndPersist(predicate); the
        // predicate is exercised by the service. We just verify that
        // generateAndPersist was called exactly once and returned the
        // expected LGST (the retry semantics are owned by the service
        // we mock, not the ShipmentService).
        AtomicInteger callCount = new AtomicInteger();
        when(lgstGeneratorService.generateAndPersist(any())).thenAnswer(inv -> {
            callCount.incrementAndGet();
            return LGST_VALID;
        });
        stubShipmentSave();
        stubPackageSave();

        service.create(TENANT_ID, ADMIN_ID, req);

        assertThat(callCount.get()).isEqualTo(1);
        verify(shipmentRepository, atLeastOnce()).save(any(Shipment.class));
    }

    @Test
    @DisplayName("create surfaces IllegalStateException when LGST generation exhausts retries")
    void create_lgstExhaustion_throws() {
        CreateShipmentRequest req = validCreateRequest(true);
        stubTenant_active();
        stubCustomerLookups_ok();
        stubLazySeed_ok();
        when(lgstGeneratorService.generateAndPersist(any()))
                .thenThrow(new IllegalStateException("LGST generation exhausted after 5 attempts"));

        assertThatThrownBy(() -> service.create(TENANT_ID, ADMIN_ID, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LGST generation exhausted");

        verify(shipmentRepository, never()).save(any());
        verify(tenantRepository, never()).incrementShipmentCount(any());
    }

    // ===================================================================
    //  validate
    // ===================================================================

    @Test
    @DisplayName("validate on PRE_ALTA shipment moves status to CREADO + emits shipment_validated event")
    void validate_preAlta_succeeds() {
        Shipment existing = new Shipment();
        existing.setId(SHIPMENT_ID);
        existing.setTenantId(TENANT_ID);
        existing.setTrackingId(LGST_VALID);
        existing.setStatus("PRE_ALTA");
        when(shipmentRepository.findById(SHIPMENT_ID)).thenReturn(Optional.of(existing));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(packageRepository.findAll()).thenReturn(List.of());
        when(trackingEventRepository.save(any(TrackingEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        ShipmentDetailDto dto = service.validate(TENANT_ID, ADMIN_ID, SHIPMENT_ID);

        assertThat(dto.shipment().getStatus()).isEqualTo("CREADO");
        verify(trackingEventRepository, times(1)).save(any(TrackingEvent.class));

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger, times(1)).logAsync(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("SHIPMENT_VALIDATED");
    }

    @Test
    @DisplayName("validate on already-CREADO shipment throws INVALID_STATE_TRANSITION")
    void validate_alreadyCreado_throws() {
        Shipment existing = new Shipment();
        existing.setId(SHIPMENT_ID);
        existing.setTenantId(TENANT_ID);
        existing.setStatus("CREADO");
        when(shipmentRepository.findById(SHIPMENT_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.validate(TENANT_ID, ADMIN_ID, SHIPMENT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("INVALID_STATE_TRANSITION");

        verify(shipmentRepository, never()).save(any());
    }

    // ===================================================================
    //  cancel
    // ===================================================================

    @Test
    @DisplayName("cancel on non-final state moves shipment + all packages to CANCELADO + emits event + audit")
    void cancel_nonFinal_succeeds() {
        Shipment existing = new Shipment();
        existing.setId(SHIPMENT_ID);
        existing.setTenantId(TENANT_ID);
        existing.setTrackingId(LGST_VALID);
        existing.setStatus("CREADO");

        Package pkg = new Package();
        pkg.setId(UUID.randomUUID());
        pkg.setShipmentId(SHIPMENT_ID);
        pkg.setStatus("CREADO");
        pkg.setTenantId(TENANT_ID);
        pkg.setQrCode("QR-" + LGST_VALID + "-1");

        when(shipmentRepository.findById(SHIPMENT_ID)).thenReturn(Optional.of(existing));
        when(packageRepository.findAll()).thenReturn(List.of(pkg));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(packageRepository.save(any(Package.class))).thenAnswer(inv -> inv.getArgument(0));
        when(trackingEventRepository.save(any(TrackingEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        ShipmentDetailDto dto = service.cancel(TENANT_ID, ADMIN_ID, SHIPMENT_ID, "customer requested");

        assertThat(dto.shipment().getStatus()).isEqualTo("CANCELADO");
        assertThat(dto.packages()).hasSize(1);
        assertThat(dto.packages().get(0).getStatus()).isEqualTo("CANCELADO");

        verify(trackingEventRepository, atLeastOnce()).save(any(TrackingEvent.class));

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger, times(1)).logAsync(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("SHIPMENT_CANCELLED");
    }

    @Test
    @DisplayName("cancel on ENTREGADO (terminal) throws INVALID_STATE_TRANSITION")
    void cancel_terminal_throws() {
        Shipment existing = new Shipment();
        existing.setId(SHIPMENT_ID);
        existing.setTenantId(TENANT_ID);
        existing.setStatus("ENTREGADO");
        when(shipmentRepository.findById(SHIPMENT_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.cancel(TENANT_ID, ADMIN_ID, SHIPMENT_ID, "late cancel"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("INVALID_STATE_TRANSITION");

        verify(shipmentRepository, never()).save(any());
    }

    // ===================================================================
    //  update
    // ===================================================================

    @Test
    @DisplayName("update on PRE_ALTA applies the supplied field changes + emits SHIPMENT_UPDATED audit")
    void update_preAlta_succeeds() {
        Shipment existing = new Shipment();
        existing.setId(SHIPMENT_ID);
        existing.setTenantId(TENANT_ID);
        existing.setStatus("PRE_ALTA");
        existing.setDeliveryInstructions("Original instructions");
        when(shipmentRepository.findById(SHIPMENT_ID)).thenReturn(Optional.of(existing));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateShipmentRequest req = new UpdateShipmentRequest("Updated instructions", null, null, null);

        Shipment updated = service.update(TENANT_ID, ADMIN_ID, SHIPMENT_ID, req);

        assertThat(updated.getDeliveryInstructions()).isEqualTo("Updated instructions");

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger, times(1)).logAsync(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("SHIPMENT_UPDATED");
    }

    @Test
    @DisplayName("update on CREADO throws INVALID_STATE_TRANSITION (only PRE_ALTA is editable)")
    void update_creado_throws() {
        Shipment existing = new Shipment();
        existing.setId(SHIPMENT_ID);
        existing.setTenantId(TENANT_ID);
        existing.setStatus("CREADO");
        when(shipmentRepository.findById(SHIPMENT_ID)).thenReturn(Optional.of(existing));

        UpdateShipmentRequest req = new UpdateShipmentRequest("X", null, null, null);

        assertThatThrownBy(() -> service.update(TENANT_ID, ADMIN_ID, SHIPMENT_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("INVALID_STATE_TRANSITION");

        verify(shipmentRepository, never()).save(any());
    }

    // ===================================================================
    //  list
    // ===================================================================

    @Test
    @DisplayName("list returns tenant-scoped shipments honoring the supplied filters")
    void list_returnsPage() {
        Shipment s1 = new Shipment();
        s1.setId(SHIPMENT_ID);
        s1.setTenantId(TENANT_ID);
        s1.setTrackingId(LGST_VALID);
        s1.setStatus("CREADO");
        when(shipmentRepository.findAll()).thenReturn(List.of(s1));

        Pageable pageable = PageRequest.of(0, 20);
        Page<Shipment> page = service.list(TENANT_ID, new ShipmentListFilters("CREADO", null, null, null), pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTrackingId()).isEqualTo(LGST_VALID);
    }
}
