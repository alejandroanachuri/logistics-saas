package ar.com.logistics.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.logistics.auth.domain.Role;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.auth.service.RoleAssignmentService.RoleDiff;
import ar.com.logistics.auth.service.RoleAssignmentService.RoleRef;
import ar.com.logistics.common.exception.BusinessRuleException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RoleAssignmentService}. Strict TDD — every test
 * below pins a piece of the contract from spec §B.2 + design §3.2 before
 * the implementation exists.
 *
 * <p>Contract highlights:
 * <ul>
 *   <li>Bulk assign / remove from the {@code company_user_roles}
 *       junction (delegated to the company-side repo).</li>
 *   <li>{@code validateScopeCompany} rejects any role whose
 *       {@code scope = PLATFORM} with code
 *       {@code INVALID_ROLE} — spec C7.</li>
 *   <li>{@code validateAtLeastOneRole} rejects any mutation that
 *       would leave the user with zero roles — spec §B.4.</li>
 *   <li>{@code diffRoles} returns a {@link RoleDiff} carrying
 *       {@code added} / {@code removed} / {@code unchanged} so the
 *       service layer can emit the right audit events.</li>
 *   <li>{@code getRolesForUser} hydrates the {@code [{id, name}]}
 *       projection used in response DTOs.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private ar.com.logistics.auth.repository.company.CompanyUserRoleRepository roleAssignmentRepository;

    @InjectMocks
    private RoleAssignmentService service;

    @Test
    @DisplayName("validateScopeCompany rejects PLATFORM-scope roles with BusinessRuleException(INVALID_ROLE)")
    void validateScopeCompany_rejectsPlatformRoles() {
        UUID platformId = UUID.randomUUID();
        Role platform = mockRoleMinimal(platformId, Role.RoleScope.PLATFORM);

        when(roleRepository.findAllById(List.of(platformId))).thenReturn(List.of(platform));

        assertThatThrownBy(() -> service.validateScopeCompany(List.of(platformId)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("INVALID_ROLE");
    }

    @Test
    @DisplayName("validateScopeCompany passes silently when every role has scope=COMPANY")
    void validateScopeCompany_acceptsCompanyRoles() {
        UUID id = UUID.randomUUID();
        Role company = mockRoleMinimal(id, Role.RoleScope.COMPANY);

        when(roleRepository.findAllById(List.of(id))).thenReturn(List.of(company));

        service.validateScopeCompany(List.of(id)); // no throw
    }

    @Test
    @DisplayName("validateAtLeastOneRole throws when the desired set is empty")
    void validateAtLeastOneRole_rejectsEmptySet() {
        assertThatThrownBy(() -> service.validateAtLeastOneRole(Set.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one role");
    }

    @Test
    @DisplayName("validateAtLeastOneRole passes when the desired set is non-empty")
    void validateAtLeastOneRole_acceptsNonEmptySet() {
        service.validateAtLeastOneRole(Set.of(UUID.randomUUID())); // no throw
    }

    @Test
    @DisplayName("diffRoles returns added/removed/unchanged correctly when adding new roles")
    void diffRoles_addedNewRoles() {
        UUID existingA = UUID.randomUUID();
        UUID existingB = UUID.randomUUID();
        UUID newC = UUID.randomUUID();

        // Existing: A, B. Desired: A, B, C.
        when(roleAssignmentRepository.findRoleIdsByUserId(any())).thenReturn(List.of(existingA, existingB));

        RoleDiff diff = service.diffRoles(UUID.randomUUID(), List.of(existingA, existingB, newC));

        assertThat(diff.added()).containsExactly(newC);
        assertThat(diff.removed()).isEmpty();
        assertThat(diff.unchanged()).containsExactlyInAnyOrder(existingA, existingB);
    }

    @Test
    @DisplayName("diffRoles returns the removed roles when shrinking the set")
    void diffRoles_removedRoles() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        when(roleAssignmentRepository.findRoleIdsByUserId(any())).thenReturn(List.of(a, b, c));

        RoleDiff diff = service.diffRoles(UUID.randomUUID(), List.of(a));

        assertThat(diff.added()).isEmpty();
        assertThat(diff.removed()).containsExactlyInAnyOrder(b, c);
        assertThat(diff.unchanged()).containsExactly(a);
    }

    @Test
    @DisplayName("assignRoles writes a junction row per added role and emits no row for unchanged ones")
    void assignRoles_persistsAddedRoles() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID existing = UUID.randomUUID();
        UUID added = UUID.randomUUID();

        when(roleAssignmentRepository.findRoleIdsByUserId(userId)).thenReturn(List.of(existing));

        service.assignRoles(userId, List.of(existing, added), actorId);

        verify(roleAssignmentRepository, times(1)).insertRow(userId, added, actorId);
        verify(roleAssignmentRepository, never()).insertRow(eq(userId), eq(existing), any());
    }

    @Test
    @DisplayName("removeRoles deletes a junction row per removed role and leaves unchanged ones alone")
    void removeRoles_persistsRemovedRoles() {
        UUID userId = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        UUID removed = UUID.randomUUID();

        when(roleAssignmentRepository.findRoleIdsByUserId(userId)).thenReturn(List.of(kept, removed));

        service.removeRoles(userId, List.of(removed));

        verify(roleAssignmentRepository, times(1)).deleteByCompanyUserIdAndRoleId(userId, removed);
        verify(roleAssignmentRepository, never()).deleteByCompanyUserIdAndRoleId(eq(userId), eq(kept));
    }

    @Test
    @DisplayName("getRolesForUser returns the [{id, name}] projection hydrated from the role catalog")
    void getRolesForUser_hydratesProjection() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        // Only getId() + getName() are read on this path (the
        // [{id, name}] projection never includes scope).
        Role role = Mockito.mock(Role.class);
        when(role.getId()).thenReturn(roleId);
        when(role.getName()).thenReturn("COMPANY_VIEWER");

        when(roleAssignmentRepository.findRoleIdsByUserId(userId)).thenReturn(List.of(roleId));
        when(roleRepository.findAllById(List.of(roleId))).thenReturn(List.of(role));

        List<RoleRef> refs = service.getRolesForUser(userId);

        assertThat(refs).containsExactly(new RoleRef(roleId, "COMPANY_VIEWER"));
    }

    @Test
    @DisplayName("getRolesForUser returns an empty list when the user has no roles")
    void getRolesForUser_emptyForUserWithNoRoles() {
        UUID userId = UUID.randomUUID();
        when(roleAssignmentRepository.findRoleIdsByUserId(userId)).thenReturn(List.of());

        List<RoleRef> refs = service.getRolesForUser(userId);

        assertThat(refs).isEmpty();
        // No need to query the role catalog when there's nothing to map.
        verify(roleRepository, never()).findAllById(anyList());
    }

    @Test
    @DisplayName("RoleRef equality is by id + name (so DTO projection tests stay deterministic)")
    void roleRef_equality_byIdAndName() {
        UUID id = UUID.randomUUID();
        RoleRef a = new RoleRef(id, "COMPANY_ADMIN");
        RoleRef b = new RoleRef(id, "COMPANY_ADMIN");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    // -------------------------------------------------------------------
    //  Test helpers
    // -------------------------------------------------------------------

    /**
     * Build a Mockito-mocked {@link Role} that returns the supplied id /
     * name / scope from its getters. We use mocks instead of constructing
     * a real entity because {@code Role} is {@code @Getter}-only with no
     * setters — it has no public API for setting its fields from a test.
     * The mock also doubles as documentation of which fields the service
     * actually reads.
     */
    private static Role mockRole(UUID id, String name, Role.RoleScope scope) {
        Role role = Mockito.mock(Role.class);
        when(role.getId()).thenReturn(id);
        when(role.getName()).thenReturn(name);
        when(role.getScope()).thenReturn(scope);
        return role;
    }

    /**
     * Build a Mockito-mocked {@link Role} that only stubs the supplied
     * fields. Used by tests that don't exercise every getter on the role —
     * keeps Mockito's strict-stubbing rule happy (no
     * {@code UnnecessaryStubbingException}).
     */
    private static Role mockRoleMinimal(UUID id, Role.RoleScope scope) {
        Role role = Mockito.mock(Role.class);
        when(role.getId()).thenReturn(id);
        when(role.getScope()).thenReturn(scope);
        return role;
    }
}
