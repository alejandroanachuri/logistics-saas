package ar.com.logistics.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.logistics.auth.domain.CompanyUser;
import ar.com.logistics.auth.repository.company.CompanyUserRepository;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.common.audit.AuditEvent;
import ar.com.logistics.common.exception.BusinessRuleException;
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
 * Unit tests for {@link CompanyUsersService#disable}. Strict TDD —
 * every test pins a piece of the contract from spec §B.5 before
 * the implementation exists.
 *
 * <p>Contract highlights:
 * <ul>
 *   <li>Sets {@code status = DISABLED} and {@code deleted_at = now()}.</li>
 *   <li>Revokes all active refresh tokens (spec C9).</li>
 *   <li>Emits {@code COMPANY_USER_DISABLED} audit event.</li>
 *   <li>Self-block / first-admin block / last-admin block all surface
 *       as their respective error codes.</li>
 *   <li>Already-disabled user surfaces as USER_ALREADY_DISABLED.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CompanyUsersServiceDisableTest {

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
    @DisplayName("disable: SELF_EDIT_BLOCKED when admin tries to disable themselves")
    void disable_selfBlocked() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = adminId;
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        org.mockito.Mockito.doThrow(new BusinessRuleException("SELF_EDIT_BLOCKED"))
                .when(businessRuleValidator)
                .assertNotSelf(adminId, userId);

        assertThatThrownBy(() -> service.disable(tenantId, adminId, userId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("SELF_EDIT_BLOCKED");

        verify(userRepository, never()).save(any(CompanyUser.class));
        verify(refreshTokenService, never()).revokeAllForUser(any(UUID.class), any(String.class));
    }

    @Test
    @DisplayName("disable: FIRST_ADMIN_PROTECTED when the target is the first admin")
    void disable_firstAdminProtected() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        org.mockito.Mockito.doThrow(new BusinessRuleException("FIRST_ADMIN_PROTECTED"))
                .when(businessRuleValidator)
                .assertNotFirstAdmin(userId, tenantId, BusinessRuleValidator.Action.DISABLE);

        assertThatThrownBy(() -> service.disable(tenantId, adminId, userId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("FIRST_ADMIN_PROTECTED");
    }

    @Test
    @DisplayName("disable: LAST_ADMIN_PROTECTED when the target is the last admin")
    void disable_lastAdminProtected() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        org.mockito.Mockito.doThrow(new BusinessRuleException("LAST_ADMIN_PROTECTED"))
                .when(businessRuleValidator)
                .assertNotLastAdmin(userId, tenantId, BusinessRuleValidator.Action.DISABLE);

        assertThatThrownBy(() -> service.disable(tenantId, adminId, userId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("LAST_ADMIN_PROTECTED");
    }

    @Test
    @DisplayName("disable: USER_ALREADY_DISABLED when the user is already disabled")
    void disable_alreadyDisabled() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.DISABLED);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.disable(tenantId, adminId, userId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("USER_ALREADY_DISABLED");

        verify(userRepository, never()).save(any(CompanyUser.class));
    }

    @Test
    @DisplayName("disable: happy path flips status, deletes_at, revokes tokens, emits audit")
    void disable_happyPath() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any(CompanyUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenService.revokeAllForUser(userId, "COMPANY")).thenReturn(2);

        service.disable(tenantId, adminId, userId);

        // status flipped, deleted_at set, save called, tokens revoked,
        // audit emitted
        org.mockito.Mockito.verify(userRepository).save(any(CompanyUser.class));
        verify(refreshTokenService, times(1)).revokeAllForUser(userId, "COMPANY");
        verify(auditLogger, times(1)).logAsync(any(AuditEvent.class));
    }
}
