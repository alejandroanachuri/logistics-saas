package ar.com.logistics.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.logistics.auth.repository.company.CompanyUserRepository;
import ar.com.logistics.auth.repository.company.CompanyUserRoleRepository;
import ar.com.logistics.common.exception.BusinessRuleException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link BusinessRuleValidator}. Strict TDD — every test
 * pins a piece of the contract from spec §A.5 + §A.6 + §B.4 + §B.5 before
 * the implementation exists.
 *
 * <p>Contract highlights:
 * <ul>
 *   <li>{@code isFirstAdmin} = {@code created_by IS NULL AND deleted_at IS NULL}.
 *       The flag depends on registration-time rows never having an actor,
 *       which {@code RegistrationService.register()} guarantees.</li>
 *   <li>{@code isLastAdmin} = the user is the ONLY active
 *       {@code COMPANY_ADMIN} in the tenant.</li>
 *   <li>{@code assertNotSelf} throws {@code SELF_EDIT_BLOCKED} when
 *       actor == target.</li>
 *   <li>{@code assertNotFirstAdmin} throws
 *       {@code FIRST_ADMIN_PROTECTED} when the target is the first admin
 *       AND the action is non-info (FIELD_EDIT is allowed for the first
 *       admin's firstName/lastName per decision #8).</li>
 *   <li>{@code assertNotLastAdmin} throws {@code LAST_ADMIN_PROTECTED}
 *       when the target is the last admin AND the action would remove
 *       the admin role.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BusinessRuleValidatorTest {

    @Mock
    private CompanyUserRepository userRepository;

    @Mock
    private CompanyUserRoleRepository roleAssignmentRepository;

    @InjectMocks
    private BusinessRuleValidator validator;

    // -------------------------------------------------------------------
    //  isFirstAdmin
    // -------------------------------------------------------------------

    @Test
    @DisplayName("isFirstAdmin returns true when the user has created_by IS NULL AND deleted_at IS NULL")
    void isFirstAdmin_trueWhenCreatedByIsNull() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(userRepository.countByIdAndCreatedByIsNullAndDeletedAtIsNull(userId))
                .thenReturn(1L);

        assertThat(validator.isFirstAdmin(userId, tenantId)).isTrue();
    }

    @Test
    @DisplayName("isFirstAdmin returns false when the user was created by an actor")
    void isFirstAdmin_falseWhenCreatedByIsNotNull() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(userRepository.countByIdAndCreatedByIsNullAndDeletedAtIsNull(userId))
                .thenReturn(0L);

        assertThat(validator.isFirstAdmin(userId, tenantId)).isFalse();
    }

    @Test
    @DisplayName("isFirstAdmin returns false for a soft-deleted user even if they had no actor")
    void isFirstAdmin_falseForSoftDeletedUser() {
        // Same SQL handles both clauses (created_by IS NULL AND deleted_at IS NULL),
        // so a soft-deleted registration-time admin returns 0 here.
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(userRepository.countByIdAndCreatedByIsNullAndDeletedAtIsNull(userId))
                .thenReturn(0L);

        assertThat(validator.isFirstAdmin(userId, tenantId)).isFalse();
    }

    // -------------------------------------------------------------------
    //  isSelf
    // -------------------------------------------------------------------

    @Test
    @DisplayName("isSelf returns true when actor == target")
    void isSelf_trueWhenSameId() {
        UUID id = UUID.randomUUID();
        assertThat(validator.isSelf(id, id)).isTrue();
    }

    @Test
    @DisplayName("isSelf returns false when actor != target")
    void isSelf_falseWhenDifferentId() {
        assertThat(validator.isSelf(UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }

    // -------------------------------------------------------------------
    //  assertNotSelf
    // -------------------------------------------------------------------

    @Test
    @DisplayName("assertNotSelf throws SELF_EDIT_BLOCKED when actor == target")
    void assertNotSelf_throwsOnSameId() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> validator.assertNotSelf(id, id))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("SELF_EDIT_BLOCKED");
    }

    @Test
    @DisplayName("assertNotSelf is silent when actor != target")
    void assertNotSelf_silentOnDifferentId() {
        validator.assertNotSelf(UUID.randomUUID(), UUID.randomUUID()); // no throw
    }

    // -------------------------------------------------------------------
    //  assertNotFirstAdmin
    // -------------------------------------------------------------------

    @Test
    @DisplayName("assertNotFirstAdmin throws FIRST_ADMIN_PROTECTED for the first admin on ROLE_EDIT")
    void assertNotFirstAdmin_throwsOnRoleEdit() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(userRepository.countByIdAndCreatedByIsNullAndDeletedAtIsNull(userId))
                .thenReturn(1L);

        assertThatThrownBy(
                        () -> validator.assertNotFirstAdmin(userId, tenantId, BusinessRuleValidator.Action.ROLE_EDIT))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("FIRST_ADMIN_PROTECTED");
    }

    @Test
    @DisplayName("assertNotFirstAdmin throws FIRST_ADMIN_PROTECTED for the first admin on DISABLE")
    void assertNotFirstAdmin_throwsOnDisable() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(userRepository.countByIdAndCreatedByIsNullAndDeletedAtIsNull(userId))
                .thenReturn(1L);

        assertThatThrownBy(() -> validator.assertNotFirstAdmin(userId, tenantId, BusinessRuleValidator.Action.DISABLE))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("FIRST_ADMIN_PROTECTED");
    }

    @Test
    @DisplayName(
            "assertNotFirstAdmin is silent for FIELD_EDIT on the first admin (firstName/lastName ARE editable per decision #8)")
    void assertNotFirstAdmin_silentOnFieldEdit() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        // FIELD_EDIT short-circuits before the isFirstAdmin probe,
        // so no stubbing is needed (and the strict-stubbing rule
        // would flag it as unnecessary if we added one).
        validator.assertNotFirstAdmin(userId, tenantId, BusinessRuleValidator.Action.FIELD_EDIT); // no throw
    }

    @Test
    @DisplayName("assertNotFirstAdmin is silent for a non-first-admin user regardless of action")
    void assertNotFirstAdmin_silentForNonFirstAdmin() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(userRepository.countByIdAndCreatedByIsNullAndDeletedAtIsNull(userId))
                .thenReturn(0L);

        // All actions must pass for a non-first admin. The
        // isFirstAdmin probe runs for ROLE_EDIT and DISABLE; the
        // FIELD_EDIT path short-circuits before the probe.
        validator.assertNotFirstAdmin(userId, tenantId, BusinessRuleValidator.Action.ROLE_EDIT);
        validator.assertNotFirstAdmin(userId, tenantId, BusinessRuleValidator.Action.DISABLE);
        validator.assertNotFirstAdmin(userId, tenantId, BusinessRuleValidator.Action.FIELD_EDIT);
        // Exactly 2 probe calls (FIELD_EDIT skips it). The
        // countActiveCompanyAdmins call must NOT happen — the first-
        // admin check short-circuits the last-admin check.
        verify(userRepository, times(2)).countByIdAndCreatedByIsNullAndDeletedAtIsNull(userId);
        verify(roleAssignmentRepository, never()).countActiveCompanyAdmins(any(UUID.class));
    }

    // -------------------------------------------------------------------
    //  isLastAdmin + assertNotLastAdmin
    // -------------------------------------------------------------------

    @Test
    @DisplayName("isLastAdmin returns true when the user is the only active COMPANY_ADMIN")
    void isLastAdmin_trueWhenSingleAdmin() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        // 1 active admin total — the user themselves
        when(roleAssignmentRepository.countActiveCompanyAdmins(tenantId)).thenReturn(1L);

        assertThat(validator.isLastAdmin(userId, tenantId)).isTrue();
    }

    @Test
    @DisplayName("isLastAdmin returns false when there are 2+ active admins in the tenant")
    void isLastAdmin_falseWhenMultipleAdmins() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(roleAssignmentRepository.countActiveCompanyAdmins(tenantId)).thenReturn(2L);

        assertThat(validator.isLastAdmin(userId, tenantId)).isFalse();
    }

    @Test
    @DisplayName("isLastAdmin returns false when the tenant has zero active admins")
    void isLastAdmin_falseWhenZeroAdmins() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(roleAssignmentRepository.countActiveCompanyAdmins(tenantId)).thenReturn(0L);

        assertThat(validator.isLastAdmin(userId, tenantId)).isFalse();
    }

    @Test
    @DisplayName("assertNotLastAdmin throws LAST_ADMIN_PROTECTED when the user is the last admin")
    void assertNotLastAdmin_throwsOnLastAdmin() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(roleAssignmentRepository.countActiveCompanyAdmins(tenantId)).thenReturn(1L);

        assertThatThrownBy(() -> validator.assertNotLastAdmin(userId, tenantId, BusinessRuleValidator.Action.ROLE_EDIT))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("LAST_ADMIN_PROTECTED");
    }

    @Test
    @DisplayName("assertNotLastAdmin is silent when there are multiple admins")
    void assertNotLastAdmin_silentWhenMultipleAdmins() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(roleAssignmentRepository.countActiveCompanyAdmins(tenantId)).thenReturn(2L);

        validator.assertNotLastAdmin(userId, tenantId, BusinessRuleValidator.Action.ROLE_EDIT); // no throw
        validator.assertNotLastAdmin(userId, tenantId, BusinessRuleValidator.Action.DISABLE); // no throw
    }
}
