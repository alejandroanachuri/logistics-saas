package ar.com.logistics.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.logistics.auth.domain.CompanyUser;
import ar.com.logistics.auth.repository.company.CompanyUserRepository;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.auth.service.CompanyUsersService.CompanyUserDetail;
import ar.com.logistics.auth.service.CompanyUsersService.UpdateCompanyUserRequest;
import ar.com.logistics.common.audit.AuditEvent;
import ar.com.logistics.common.exception.BusinessRuleException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link CompanyUsersService#update}. Strict TDD — every
 * test pins a piece of the contract from spec §B.4 before the
 * implementation exists.
 *
 * <p>Contract highlights:
 * <ul>
 *   <li>First-name / last-name are ALWAYS editable (decision #8).</li>
 *   <li>Email is protected on the first admin (FIRST_ADMIN_PROTECTED).</li>
 *   <li>Role edit is protected by first-admin AND last-admin rules.</li>
 *   <li>Self-edit is blocked (SELF_EDIT_BLOCKED).</li>
 *   <li>Email uniqueness re-checked on change.</li>
 *   <li>Audit event COMPANY_USER_UPDATED emitted with changedFields metadata.</li>
 *   <li>Audit events COMPANY_USER_ROLES_ASSIGNED / _ROLES_REMOVED emitted per role diff.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CompanyUsersServiceUpdateTest {

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
    @DisplayName("update: changing firstName updates the field and emits COMPANY_USER_UPDATED")
    void update_changesFirstName() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any(CompanyUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleAssignmentService.getRolesForUser(any(UUID.class))).thenReturn(List.of());
        when(businessRuleValidator.isFirstAdmin(any(UUID.class), any(UUID.class)))
                .thenReturn(false);

        UpdateCompanyUserRequest req = new UpdateCompanyUserRequest("Alice M.", null, null, null);

        CompanyUserDetail detail = service.update(tenantId, adminId, userId, req);

        assertThat(detail.firstName()).isEqualTo("Alice M.");
        verify(auditLogger, times(1)).logAsync(any(AuditEvent.class));
    }

    @Test
    @DisplayName("update: SELF_EDIT_BLOCKED is thrown when admin tries to PATCH themselves")
    void update_selfEditBlocked() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = adminId; // self
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        // The validator throws on self-edit
        org.mockito.Mockito.doThrow(new BusinessRuleException("SELF_EDIT_BLOCKED"))
                .when(businessRuleValidator)
                .assertNotSelf(adminId, userId);

        UpdateCompanyUserRequest req = new UpdateCompanyUserRequest("NewName", null, null, null);

        assertThatThrownBy(() -> service.update(tenantId, adminId, userId, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("SELF_EDIT_BLOCKED");

        verify(userRepository, never()).save(any(CompanyUser.class));
    }

    @Test
    @DisplayName("update: FIRST_ADMIN_PROTECTED when the first admin's email is changed")
    void update_firstAdminEmailChangeProtected() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        // Validator throws on first-admin email change (the
        // service calls assertNotFirstAdmin which we mock here;
        // isFirstAdmin is never reached because the assertion
        // throws first).
        org.mockito.Mockito.doThrow(new BusinessRuleException("FIRST_ADMIN_PROTECTED"))
                .when(businessRuleValidator)
                .assertNotFirstAdmin(userId, tenantId, BusinessRuleValidator.Action.ROLE_EDIT);

        UpdateCompanyUserRequest req = new UpdateCompanyUserRequest(null, null, "new@example.com", null);

        assertThatThrownBy(() -> service.update(tenantId, adminId, userId, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("FIRST_ADMIN_PROTECTED");

        verify(userRepository, never()).save(any(CompanyUser.class));
    }

    @Test
    @DisplayName("update: FIRST_ADMIN_PROTECTED when the first admin's roleIds change")
    void update_firstAdminRoleChangeProtected() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID newRoleId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        // Validator throws on first-admin role edit (no isFirstAdmin stub needed — the assertion throws first).
        org.mockito.Mockito.doThrow(new BusinessRuleException("FIRST_ADMIN_PROTECTED"))
                .when(businessRuleValidator)
                .assertNotFirstAdmin(userId, tenantId, BusinessRuleValidator.Action.ROLE_EDIT);

        UpdateCompanyUserRequest req = new UpdateCompanyUserRequest(null, null, null, List.of(newRoleId));

        assertThatThrownBy(() -> service.update(tenantId, adminId, userId, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("FIRST_ADMIN_PROTECTED");
    }

    @Test
    @DisplayName("update: LAST_ADMIN_PROTECTED when the last admin's roleIds change")
    void update_lastAdminRoleChangeProtected() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID newRoleId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        // Validator throws on last-admin role edit (no isFirstAdmin stub needed — assertNotLastAdmin throws first).
        org.mockito.Mockito.doThrow(new BusinessRuleException("LAST_ADMIN_PROTECTED"))
                .when(businessRuleValidator)
                .assertNotLastAdmin(userId, tenantId, BusinessRuleValidator.Action.ROLE_EDIT);

        UpdateCompanyUserRequest req = new UpdateCompanyUserRequest(null, null, null, List.of(newRoleId));

        assertThatThrownBy(() -> service.update(tenantId, adminId, userId, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("LAST_ADMIN_PROTECTED");
    }

    @Test
    @DisplayName("update: EMAIL_ALREADY_TAKEN when the new email is taken by another user in the tenant")
    void update_duplicateEmailRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        when(userRepository.existsByTenantIdAndEmailAndIdNot(tenantId, "new@example.com", userId))
                .thenReturn(true);

        UpdateCompanyUserRequest req = new UpdateCompanyUserRequest(null, null, "new@example.com", null);

        assertThatThrownBy(() -> service.update(tenantId, adminId, userId, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("EMAIL_ALREADY_TAKEN");

        verify(userRepository, never()).save(any(CompanyUser.class));
    }

    @Test
    @DisplayName("update: role diff emits COMPANY_USER_ROLES_ASSIGNED + COMPANY_USER_ROLES_REMOVED audit events")
    void update_roleDiffEmitsAuditEvents() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID addedRole = UUID.randomUUID();
        UUID removedRole = UUID.randomUUID();
        CompanyUser u = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        u.setStatus(CompanyUser.UserStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any(CompanyUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleAssignmentService.assignRoles(eq(userId), anyCollection(), eq(adminId)))
                .thenReturn(new RoleAssignmentService.RoleDiff(List.of(addedRole), List.of(removedRole), List.of()));
        when(roleAssignmentService.getRolesForUser(any(UUID.class))).thenReturn(List.of());

        UpdateCompanyUserRequest req = new UpdateCompanyUserRequest(null, null, null, List.of(addedRole));

        service.update(tenantId, adminId, userId, req);

        // 2 role-events (assigned + removed) + 0 COMPANY_USER_UPDATED
        // because the only changed field was roleIds (not first/last/email).
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger, times(2)).logAsync(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AuditEvent::eventType)
                .containsExactlyInAnyOrder("COMPANY_USER_ROLES_ASSIGNED", "COMPANY_USER_ROLES_REMOVED");
    }

    // Local anyCollection/eq helper to keep imports tidy
    private static <T> T any(Class<T> c) {
        return org.mockito.ArgumentMatchers.any(c);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
