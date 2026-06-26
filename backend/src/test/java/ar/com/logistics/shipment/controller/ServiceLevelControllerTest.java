package ar.com.logistics.shipment.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.com.logistics.shipment.domain.ServiceLevel;
import ar.com.logistics.shipment.service.ServiceLevelService;
import ar.com.logistics.tenant.TenantContext;
import ar.com.logistics.tenant.TenantContextEntry;
import java.time.Instant;
import java.util.List;
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
 * Controller tests for {@code GET /api/v1/service-levels} (PR-4
 * Chunk B Part 1). Read-only catalog endpoint — any authenticated
 * company user can list the tenant's active service levels.
 */
@ExtendWith(MockitoExtension.class)
class ServiceLevelControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ServiceLevelService serviceLevelService;

    @InjectMocks
    private ServiceLevelController controller;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        tenantId = UUID.randomUUID();
        TenantContext.set(tenantId, TenantContextEntry.Scope.COMPANY);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID(),
                        "n/a",
                        List.of(
                                new SimpleGrantedAuthority("ROLE_COMPANY_OPERATOR"),
                                new SimpleGrantedAuthority("SCOPE_COMPANY"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/service-levels: returns 200 with List<ServiceLevelDto>")
    void list_returnsServiceLevels() throws Exception {
        ServiceLevel standard = new ServiceLevel();
        standard.setId(UUID.randomUUID());
        standard.setTenantId(tenantId);
        standard.setCode("STANDARD");
        standard.setName("Standard (24-48h)");
        standard.setActive(true);
        standard.setCreatedAt(Instant.now());
        standard.setUpdatedAt(Instant.now());

        when(serviceLevelService.list(tenantId)).thenReturn(List.of(standard));

        mockMvc.perform(get("/api/v1/service-levels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("STANDARD"))
                .andExpect(jsonPath("$[0].name").value("Standard (24-48h)"))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }
}
