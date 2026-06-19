package ar.com.logistics.auth.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.com.logistics.auth.dto.RoleDto;
import ar.com.logistics.auth.service.RoleAssignmentService;
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
 * Unit tests for {@code GET /api/v1/roles} (T-3.3). Uses
 * {@code MockMvcBuilders.standaloneSetup} to wire just the
 * controller + its dependency — no Spring context needed.
 *
 * <p>Why not {@code @WebMvcTest}? Spring Boot 4 reorganised the
 * web test annotations into the {@code spring-boot-webmvc-test}
 * artifact; {@code @MockBean} was deprecated in favour of
 * {@code @MockitoBean} (Spring Framework 6.2+). The standalone
 * setup gives us the same coverage — controller wiring +
 * service delegation + JSON shape — without depending on the
 * Spring Boot test slice infrastructure, which keeps the test
 * small and dependency-free.
 *
 * <p>Security is tested separately in the IT suite — the standalone
 * setup deliberately does NOT exercise {@code @PreAuthorize}
 * (the controller advice is not in scope here); this test focuses
 * on the controller's contract:
 * <ul>
 *   <li>The endpoint returns the COMPANY role catalog as a
 *       {@code List<RoleDto>}.</li>
 *   <li>Empty catalog → empty list, still 200.</li>
 *   <li>Non-COMPANY scope returns empty list (v1 only wires COMPANY).</li>
 *   <li>Default scope is COMPANY.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RolesControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RoleAssignmentService roleAssignmentService;

    @InjectMocks
    private RolesController controller;

    @BeforeEach
    void setUp() {
        // Standalone MockMvc: just the controller, no Spring context.
        // Security is NOT exercised here (PreAuthorize enforcement
        // requires the Spring Security filter chain — that's covered
        // by the IT suite, not by this unit test).
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/roles?scope=COMPANY: returns the 4 COMPANY roles as [{id, name, description}]")
    void listCompanyRoles_returnsFourRoles() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        // Authenticated context for completeness (standalone setup
        // doesn't enforce security; the SecurityContext is a
        // stand-in for the integration-level assertion).
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"))));

        when(roleAssignmentService.listCompanyRoles())
                .thenReturn(List.of(
                        new RoleDto(adminId, "COMPANY_ADMIN", "Full access"),
                        new RoleDto(operatorId, "COMPANY_OPERATOR", "Manage shipments"),
                        new RoleDto(driverId, "COMPANY_DRIVER", "Execute deliveries"),
                        new RoleDto(viewerId, "COMPANY_VIEWER", "Read-only access")));

        mockMvc.perform(get("/api/v1/roles?scope=COMPANY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").value("COMPANY_ADMIN"))
                .andExpect(jsonPath("$[1].name").value("COMPANY_OPERATOR"))
                .andExpect(jsonPath("$[2].name").value("COMPANY_DRIVER"))
                .andExpect(jsonPath("$[3].name").value("COMPANY_VIEWER"));
    }

    @Test
    @DisplayName(
            "GET /api/v1/roles?scope=COMPANY: returns 200 with an empty list when the catalog has no COMPANY roles")
    void listCompanyRoles_emptyWhenNoRoles() throws Exception {
        when(roleAssignmentService.listCompanyRoles()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/roles?scope=COMPANY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/roles?scope=PLATFORM: returns 200 with empty list (v1 only wires COMPANY)")
    void listCompanyRoles_nonCompanyScopeReturnsEmpty() throws Exception {
        // v1 only wires the COMPANY scope — anything else returns an
        // empty list with 200 (the controller does NOT 403; that
        // would be wrong because the endpoint is admin-only via
        // @PreAuthorize, not scope-restricted via the query param).
        mockMvc.perform(get("/api/v1/roles?scope=PLATFORM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/roles without scope: defaults to COMPANY")
    void listCompanyRoles_defaultsToCompany() throws Exception {
        when(roleAssignmentService.listCompanyRoles())
                .thenReturn(List.of(new RoleDto(UUID.randomUUID(), "COMPANY_VIEWER", "Read-only")));

        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
