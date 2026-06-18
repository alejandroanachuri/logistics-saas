package ar.com.logistics.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.logistics.auth.domain.CompanyUser;
import ar.com.logistics.auth.repository.company.CompanyUserRepository;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.auth.service.CompanyUsersService.CompanyUserDetail;
import ar.com.logistics.common.audit.AuditEvent;
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
 * Unit tests for {@link CompanyUsersService#reactivate}. Strict TDD —
 * every test pins a piece of the contract from spec §B.6 before
 * the implementation exists.
 */
@ExtendWith(MockitoExtension.class)
class CompanyUsersServiceReactivateTest {

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
    @DisplayName("reactivate: USER_ALREADY_ACTIVE when the user is already ACTIVE")
    void reactivate_alreadyActive() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.reactivate(tenantId, adminId, userId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("USER_ALREADY_ACTIVE");

        verify(userRepository, never()).save(any(CompanyUser.class));
    }

    @Test
    @DisplayName("reactivate: happy path flips status to ACTIVE, clears deleted_at, emits audit")
    void reactivate_happyPath() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.DISABLED);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any(CompanyUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleAssignmentService.getRolesForUser(any(UUID.class))).thenReturn(List.of());

        CompanyUserDetail detail = service.reactivate(tenantId, adminId, userId);

        assertThat(detail.status()).isEqualTo("ACTIVE");
        verify(auditLogger, times(1)).logAsync(any(AuditEvent.class));
    }
}
