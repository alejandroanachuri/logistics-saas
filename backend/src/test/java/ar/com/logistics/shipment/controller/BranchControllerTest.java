package ar.com.logistics.shipment.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.com.logistics.shipment.domain.Branch;
import ar.com.logistics.shipment.service.BranchService;
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
 * Controller tests for {@code GET /api/v1/branches} (PR-4 Chunk B
 * Part 1). Read-only catalog endpoint — any authenticated company
 * user can list the tenant's active branches.
 */
@ExtendWith(MockitoExtension.class)
class BranchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BranchService branchService;

    @InjectMocks
    private BranchController controller;

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
                                new SimpleGrantedAuthority("ROLE_COMPANY_VIEWER"),
                                new SimpleGrantedAuthority("SCOPE_COMPANY"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/branches: returns 200 with List<BranchDto> ordered by service")
    void list_returnsBranches() throws Exception {
        UUID principalId = UUID.randomUUID();
        Branch principal = new Branch();
        principal.setId(UUID.randomUUID());
        principal.setTenantId(tenantId);
        principal.setCode("PRINCIPAL");
        principal.setName("Casa Central");
        principal.setAddressId(principalId);
        principal.setActive(true);
        principal.setCreatedAt(Instant.now());
        principal.setUpdatedAt(Instant.now());

        when(branchService.list(tenantId)).thenReturn(List.of(principal));

        mockMvc.perform(get("/api/v1/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("PRINCIPAL"))
                .andExpect(jsonPath("$[0].name").value("Casa Central"))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }
}
