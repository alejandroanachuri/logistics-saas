package ar.com.logistics.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
import ar.com.logistics.shipment.service.PublicTrackService.PublicTrackResponse;
import ar.com.logistics.shipment.tracking.ShipmentStatusCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link PublicTrackService}. Strict TDD — every test pins a piece
 * of the public-tracking contract (PRD §9) before the implementation exists.
 *
 * <p>Contract highlights (mirrors the class javadoc):
 * <ul>
 *   <li>{@code get(trackingId)} is the only public method. It looks up a
 *       shipment across tenants (BYPASSRLS), builds the customer-facing
 *       message per PRD §9.4, and assembles a public-safe response.</li>
 *   <li>Missing tracking id → {@code TRACKING_NOT_FOUND}.</li>
 *   <li>Invalid LGST format → same {@code TRACKING_NOT_FOUND} (the lookup
 *       simply misses).</li>
 *   <li>Response MUST contain ONLY public fields (PRD §9.3). The
 *       {@code responseDoesNotLeakInternalFields} test pins this.</li>
 *   <li>Timeline is filtered by the visibility whitelist per PRD §9.5.</li>
 * </ul>
 *
 * <p>Test isolation: every dependency is a Mockito mock. The service
 * has no Spring context (no {@code @DataJpaTest}, no {@code @SpringBootTest}).
 */
@ExtendWith(MockitoExtension.class)
class PublicTrackServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID SHIPMENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID PACKAGE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID RECEIVER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final String LGST = "LGST-7K2M9XQP";

    @Mock
    private ShipmentAdminRepository shipmentAdminRepository;

    @Mock
    private PackageAdminRepository packageAdminRepository;

    @Mock
    private CustomerAdminRepository customerAdminRepository;

    @Mock
    private TrackingEventAdminRepository trackingEventAdminRepository;

    @InjectMocks
    private PublicTrackService service;

    // -------------------------------------------------------------------
    //  Fixtures
    // -------------------------------------------------------------------

    private static Shipment shipment(String status) {
        Shipment s = new Shipment();
        s.setId(SHIPMENT_ID);
        s.setTenantId(TENANT_ID);
        s.setTrackingId(LGST);
        s.setShipmentType("NORMAL");
        s.setSenderId(UUID.randomUUID());
        s.setReceiverId(RECEIVER_ID);
        s.setDeliveryAddressId(UUID.randomUUID());
        s.setOriginBranchId(UUID.randomUUID());
        s.setDestinationBranchId(UUID.randomUUID());
        s.setServiceLevelId(UUID.randomUUID());
        s.setPaymentType("PAGO_ORIGEN");
        s.setDeliveryMode("DOMICILIO");
        s.setStatus(status);
        s.setSlaStatus("ON_TIME");
        s.setTotalWeightKg(new BigDecimal("2.50"));
        s.setCreatedBy(UUID.randomUUID());
        s.setCreatedAt(Instant.parse("2026-06-25T09:00:00Z"));
        s.setUpdatedAt(Instant.parse("2026-06-25T09:00:00Z"));
        return s;
    }

    private static Package pkg(UUID id, String status, BigDecimal weight) {
        Package p = new Package();
        p.setId(id);
        p.setTenantId(TENANT_ID);
        p.setShipmentId(SHIPMENT_ID);
        p.setQrCode("QR-" + id);
        p.setStatus(status);
        p.setWeightKg(weight);
        p.setContentDescription("content");
        p.setDeclaredCurrency("ARS");
        p.setCategory("GENERAL");
        return p;
    }

    private static Customer customer(String firstName, String lastName) {
        Customer c = new Customer();
        c.setId(RECEIVER_ID);
        c.setTenantId(TENANT_ID);
        c.setPersonType("FISICA");
        c.setFirstName(firstName);
        c.setLastName(lastName);
        c.setTaxCondition("CONSUMIDOR_FINAL");
        c.setPhone("+5491155550000");
        c.setDataConsent(true);
        c.setConsentDate(Instant.parse("2026-01-01T00:00:00Z"));
        return c;
    }

    private static TrackingEvent event(UUID id, UUID packageId, String eventType, Instant ts) {
        TrackingEvent e = new TrackingEvent();
        e.setId(id);
        e.setTenantId(TENANT_ID);
        e.setPackageId(packageId);
        e.setEventType(eventType);
        e.setEventTimestamp(ts);
        e.setUserId(UUID.randomUUID());
        e.setEventSource("OPERADOR_SUCURSAL");
        e.setEventHash("hash-" + id);
        e.setCreatedAt(ts);
        return e;
    }

    /** Wire the happy-path mocks so individual tests can override. */
    private void stubHappyPath(Shipment s, List<Package> packages, Customer c, List<TrackingEvent> events) {
        when(shipmentAdminRepository.findByTrackingId(LGST)).thenReturn(Optional.of(s));
        when(packageAdminRepository.findByShipmentId(SHIPMENT_ID)).thenReturn(packages);
        when(customerAdminRepository.findById(RECEIVER_ID)).thenReturn(Optional.of(c));
        when(trackingEventAdminRepository.findByPackageIdOrderByEventTimestampAsc(PACKAGE_ID))
                .thenReturn(events);
    }

    // ===================================================================
    //  Happy path
    // ===================================================================

    @Test
    @DisplayName("get(trackingId) returns PublicTrackResponse with all public fields populated")
    void get_happyPath_returnsFullResponse() {
        Shipment s = shipment(PackageFsm.CREADO);
        Package p1 = pkg(PACKAGE_ID, PackageFsm.CREADO, new BigDecimal("1.25"));
        Customer c = customer("Juan", "García");
        TrackingEvent ev =
                event(UUID.randomUUID(), PACKAGE_ID, "package_created", Instant.parse("2026-06-25T09:00:00Z"));
        stubHappyPath(s, List.of(p1), c, List.of(ev));

        PublicTrackResponse resp = service.get(LGST);

        assertThat(resp.trackingId()).isEqualTo(LGST);
        assertThat(resp.status()).isEqualTo(PackageFsm.CREADO);
        assertThat(resp.statusMessage()).isEqualTo("Envío registrado");
        assertThat(resp.isPartial()).isFalse();
        assertThat(resp.packageCount()).isEqualTo(1);
        assertThat(resp.totalWeightKg()).isEqualByComparingTo("1.25");
        assertThat(resp.receiverName()).isEqualTo("Juan G.");
        assertThat(resp.timeline()).hasSize(1);
        assertThat(resp.timeline().get(0).message()).isEqualTo("Envío registrado");
        assertThat(resp.timeline().get(0).timestamp()).isEqualTo(Instant.parse("2026-06-25T09:00:00Z"));
    }

    // ===================================================================
    //  Not found
    // ===================================================================

    @Test
    @DisplayName("get with non-existent tracking id throws TRACKING_NOT_FOUND")
    void get_unknownTrackingId_throws() {
        when(shipmentAdminRepository.findByTrackingId("LGST-AAAAAAAA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("LGST-AAAAAAAA"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("TRACKING_NOT_FOUND");
    }

    @Test
    @DisplayName("get with garbage (non-LGST) tracking id throws TRACKING_NOT_FOUND (DB miss)")
    void get_garbageTrackingId_throws() {
        // The repository simply returns empty — the service treats any
        // miss as TRACKING_NOT_FOUND without leaking why.
        when(shipmentAdminRepository.findByTrackingId("INVALID-123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("INVALID-123"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("TRACKING_NOT_FOUND");
    }

    // ===================================================================
    //  Status message mapping (PRD §9.4)
    // ===================================================================

    @Test
    @DisplayName("ENTREGADO_PARCIAL shipment: statusMessage='Entregado parcialmente', isPartial=true")
    void get_entregadoParcial_mapsCorrectly() {
        Shipment s = shipment(ShipmentStatusCalculator.STATUS_ENTREGADO_PARCIAL);
        Package p1 = pkg(PACKAGE_ID, PackageFsm.ENTREGADO, new BigDecimal("1.00"));
        Customer c = customer("Ana", "Pérez");
        TrackingEvent ev =
                event(UUID.randomUUID(), PACKAGE_ID, "package_created", Instant.parse("2026-06-25T09:00:00Z"));
        stubHappyPath(s, List.of(p1), c, List.of(ev));

        PublicTrackResponse resp = service.get(LGST);

        assertThat(resp.status()).isEqualTo(ShipmentStatusCalculator.STATUS_ENTREGADO_PARCIAL);
        assertThat(resp.statusMessage()).isEqualTo("Entregado parcialmente");
        assertThat(resp.isPartial()).isTrue();
    }

    @Test
    @DisplayName("ENTREGADO shipment: statusMessage='Entregado', isPartial=false")
    void get_entregado_mapsCorrectly() {
        Shipment s = shipment(PackageFsm.ENTREGADO);
        Package p1 = pkg(PACKAGE_ID, PackageFsm.ENTREGADO, new BigDecimal("1.00"));
        Customer c = customer("Ana", "Pérez");
        TrackingEvent ev =
                event(UUID.randomUUID(), PACKAGE_ID, "package_created", Instant.parse("2026-06-25T09:00:00Z"));
        stubHappyPath(s, List.of(p1), c, List.of(ev));

        PublicTrackResponse resp = service.get(LGST);

        assertThat(resp.status()).isEqualTo(PackageFsm.ENTREGADO);
        assertThat(resp.statusMessage()).isEqualTo("Entregado");
        assertThat(resp.isPartial()).isFalse();
    }

    @Test
    @DisplayName("CANCELADO shipment: statusMessage='Cancelado'")
    void get_cancelado_mapsCorrectly() {
        Shipment s = shipment(PackageFsm.CANCELADO);
        Package p1 = pkg(PACKAGE_ID, PackageFsm.CANCELADO, new BigDecimal("1.00"));
        Customer c = customer("Ana", "Pérez");
        TrackingEvent ev =
                event(UUID.randomUUID(), PACKAGE_ID, "shipment_rejected", Instant.parse("2026-06-25T09:30:00Z"));
        stubHappyPath(s, List.of(p1), c, List.of(ev));

        PublicTrackResponse resp = service.get(LGST);

        assertThat(resp.status()).isEqualTo(PackageFsm.CANCELADO);
        assertThat(resp.statusMessage()).isEqualTo("Cancelado");
    }

    // ===================================================================
    //  Timeline visibility (PRD §9.5)
    // ===================================================================

    @Test
    @DisplayName("Timeline includes package_created events")
    void get_timelineIncludesPackageCreated() {
        Shipment s = shipment(PackageFsm.CREADO);
        Package p1 = pkg(PACKAGE_ID, PackageFsm.CREADO, new BigDecimal("1.00"));
        Customer c = customer("Ana", "Pérez");
        TrackingEvent ev1 =
                event(UUID.randomUUID(), PACKAGE_ID, "package_created", Instant.parse("2026-06-25T09:00:00Z"));
        stubHappyPath(s, List.of(p1), c, List.of(ev1));

        PublicTrackResponse resp = service.get(LGST);

        assertThat(resp.timeline()).hasSize(1);
        assertThat(resp.timeline().get(0).message()).isEqualTo("Envío registrado");
    }

    @Test
    @DisplayName("Timeline excludes events not in the visibility whitelist (PRD §9.5)")
    void get_timelineExcludesNonWhitelisted() {
        Shipment s = shipment(PackageFsm.CREADO);
        Package p1 = pkg(PACKAGE_ID, PackageFsm.CREADO, new BigDecimal("1.00"));
        Customer c = customer("Ana", "Pérez");

        TrackingEvent allowed =
                event(UUID.randomUUID(), PACKAGE_ID, "package_created", Instant.parse("2026-06-25T09:00:00Z"));
        TrackingEvent forbidden1 = event(
                UUID.randomUUID(),
                PACKAGE_ID,
                "shipment_validated",
                Instant.parse("2026-06-25T09:10:00Z")); // internal event — NOT in PRD §9.5
        TrackingEvent forbidden2 = event(
                UUID.randomUUID(),
                PACKAGE_ID,
                "compensating_event",
                Instant.parse("2026-06-25T09:20:00Z")); // internal event — NOT in PRD §9.5
        // Repository returns ALL events (it does not filter); the service
        // is the boundary that applies the whitelist.
        stubHappyPath(s, List.of(p1), c, List.of(allowed, forbidden1, forbidden2));

        PublicTrackResponse resp = service.get(LGST);

        assertThat(resp.timeline()).hasSize(1);
        assertThat(resp.timeline().get(0).message()).isEqualTo("Envío registrado");
    }

    @Test
    @DisplayName("Timeline includes shipment_rejected when status is CANCELADO")
    void get_timelineIncludesRejectedWhenCancelled() {
        Shipment s = shipment(PackageFsm.CANCELADO);
        Package p1 = pkg(PACKAGE_ID, PackageFsm.CANCELADO, new BigDecimal("1.00"));
        Customer c = customer("Ana", "Pérez");
        TrackingEvent rejected =
                event(UUID.randomUUID(), PACKAGE_ID, "shipment_rejected", Instant.parse("2026-06-25T09:30:00Z"));
        stubHappyPath(s, List.of(p1), c, List.of(rejected));

        PublicTrackResponse resp = service.get(LGST);

        // shipment_rejected is in the whitelist and status is CANCELADO,
        // so the message comes through.
        assertThat(resp.timeline()).hasSize(1);
        assertThat(resp.timeline().get(0).message()).isEqualTo("Cancelado");
    }

    // ===================================================================
    //  Negative: response MUST NOT leak internal fields (PRD §9.3)
    // ===================================================================

    @Test
    @DisplayName("get response MUST NOT contain tenantId, customerId, packageId, branchId, paymentType, dni, cuit")
    void get_responseDoesNotLeakInternalFields() throws Exception {
        Shipment s = shipment(PackageFsm.CREADO);
        Package p1 = pkg(PACKAGE_ID, PackageFsm.CREADO, new BigDecimal("1.00"));
        // Stuff the customer with extra fields to make sure the service
        // never projects them through.
        Customer c = customer("Juan", "García");
        c.setDni("12345678");
        c.setCuitCuil("20123456780");
        c.setNotes("notas internas del operador");
        TrackingEvent ev =
                event(UUID.randomUUID(), PACKAGE_ID, "package_created", Instant.parse("2026-06-25T09:00:00Z"));
        stubHappyPath(s, List.of(p1), c, List.of(ev));

        PublicTrackResponse resp = service.get(LGST);

        // Serialize and check the JSON tree — this is the wire shape the
        // public client will see. Disable the REQUIRE_HANDLERS_FOR_JAVA8_TIMES
        // mapper feature so we don't need the JSR-310 module for this one-off
        // assertion; the test isn't about the timestamp encoding, it's about
        // the field-name leakage.
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        m.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m.disable(com.fasterxml.jackson.databind.MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_TIMES);
        String json = m.writeValueAsString(resp);

        assertThat(json)
                .doesNotContain("tenantId")
                .doesNotContain("customerId")
                .doesNotContain("packageId")
                .doesNotContain("branchId")
                .doesNotContain("paymentType")
                .doesNotContain("dni")
                .doesNotContain("cuit")
                .doesNotContain("12345678")
                .doesNotContain("20123456780")
                .doesNotContain("notas internas");

        // Also check the record's accessor surface — Jackson could only
        // leak fields that exist on the record, so a missing accessor is
        // a hard guarantee.
        assertThat(resp.getClass().getRecordComponents())
                .extracting("name", String.class)
                .containsExactlyInAnyOrder(
                        "trackingId",
                        "status",
                        "statusMessage",
                        "isPartial",
                        "packageCount",
                        "totalWeightKg",
                        "receiverName",
                        "timeline");
    }

    // ===================================================================
    //  Receiver name formatting
    // ===================================================================

    @Test
    @DisplayName("Receiver name format: firstName + lastName[0] + '.' (e.g. 'Juan G.')")
    void get_receiverNameFirstInitialOfLastName() {
        Shipment s = shipment(PackageFsm.CREADO);
        Package p1 = pkg(PACKAGE_ID, PackageFsm.CREADO, new BigDecimal("1.00"));
        Customer c = customer("Lucía", "Martínez");
        TrackingEvent ev =
                event(UUID.randomUUID(), PACKAGE_ID, "package_created", Instant.parse("2026-06-25T09:00:00Z"));
        stubHappyPath(s, List.of(p1), c, List.of(ev));

        PublicTrackResponse resp = service.get(LGST);

        assertThat(resp.receiverName()).isEqualTo("Lucía M.");
    }

    @Test
    @DisplayName("Receiver name with no lastName falls back to firstName only")
    void get_receiverNameNoLastName() {
        Shipment s = shipment(PackageFsm.CREADO);
        Package p1 = pkg(PACKAGE_ID, PackageFsm.CREADO, new BigDecimal("1.00"));
        Customer c = customer("Pedro", null);
        TrackingEvent ev =
                event(UUID.randomUUID(), PACKAGE_ID, "package_created", Instant.parse("2026-06-25T09:00:00Z"));
        stubHappyPath(s, List.of(p1), c, List.of(ev));

        PublicTrackResponse resp = service.get(LGST);

        assertThat(resp.receiverName()).isEqualTo("Pedro");
    }

    // ===================================================================
    //  Weight aggregation
    // ===================================================================

    @Test
    @DisplayName("totalWeightKg sums every package's weightKg")
    void get_totalWeightIsSumOfPackages() {
        Shipment s = shipment(PackageFsm.CREADO);
        Package p1 = pkg(UUID.randomUUID(), PackageFsm.CREADO, new BigDecimal("1.25"));
        Package p2 = pkg(UUID.randomUUID(), PackageFsm.CREADO, new BigDecimal("3.75"));
        Customer c = customer("Ana", "Pérez");
        // Use the FIRST package's id for the timeline anchor (the
        // service picks packages.get(0).getId()).
        TrackingEvent ev =
                event(UUID.randomUUID(), p1.getId(), "package_created", Instant.parse("2026-06-25T09:00:00Z"));
        when(shipmentAdminRepository.findByTrackingId(LGST)).thenReturn(Optional.of(s));
        when(packageAdminRepository.findByShipmentId(SHIPMENT_ID)).thenReturn(List.of(p1, p2));
        when(customerAdminRepository.findById(RECEIVER_ID)).thenReturn(Optional.of(c));
        when(trackingEventAdminRepository.findByPackageIdOrderByEventTimestampAsc(p1.getId()))
                .thenReturn(List.of(ev));

        PublicTrackResponse resp = service.get(LGST);

        assertThat(resp.packageCount()).isEqualTo(2);
        assertThat(resp.totalWeightKg()).isEqualByComparingTo("5.00");
    }
}
