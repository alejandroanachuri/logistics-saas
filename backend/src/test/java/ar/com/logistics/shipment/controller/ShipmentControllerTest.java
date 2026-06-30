package ar.com.logistics.shipment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.com.logistics.common.exception.BusinessRuleException;
import ar.com.logistics.common.exception.GlobalExceptionHandler;
import ar.com.logistics.shipment.domain.Package;
import ar.com.logistics.shipment.domain.Shipment;
import ar.com.logistics.shipment.domain.TrackingEvent;
import ar.com.logistics.shipment.repository.company.AddressRepository;
import ar.com.logistics.shipment.repository.company.BranchRepository;
import ar.com.logistics.shipment.repository.company.CustomerRepository;
import ar.com.logistics.shipment.repository.company.ServiceLevelRepository;
import ar.com.logistics.shipment.service.ShipmentService;
import ar.com.logistics.shipment.service.ShipmentService.CreateShipmentRequest;
import ar.com.logistics.shipment.service.ShipmentService.CreateShipmentResponse;
import ar.com.logistics.shipment.service.ShipmentService.ShipmentDetailDto;
import ar.com.logistics.shipment.service.ShipmentService.ShipmentListFilters;
import ar.com.logistics.tenant.TenantContext;
import ar.com.logistics.tenant.TenantContextEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller tests for {@code /api/v1/shipments/**} (PR-4 Chunk B
 * Part 2). Eight endpoints, role-gated by {@code @PreAuthorize}
 * (ADMIN+OPERATOR for create/update/validate/reject; ADMIN only for
 * cancel; ADMIN+OPERATOR+VIEWER for list/get/timeline).
 *
 * <p>Uses {@code MockMvcBuilders.standaloneSetup} — the project
 * convention from {@code CompanyUsersControllerTest}. Security
 * enforcement is covered by the IT suite; here we focus on
 * controller wiring + service delegation + JSON shape.
 */
@ExtendWith(MockitoExtension.class)
class ShipmentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ShipmentService shipmentService;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private ServiceLevelRepository serviceLevelRepository;

    @InjectMocks
    private ShipmentController controller;

    private UUID tenantId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        tenantId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        TenantContext.set(tenantId, TenantContextEntry.Scope.COMPANY);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        adminId,
                        "n/a",
                        List.of(
                                new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"),
                                new SimpleGrantedAuthority("SCOPE_COMPANY"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/v1/shipments: returns 201 with the create response (shipment + packages)")
    void create_returns201WithResponse() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        UUID pkgId = UUID.randomUUID();
        Shipment s = buildShipment(shipmentId, "PRE_ALTA");
        Package p = buildPackage(pkgId, shipmentId, "CREADO");
        CreateShipmentResponse serviceResp = new CreateShipmentResponse(s, List.of(p));

        when(shipmentService.create(eq(tenantId), eq(adminId), any(CreateShipmentRequest.class)))
                .thenReturn(serviceResp);

        Map<String, Object> reqBody = Map.of(
                "senderId",
                UUID.randomUUID().toString(),
                "receiverId",
                UUID.randomUUID().toString(),
                "deliveryAddressId",
                UUID.randomUUID().toString(),
                "shipmentType",
                "NACIONAL",
                "deliveryMode",
                "DOMICILIO",
                "paymentType",
                "ORIGEN",
                "validateNow",
                false,
                "packages",
                List.of(Map.of(
                        "weightKg", "1.50",
                        "contentDescription", "books",
                        "category", "GENERAL",
                        "hasInsurance", false,
                        "isFragile", false,
                        "isUrgent", false,
                        "requiresSignature", true,
                        "requiresIdCheck", false)));

        mockMvc.perform(post("/api/v1/shipments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(reqBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipment.id").value(shipmentId.toString()))
                .andExpect(jsonPath("$.shipment.status").value("PRE_ALTA"))
                .andExpect(jsonPath("$.packages.length()").value(1))
                .andExpect(jsonPath("$.packages[0].id").value(pkgId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/shipments: returns 400 when service raises a validation error")
    void create_returns400OnMissingField() throws Exception {
        when(shipmentService.create(eq(tenantId), eq(adminId), any(CreateShipmentRequest.class)))
                .thenThrow(new BusinessRuleException("VALIDATION_ERROR", Map.of("field", "senderId")));

        // Missing senderId + receiverId — service surfaces the validation
        // error which GlobalExceptionHandler maps to 400.
        Map<String, Object> reqBody = Map.of(
                "deliveryAddressId",
                UUID.randomUUID().toString(),
                "shipmentType",
                "NACIONAL",
                "deliveryMode",
                "DOMICILIO",
                "paymentType",
                "ORIGEN",
                "validateNow",
                false,
                "packages",
                List.of());

        mockMvc.perform(post("/api/v1/shipments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(reqBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/shipments: returns 200 with PageResponse<ShipmentSummaryDto>")
    void list_returnsPageResponse() throws Exception {
        UUID id = UUID.randomUUID();
        Shipment s = buildShipment(id, "CREADO");
        Page<Shipment> page = new PageImpl<>(List.of(s), PageRequest.of(0, 20), 1);

        when(shipmentService.list(eq(tenantId), any(ShipmentListFilters.class), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/shipments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data[0].status").value("CREADO"));
    }

    @Test
    @DisplayName("GET /api/v1/shipments/{id}: returns 200 with ShipmentDetailDto")
    void get_returnsDetail() throws Exception {
        UUID id = UUID.randomUUID();
        Shipment s = buildShipment(id, "CREADO");
        Package p = buildPackage(UUID.randomUUID(), id, "CREADO");
        TrackingEvent ev = new TrackingEvent();
        ev.setId(UUID.randomUUID());
        ev.setPackageId(p.getId());
        ev.setEventType("package_created");
        ev.setEventTimestamp(Instant.parse("2026-01-15T10:00:00Z"));
        ShipmentDetailDto detail = new ShipmentDetailDto(s, List.of(p), ev);

        when(shipmentService.get(tenantId, id)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/shipments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.id").value(id.toString()))
                .andExpect(jsonPath("$.shipment.packages.length()").value(1))
                .andExpect(jsonPath("$.shipment.latestEvent.eventType").value("package_created"));
    }

    @Test
    @DisplayName("POST /api/v1/shipments/{id}/validate: returns 200 with the validated detail")
    void validate_returns200WithDetail() throws Exception {
        UUID id = UUID.randomUUID();
        Shipment s = buildShipment(id, "CREADO");
        Package p = buildPackage(UUID.randomUUID(), id, "CREADO");
        TrackingEvent ev = new TrackingEvent();
        ev.setId(UUID.randomUUID());
        ev.setPackageId(id);
        ev.setEventType("shipment_validated");
        ev.setEventTimestamp(Instant.parse("2026-01-15T11:00:00Z"));
        ShipmentDetailDto detail = new ShipmentDetailDto(s, List.of(p), ev);

        when(shipmentService.validate(tenantId, adminId, id)).thenReturn(detail);

        mockMvc.perform(post("/api/v1/shipments/" + id + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.status").value("CREADO"))
                .andExpect(jsonPath("$.shipment.latestEvent.eventType").value("shipment_validated"));
    }

    // -------------------------------------------------------------------
    //  helpers
    // -------------------------------------------------------------------

    private Shipment buildShipment(UUID id, String status) {
        Shipment s = new Shipment();
        s.setId(id);
        s.setTenantId(tenantId);
        s.setTrackingId("LGST-TEST0001");
        s.setShipmentType("NACIONAL");
        s.setSenderId(UUID.randomUUID());
        s.setReceiverId(UUID.randomUUID());
        s.setDeliveryAddressId(UUID.randomUUID());
        s.setOriginBranchId(UUID.randomUUID());
        s.setDestinationBranchId(UUID.randomUUID());
        s.setServiceLevelId(UUID.randomUUID());
        s.setPaymentType("ORIGEN");
        s.setDeliveryMode("DOMICILIO");
        s.setStatus(status);
        s.setSlaStatus("ON_TIME");
        s.setCreatedBy(adminId);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        return s;
    }

    private Package buildPackage(UUID id, UUID shipmentId, String status) {
        Package p = new Package();
        p.setId(id);
        p.setTenantId(tenantId);
        p.setShipmentId(shipmentId);
        p.setQrCode("QR-LGST-TEST0001-1");
        p.setStatus(status);
        p.setWeightKg(new BigDecimal("1.50"));
        p.setContentDescription("books");
        p.setDeclaredCurrency("ARS");
        p.setCategory("GENERAL");
        p.setHasInsurance(false);
        p.setFragile(false);
        p.setUrgent(false);
        p.setRequiresSignature(true);
        p.setRequiresIdCheck(false);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return p;
    }
}
