package ar.com.logistics.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.logistics.auth.domain.CompanyUser;
import ar.com.logistics.auth.repository.company.CompanyUserRepository;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.auth.service.CompanyUsersService.ResetPasswordResponse;
import ar.com.logistics.common.audit.AuditEvent;
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
 * Unit tests for {@link CompanyUsersService#resetPassword}. Strict TDD —
 * every test pins a piece of the contract from spec §B.7 + C9
 * before the implementation exists.
 *
 * <p>Contract highlights:
 * <ul>
 *   <li>Generates a 12+ char password with SecureRandom.</li>
 *   <li>Resets failed_login_attempts to 0 and locked_until to NULL.</li>
 *   <li>BCrypt-hashes and stores the new password.</li>
 *   <li>Revokes all refresh tokens (spec C9).</li>
 *   <li>Emits COMPANY_USER_PASSWORD_RESET audit event (without the password in metadata).</li>
 *   <li>Returns the cleartext password exactly once.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CompanyUsersServiceResetPasswordTest {

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
    @DisplayName("resetPassword: happy path generates password, BCrypts, stores, revokes tokens, audits")
    void resetPassword_happyPath() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);
        u.setFailedLoginAttempts(3);
        u.setLockedUntil(java.time.Instant.now().plusSeconds(60));

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any(CompanyUser.class))).thenAnswer(inv -> inv.getArgument(0));
        PasswordGeneratorService.GeneratedPassword genPwd =
                new PasswordGeneratorService.GeneratedPassword("GenPwd!X9zQ");
        when(passwordGenerator.generate()).thenReturn(genPwd);
        when(passwordEncoder.encode("GenPwd!X9zQ")).thenReturn("$2a$12$hashOfGen");
        when(refreshTokenService.revokeAllForUser(userId, "COMPANY")).thenReturn(2);

        ResetPasswordResponse resp = service.resetPassword(tenantId, adminId, userId);

        assertThat(resp.userId()).isEqualTo(userId);
        assertThat(resp.username()).isEqualTo("alice");
        assertThat(resp.temporaryPassword()).isEqualTo("GenPwd!X9zQ");
        assertThat(resp.passwordWarning()).isNotBlank();

        // New hash stored
        verify(passwordEncoder).encode("GenPwd!X9zQ");
        // failed_login_attempts reset, locked_until cleared, save called
        org.mockito.ArgumentCaptor<CompanyUser> captor = org.mockito.ArgumentCaptor.forClass(CompanyUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNull();
        // tokens revoked + audit emitted
        verify(refreshTokenService, times(1)).revokeAllForUser(userId, "COMPANY");
        verify(auditLogger, times(1)).logAsync(any(AuditEvent.class));
    }

    @Test
    @DisplayName("resetPassword: audit metadata MUST NOT contain the cleartext password")
    void resetPassword_auditMetadataDoesNotContainPassword() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any(CompanyUser.class))).thenAnswer(inv -> inv.getArgument(0));
        PasswordGeneratorService.GeneratedPassword genPwd =
                new PasswordGeneratorService.GeneratedPassword("Secret1!Xyz");
        when(passwordGenerator.generate()).thenReturn(genPwd);
        when(passwordEncoder.encode(any(String.class))).thenReturn("$2a$12$h");

        service.resetPassword(tenantId, adminId, userId);

        org.mockito.ArgumentCaptor<AuditEvent> captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).logAsync(captor.capture());
        java.util.Map<String, Object> metadata = captor.getValue().metadata();
        // The cleartext password MUST NOT appear in the audit metadata (spec C2).
        assertThat(metadata.values()).noneMatch(v -> v != null && v.toString().contains("Secret1!Xyz"));
        assertThat(metadata).doesNotContainKey("password");
    }

    @Test
    @DisplayName("resetPassword: does NOT re-disable / re-enable anything — only updates hash + counters + tokens")
    void resetPassword_doesNotChangeStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any(CompanyUser.class))).thenAnswer(inv -> inv.getArgument(0));
        PasswordGeneratorService.GeneratedPassword genPwd =
                new PasswordGeneratorService.GeneratedPassword("NewPwd!1Xyz");
        when(passwordGenerator.generate()).thenReturn(genPwd);
        when(passwordEncoder.encode(any(String.class))).thenReturn("$2a$12$h");

        service.resetPassword(tenantId, adminId, userId);

        org.mockito.ArgumentCaptor<CompanyUser> captor = org.mockito.ArgumentCaptor.forClass(CompanyUser.class);
        verify(userRepository).save(captor.capture());
        // Status is unchanged — reset-password does NOT toggle ACTIVE/DISABLED.
        assertThat(captor.getValue().getStatus()).isEqualTo(CompanyUser.UserStatus.ACTIVE);
        // The COMPANY_USER_PASSWORD_RESET audit IS emitted once.
        verify(auditLogger, times(1)).logAsync(any(AuditEvent.class));
    }
}
