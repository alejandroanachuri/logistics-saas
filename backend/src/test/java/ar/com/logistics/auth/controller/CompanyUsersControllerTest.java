package ar.com.logistics.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.com.logistics.auth.dto.CreateCompanyUserRequest;
import ar.com.logistics.auth.dto.UpdateCompanyUserRequest;
import ar.com.logistics.auth.service.CompanyUsersService;
import ar.com.logistics.auth.service.CompanyUsersService.CompanyUserDetail;
import ar.com.logistics.auth.service.CompanyUsersService.CompanyUserSummary;
import ar.com.logistics.auth.service.CompanyUsersService.ListFilters;
import ar.com.logistics.auth.service.RoleAssignmentService.RoleRef;
import ar.com.logistics.tenant.TenantContext;
import ar.com.logistics.tenant.TenantContextEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Controller tests for {@code /api/v1/company-users/**} (T-3.4).
 * Seven endpoints, all gated by the service layer's business-rule
 * checks (first/last admin, self-edit). Tests use
 * {@code MockMvcBuilders.standaloneSetup} so the Spring Security
 * filter chain (which enforces the {@code @PreAuthorize} rules)
 * is NOT wired here — security enforcement is covered by the IT
 * suite. The tests focus on:
 *
 * <ul>
 *   <li>Path mapping (HTTP method + URL).</li>
 *   <li>Service delegation (the right {@code tenantId} +
 *       {@code adminId} is threaded through).</li>
 *   <li>JSON shape on the wire (the new {@code roles[]} shape
 *       + the {@code PageResponse} envelope).</li>
 *   <li>204 on disable, 200 on reactivate / reset-password.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CompanyUsersControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CompanyUsersService service;

    @InjectMocks
    private CompanyUsersController controller;

    private UUID tenantId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        tenantId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        // Authenticated admin context for every test. The controller
        // pulls tenantId from TenantContext (set by the auth filter in
        // production) and adminId from the SecurityContext principal
        // (mirrors JwtAuthentication.currentUserId()).
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
    @DisplayName("GET /api/v1/company-users: returns the paginated PageResponse<CompanyUserSummaryDto>")
    void list_returnsPageResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        CompanyUserSummary summary = new CompanyUserSummary(
                userId,
                tenantId,
                "alice",
                "alice@example.com",
                "Alice",
                "Smith",
                "ACTIVE",
                List.of(new RoleRef(UUID.randomUUID(), "COMPANY_ADMIN")),
                Instant.now(),
                Instant.now(),
                false);
        Page<CompanyUserSummary> page = new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1);
        when(service.list(eq(tenantId), any(ListFilters.class), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/company-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].username").value("alice"));
    }

    @Test
    @DisplayName("POST /api/v1/company-users: returns 201 with CreateCompanyUserResponse (includes temporaryPassword)")
    void create_returns201WithOneShotPassword() throws Exception {
        UUID userId = UUID.randomUUID();
        CompanyUserDetail detail = new CompanyUserDetail(
                userId,
                tenantId,
                "bob",
                "bob@example.com",
                "Bob",
                "Doe",
                "ACTIVE",
                false,
                List.of(new RoleRef(UUID.randomUUID(), "COMPANY_VIEWER")),
                0,
                null,
                Instant.now(),
                Instant.now(),
                false);
        CompanyUsersService.CreateCompanyUserResponse serviceResp =
                new CompanyUsersService.CreateCompanyUserResponse(detail, "TmpPwd!9abc", "warning");
        when(service.create(eq(tenantId), eq(adminId), any())).thenReturn(serviceResp);

        CreateCompanyUserRequest req = new CreateCompanyUserRequest(
                "bob", "bob@example.com", "Bob", "Doe", "TmpPwd!9abc", List.of(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/company-users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temporaryPassword").value("TmpPwd!9abc"))
                .andExpect(jsonPath("$.user.username").value("bob"))
                .andExpect(jsonPath("$.user.roles.length()").value(1))
                .andExpect(jsonPath("$.passwordWarning").exists());
    }

    @Test
    @DisplayName("GET /api/v1/company-users/{id}: returns 200 with CompanyUserDetailDto")
    void get_returnsDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        CompanyUserDetail detail = new CompanyUserDetail(
                userId,
                tenantId,
                "carol",
                "c@x.com",
                "C",
                "X",
                "ACTIVE",
                true,
                List.of(new RoleRef(UUID.randomUUID(), "COMPANY_OPERATOR")),
                0,
                null,
                Instant.now(),
                Instant.now(),
                false);
        when(service.get(tenantId, userId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/company-users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("carol"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles.length()").value(1));
    }

    @Test
    @DisplayName("PATCH /api/v1/company-users/{id}: returns 200 with the updated detail")
    void patch_returnsUpdatedDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        CompanyUserDetail detail = new CompanyUserDetail(
                userId,
                tenantId,
                "dave",
                "d@x.com",
                "Dave",
                "Y",
                "ACTIVE",
                false,
                List.of(new RoleRef(UUID.randomUUID(), "COMPANY_ADMIN")),
                0,
                null,
                Instant.now(),
                Instant.now(),
                false);
        when(service.update(eq(tenantId), eq(adminId), eq(userId), any())).thenReturn(detail);

        UpdateCompanyUserRequest req = new UpdateCompanyUserRequest("Dave", null, null, null);

        mockMvc.perform(patch("/api/v1/company-users/" + userId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Dave"));
    }

    @Test
    @DisplayName("POST /api/v1/company-users/{id}/disable: returns 204 and delegates to the service")
    void disable_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        doNothing().when(service).disable(tenantId, adminId, userId);

        mockMvc.perform(post("/api/v1/company-users/" + userId + "/disable")).andExpect(status().isNoContent());

        verify(service).disable(tenantId, adminId, userId);
    }

    @Test
    @DisplayName("POST /api/v1/company-users/{id}/reactivate: returns 200 with the reactivated user")
    void reactivate_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        CompanyUserDetail detail = new CompanyUserDetail(
                userId,
                tenantId,
                "ed",
                "e@x.com",
                "Ed",
                "Z",
                "ACTIVE",
                false,
                List.of(),
                0,
                null,
                Instant.now(),
                Instant.now(),
                false);
        when(service.reactivate(tenantId, adminId, userId)).thenReturn(detail);

        mockMvc.perform(post("/api/v1/company-users/" + userId + "/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/company-users/{id}/reset-password: returns 200 with the one-shot temporaryPassword")
    void resetPassword_returnsOneShotPassword() throws Exception {
        UUID userId = UUID.randomUUID();
        CompanyUsersService.ResetPasswordResponse serviceResp =
                new CompanyUsersService.ResetPasswordResponse(userId, "frank", "NewPwd!9xyz", "warning");
        when(service.resetPassword(tenantId, adminId, userId)).thenReturn(serviceResp);

        mockMvc.perform(post("/api/v1/company-users/" + userId + "/reset-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("frank"))
                .andExpect(jsonPath("$.temporaryPassword").value("NewPwd!9xyz"));
    }

    @Test
    @DisplayName("All seven endpoints thread tenantId + adminId correctly from the JWT context")
    void allEndpoints_threadTenantAndAdmin() throws Exception {
        UUID userId = UUID.randomUUID();
        when(service.list(eq(tenantId), any(), any())).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api/v1/company-users")).andExpect(status().isOk());
        ArgumentCaptor<UUID> tenantCap = ArgumentCaptor.forClass(UUID.class);
        verify(service).list(tenantCap.capture(), any(), any());
        Assertions.assertThat(tenantCap.getValue()).isEqualTo(tenantId);

        doNothing().when(service).disable(eq(tenantId), eq(adminId), eq(userId));
        mockMvc.perform(post("/api/v1/company-users/" + userId + "/disable")).andExpect(status().isNoContent());
        verify(service).disable(tenantId, adminId, userId);
    }
}
