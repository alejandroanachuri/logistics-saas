package ar.com.logistics.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ar.com.logistics.auth.domain.CompanyUser;
import ar.com.logistics.auth.repository.company.CompanyUserRepository;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.auth.service.CompanyUsersService.CompanyUserDetail;
import ar.com.logistics.common.exception.BusinessRuleException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link CompanyUsersService#get}. Strict TDD — every test
 * pins a piece of the contract from spec §B.3 before the
 * implementation exists.
 */
@ExtendWith(MockitoExtension.class)
class CompanyUsersServiceGetTest {

    @Mock
    private CompanyUserRepository userRepository;

    @Mock
    private ar.com.logistics.auth.repository.company.CompanyUserRoleRepository roleAssignmentRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordGeneratorService passwordGenerator;

    @Mock
    private RoleAssignmentService roleAssignmentService;

    @Mock
    private BusinessRuleValidator businessRuleValidator;

    @Mock
    private ar.com.logistics.common.audit.AuditLogger auditLogger;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private CompanyUsersService service;

    @Test
    @DisplayName("get: returns the user detail when the id belongs to the tenant")
    void get_returnsUserDetail() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        when(roleAssignmentService.getRolesForUser(any(UUID.class))).thenReturn(List.of());
        when(businessRuleValidator.isFirstAdmin(any(UUID.class), any(UUID.class)))
                .thenReturn(false);

        CompanyUserDetail detail = service.get(tenantId, userId);

        assertThat(detail.username()).isEqualTo("alice");
        assertThat(detail.email()).isEqualTo("alice@example.com");
        assertThat(detail.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("get: throws USER_NOT_FOUND when the user does not exist in the tenant")
    void get_throwsWhenUserMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(tenantId, userId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("get: throws USER_NOT_FOUND when the user belongs to a different tenant")
    void get_throwsWhenUserBelongsToDifferentTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(tenantId, userId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("get: hydrates the roles[] projection from RoleAssignmentService")
    void get_hydratesRoles() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        when(roleAssignmentService.getRolesForUser(any(UUID.class)))
                .thenReturn(List.of(new RoleAssignmentService.RoleRef(roleId, "COMPANY_VIEWER")));
        when(businessRuleValidator.isFirstAdmin(any(UUID.class), any(UUID.class)))
                .thenReturn(false);

        CompanyUserDetail detail = service.get(tenantId, userId);

        assertThat(detail.roles()).hasSize(1);
        assertThat(detail.roles().get(0).name()).isEqualTo("COMPANY_VIEWER");
    }

    @Test
    @DisplayName("get: marks isFirstAdmin=true when the user is the registration-time admin")
    void get_marksIsFirstAdmin() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        when(roleAssignmentService.getRolesForUser(any(UUID.class))).thenReturn(List.of());
        when(businessRuleValidator.isFirstAdmin(any(UUID.class), any(UUID.class)))
                .thenReturn(true);

        CompanyUserDetail detail = service.get(tenantId, userId);

        assertThat(detail.isFirstAdmin()).isTrue();
    }
}
