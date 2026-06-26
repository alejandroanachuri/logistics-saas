package ar.com.logistics.shipment.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.com.logistics.common.exception.BusinessRuleException;
import ar.com.logistics.common.exception.GlobalExceptionHandler;
import ar.com.logistics.shipment.service.PublicTrackService;
import ar.com.logistics.shipment.service.PublicTrackService.PublicTrackResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller tests for {@link PublicTrackController}
 * ({@code /api/v1/public/track/{lgstid}}, etapa-3-envios PR-4 Chunk C).
 *
 * <p>Uses {@code MockMvcBuilders.standaloneSetup} — the project
 * convention from the other shipment controller tests. No
 * {@code SecurityContextHolder} setup is performed; this is a public
 * endpoint, so the controller must work without any authentication
 * state.
 */
@ExtendWith(MockitoExtension.class)
class PublicTrackControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PublicTrackService publicTrackService;

    @InjectMocks
    private PublicTrackController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        // Deliberately no SecurityContextHolder setup — the endpoint is
        // public. Clearing here documents the intent.
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/public/track/{lgstid} returns 200 with PublicTrackResponse")
    void track_returns200() throws Exception {
        PublicTrackResponse resp = new PublicTrackResponse(
                "LGST-7K2M9XQP",
                "CREADO",
                "Envío registrado",
                false,
                1,
                new BigDecimal("1.25"),
                "Juan G.",
                List.of(new PublicTrackResponse.TimelineEntry(
                        Instant.parse("2026-06-25T09:00:00Z"), "Envío registrado")));
        when(publicTrackService.get("LGST-7K2M9XQP")).thenReturn(resp);

        mockMvc.perform(get("/api/v1/public/track/LGST-7K2M9XQP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("LGST-7K2M9XQP"))
                .andExpect(jsonPath("$.status").value("CREADO"))
                .andExpect(jsonPath("$.statusMessage").value("Envío registrado"))
                .andExpect(jsonPath("$.isPartial").value(false))
                .andExpect(jsonPath("$.packageCount").value(1))
                .andExpect(jsonPath("$.totalWeightKg").value(1.25))
                .andExpect(jsonPath("$.receiverName").value("Juan G."))
                .andExpect(jsonPath("$.timeline[0].message").value("Envío registrado"))
                // No internal fields on the wire.
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.packageId").doesNotExist())
                .andExpect(jsonPath("$.branchId").doesNotExist())
                .andExpect(jsonPath("$.paymentType").doesNotExist())
                .andExpect(jsonPath("$.dni").doesNotExist())
                .andExpect(jsonPath("$.cuit").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/public/track/{nonexistent} returns 404 with TRACKING_NOT_FOUND")
    void track_returns404WhenMissing() throws Exception {
        when(publicTrackService.get("LGST-AAAAAAAA")).thenThrow(new BusinessRuleException("TRACKING_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/public/track/LGST-AAAAAAAA"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRACKING_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/public/track/{lgstid} does NOT require authentication (works with empty SecurityContext)")
    void track_worksWithoutAuth() throws Exception {
        // The endpoint is under /api/v1/public/** which is permitted
        // without authentication in SecurityConfig. The controller has
        // no @PreAuthorize and no @AuthenticationPrincipal. This test
        // pins that contract by running with an empty
        // SecurityContextHolder.
        PublicTrackResponse resp = new PublicTrackResponse(
                "LGST-7K2M9XQP", "CREADO", "Envío registrado", false, 1, new BigDecimal("1.00"), "Ana P.", List.of());
        when(publicTrackService.get("LGST-7K2M9XQP")).thenReturn(resp);

        mockMvc.perform(get("/api/v1/public/track/LGST-7K2M9XQP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("LGST-7K2M9XQP"));
    }
}
