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
import ar.com.logistics.shipment.domain.TrackingEvent;
import ar.com.logistics.shipment.service.TrackingEventService;
import ar.com.logistics.shipment.service.TrackingEventService.RecordEventRequest;
import ar.com.logistics.tenant.TenantContext;
import ar.com.logistics.tenant.TenantContextEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller tests for {@code /api/v1/packages/{packageId}/events/**}
 * (PR-4 Chunk B Part 2). Two endpoints, role-gated by
 * {@code @PreAuthorize} (ADMIN+OPERATOR for record; ADMIN+OPERATOR+
 * VIEWER for list).
 *
 * <p>Uses {@code MockMvcBuilders.standaloneSetup} — the project
 * convention from {@code CompanyUsersControllerTest}. Security
 * enforcement is covered by the IT suite; here we focus on
 * controller wiring + service delegation + JSON shape.
 */
@ExtendWith(MockitoExtension.class)
class TrackingEventControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TrackingEventService trackingEventService;

    @InjectMocks
    private TrackingEventController controller;

    private UUID tenantId;
    private UUID adminId;
    private UUID packageId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        tenantId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        packageId = UUID.randomUUID();
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
    @DisplayName("POST /api/v1/packages/{id}/events: returns 201 with TrackingEventDto")
    void record_returns201WithEvent() throws Exception {
        TrackingEvent saved = new TrackingEvent();
        saved.setId(UUID.randomUUID());
        saved.setTenantId(tenantId);
        saved.setPackageId(packageId);
        saved.setEventType("package_cancelled");
        saved.setEventTimestamp(Instant.parse("2026-01-15T10:00:00Z"));
        saved.setUserId(adminId);
        saved.setEventSource("OPERADOR_SUCURSAL");
        saved.setEventHash("abc123");

        when(trackingEventService.record(eq(tenantId), eq(adminId), eq(packageId), any(RecordEventRequest.class)))
                .thenReturn(saved);

        Map<String, Object> reqBody = Map.of(
                "eventType",
                "package_cancelled",
                "payload",
                Map.of("cancelledBy", adminId.toString(), "reason", "customer request"));

        mockMvc.perform(post("/api/v1/packages/" + packageId + "/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(reqBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.packageId").value(packageId.toString()))
                .andExpect(jsonPath("$.eventType").value("package_cancelled"))
                .andExpect(jsonPath("$.eventSource").value("OPERADOR_SUCURSAL"));
    }

    @Test
    @DisplayName("POST /api/v1/packages/{id}/events: returns 400 when service rejects the event_type")
    void record_returns400OnUnknownEventType() throws Exception {
        when(trackingEventService.record(eq(tenantId), eq(adminId), eq(packageId), any(RecordEventRequest.class)))
                .thenThrow(new BusinessRuleException("EVENT_VALIDATION_ERROR", Map.of("field", "eventType")));

        Map<String, Object> reqBody = Map.of("eventType", "totally_made_up_event", "payload", Map.of());

        mockMvc.perform(post("/api/v1/packages/" + packageId + "/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(reqBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EVENT_VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/packages/{id}/events: returns 200 with the events array")
    void list_returnsEventsArray() throws Exception {
        TrackingEvent e1 = new TrackingEvent();
        e1.setId(UUID.randomUUID());
        e1.setTenantId(tenantId);
        e1.setPackageId(packageId);
        e1.setEventType("package_created");
        e1.setEventTimestamp(Instant.parse("2026-01-15T09:00:00Z"));
        e1.setUserId(adminId);
        e1.setEventSource("SYSTEM");
        e1.setEventHash("h1");

        TrackingEvent e2 = new TrackingEvent();
        e2.setId(UUID.randomUUID());
        e2.setTenantId(tenantId);
        e2.setPackageId(packageId);
        e2.setEventType("package_cancelled");
        e2.setEventTimestamp(Instant.parse("2026-01-15T10:00:00Z"));
        e2.setUserId(adminId);
        e2.setEventSource("OPERADOR_SUCURSAL");
        e2.setEventHash("h2");

        when(trackingEventService.list(tenantId, packageId)).thenReturn(List.of(e1, e2));

        mockMvc.perform(get("/api/v1/packages/" + packageId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventType").value("package_created"))
                .andExpect(jsonPath("$[0].packageId").value(packageId.toString()))
                .andExpect(jsonPath("$[1].eventType").value("package_cancelled"));
    }
}
