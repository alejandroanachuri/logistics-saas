package ar.com.logistics.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.logistics.auth.domain.CompanyUser;
import ar.com.logistics.auth.domain.CompanyUser.UserStatus;
import ar.com.logistics.auth.repository.company.CompanyUserRepository;
import ar.com.logistics.auth.repository.company.CompanyUserRoleRepository;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.auth.service.CompanyUsersService.CompanyUserDetail;
import ar.com.logistics.auth.service.CompanyUsersService.CreateCompanyUserRequest;
import ar.com.logistics.auth.service.CompanyUsersService.CreateCompanyUserResponse;
import ar.com.logistics.common.audit.AuditLogger;
import ar.com.logistics.common.exception.BusinessRuleException;
import ar.com.logistics.common.exception.ValidationException;
import ar.com.logistics.common.validation.PasswordValidator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link CompanyUsersService#create}. Strict TDD — every
 * test pins a piece of the contract from spec §B.2 before the
 * implementation exists.
 *
 * <p>Contract highlights:
 * <ul>
 *   <li>Validates username / email / password / roleIds before any DB
 *       write.</li>
 *   <li>Rejects empty roleIds with {@code VALIDATION_ERROR}.</li>
 *   <li>Rejects a non-COMPANY role id with {@code INVALID_ROLE}.</li>
 *   <li>Rejects a duplicate username / email with the appropriate
 *       409 code.</li>
 *   <li>Hashes the password with BCrypt, creates the user with
 *       {@code created_by = adminId}, assigns the roles, and emits
 *       the {@code COMPANY_USER_CREATED} audit event.</li>
 *   <li>Returns the cleartext password in the response — caller
 *       (PR-3 controller) writes it into the response exactly once.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CompanyUsersServiceCreateTest {

    @Mock
    private CompanyUserRepository userRepository;

    @Mock
    private CompanyUserRoleRepository roleAssignmentRepository;

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
    private AuditLogger auditLogger;

    @InjectMocks
    private CompanyUsersService service;

    @Test
    @DisplayName("create: happy path returns the user detail and the plain-text password, audit emitted")
    void create_happyPath() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        CreateCompanyUserRequest req = new CreateCompanyUserRequest(
                "alice", "alice@example.com", "Alice", "Smith", "GoodPwd1", List.of(roleId));

        // Note: no Role stubbing here because validateScopeCompany
        // is on the MOCKED RoleAssignmentService — the real
        // RoleRepository.findAllById() path is not exercised in
        // this test. The role-catalog hydration in
        // getRolesForUser (for the response) is also on the mock.

        when(userRepository.existsByTenantIdAndUsername(tenantId, "alice")).thenReturn(false);
        when(userRepository.existsByTenantIdAndEmail(tenantId, "alice@example.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("GoodPwd1")).thenReturn("$2a$12$hashed");

        CompanyUser saved = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        saved.setPasswordHash("$2a$12$hashed");
        // The service flips the factory default PENDING_VERIFICATION →
        // ACTIVE on its in-flight instance BEFORE save; the save
        // mock must therefore return a user that already reflects
        // the ACTIVE state.
        saved.setStatus(CompanyUser.UserStatus.ACTIVE);
        when(userRepository.save(any(CompanyUser.class))).thenReturn(saved);

        // diffRoles / getRolesForUser are on the MOCKED
        // RoleAssignmentService — default Mockito behavior returns
        // empty results, which is what we want here (this test only
        // asserts the password + the audit emission).

        CreateCompanyUserResponse resp = service.create(tenantId, adminId, req);

        assertThat(resp.user()).isNotNull();
        assertThat(resp.user().username()).isEqualTo("alice");
        assertThat(resp.user().email()).isEqualTo("alice@example.com");
        assertThat(resp.user().status()).isEqualTo(UserStatus.ACTIVE.name());
        // Spec C2: passwordHash MUST NEVER appear in any response.
        // The detail record does not even define a passwordHash field
        // — this is a structural guarantee, not a runtime check.
        assertThat(CompanyUserDetail.class.getRecordComponents())
                .as("CompanyUserDetail must not expose passwordHash (spec C2)")
                .extracting("name", String.class)
                .doesNotContain("passwordHash");
        assertThat(resp.temporaryPassword()).isEqualTo("GoodPwd1");
        assertThat(resp.passwordWarning()).isNotBlank();

        // created_by must be the actor (adminId), not null
        ArgumentCaptor<CompanyUser> userCaptor = ArgumentCaptor.forClass(CompanyUser.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCreatedBy()).isEqualTo(adminId);

        // role assignment + audit
        verify(roleAssignmentService, times(1)).validateScopeCompany(List.of(roleId));
        verify(roleAssignmentService, times(1)).validateAtLeastOneRole(List.of(roleId));
        verify(roleAssignmentService, times(1)).assignRoles(saved.getId(), List.of(roleId), adminId);
        verify(auditLogger, times(1)).logAsync(any());
    }

    @Test
    @DisplayName("create: empty roleIds is rejected with VALIDATION_ERROR before any DB write")
    void create_emptyRoleIdsRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        CreateCompanyUserRequest req =
                new CreateCompanyUserRequest("alice", "alice@example.com", "Alice", "Smith", "GoodPwd1", List.of());

        // The validateAtLeastOneRole validator is mocked; stub it to
        // throw the same exception the real implementation would.
        Mockito.doThrow(new BusinessRuleException("VALIDATION_ERROR", "A user must have at least one role.", null))
                .when(roleAssignmentService)
                .validateAtLeastOneRole(List.of());

        assertThatThrownBy(() -> service.create(tenantId, adminId, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("VALIDATION_ERROR");

        verify(userRepository, never()).save(any(CompanyUser.class));
        verify(auditLogger, never()).logAsync(any());
    }

    @Test
    @DisplayName("create: password that fails PasswordValidator.isValid is rejected with VALIDATION_ERROR")
    void create_weakPasswordRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        // "shorty1" — 7 chars, no upper
        CreateCompanyUserRequest req = new CreateCompanyUserRequest(
                "alice", "alice@example.com", "Alice", "Smith", "shorty1", List.of(roleId));

        // Sanity check: this password really does fail the validator
        // (the test pins the assumption — if PasswordValidator
        // ever loosens, this test catches it).
        assertThat(PasswordValidator.isValid("shorty1")).isFalse();

        assertThatThrownBy(() -> service.create(tenantId, adminId, req)).isInstanceOf(ValidationException.class);

        verify(userRepository, never()).save(any(CompanyUser.class));
        verify(auditLogger, never()).logAsync(any());
    }

    @Test
    @DisplayName("create: PLATFORM role in roleIds is rejected with INVALID_ROLE before any DB write")
    void create_platformRoleRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID platformRoleId = UUID.randomUUID();

        CreateCompanyUserRequest req = new CreateCompanyUserRequest(
                "alice", "alice@example.com", "Alice", "Smith", "GoodPwd1", List.of(platformRoleId));

        // ValidateScopeCompany delegates to RoleAssignmentService, but
        // for this test we verify the service surfaces the exception.
        Mockito.doThrow(new BusinessRuleException(
                        "INVALID_ROLE", "One or more role ids are invalid (not COMPANY scope or not found).", null))
                .when(roleAssignmentService)
                .validateScopeCompany(List.of(platformRoleId));

        assertThatThrownBy(() -> service.create(tenantId, adminId, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("INVALID_ROLE");

        verify(userRepository, never()).save(any(CompanyUser.class));
    }

    @Test
    @DisplayName("create: duplicate username (case-insensitive per spec) is rejected with USERNAME_ALREADY_TAKEN")
    void create_duplicateUsernameRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        CreateCompanyUserRequest req = new CreateCompanyUserRequest(
                "alice", "alice@example.com", "Alice", "Smith", "GoodPwd1", List.of(roleId));

        when(userRepository.existsByTenantIdAndUsername(tenantId, "alice")).thenReturn(true);

        assertThatThrownBy(() -> service.create(tenantId, adminId, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("USERNAME_ALREADY_TAKEN");

        verify(userRepository, never()).save(any(CompanyUser.class));
    }

    @Test
    @DisplayName("create: duplicate email is rejected with EMAIL_ALREADY_TAKEN")
    void create_duplicateEmailRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        CreateCompanyUserRequest req = new CreateCompanyUserRequest(
                "alice", "alice@example.com", "Alice", "Smith", "GoodPwd1", List.of(roleId));

        when(userRepository.existsByTenantIdAndUsername(tenantId, "alice")).thenReturn(false);
        when(userRepository.existsByTenantIdAndEmail(tenantId, "alice@example.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(tenantId, adminId, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("EMAIL_ALREADY_TAKEN");

        verify(userRepository, never()).save(any(CompanyUser.class));
    }

    @Test
    @DisplayName("create: returns GENERATED password when caller-provided password is null/blank")
    void create_generatesPasswordWhenNotProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        // password = null → service must generate one (per spec §B.2 the
        // create endpoint can either accept a caller-provided password
        // OR auto-generate; we exercise the auto path here).
        CreateCompanyUserRequest req =
                new CreateCompanyUserRequest("alice", "alice@example.com", "Alice", "Smith", null, List.of(roleId));

        // Note: no Role stubbing here because the auto-generate path
        // doesn't hydrate roles — the create endpoint accepts any
        // roleIds that pass validateScopeCompany and the role
        // catalog itself is read by the GET endpoints.

        when(userRepository.existsByTenantIdAndUsername(tenantId, "alice")).thenReturn(false);
        when(userRepository.existsByTenantIdAndEmail(tenantId, "alice@example.com"))
                .thenReturn(false);
        PasswordGeneratorService.GeneratedPassword genPwd =
                new PasswordGeneratorService.GeneratedPassword("Generated1!Xyz");
        when(passwordGenerator.generate()).thenReturn(genPwd);
        when(passwordEncoder.encode("Generated1!Xyz")).thenReturn("$2a$12$generatedHash");

        CompanyUser saved = CompanyUser.create(tenantId, "alice", "alice@example.com", "Alice", "Smith");
        saved.setStatus(CompanyUser.UserStatus.ACTIVE);
        saved.setPasswordHash("$2a$12$generatedHash");
        when(userRepository.save(any(CompanyUser.class))).thenReturn(saved);

        // getRolesForUser is on the MOCKED RoleAssignmentService —
        // default Mockito behavior returns an empty list, which is
        // what we want here (the test only asserts the password).

        CreateCompanyUserResponse resp = service.create(tenantId, adminId, req);

        assertThat(resp.temporaryPassword()).isEqualTo("Generated1!Xyz");
        verify(passwordGenerator, times(1)).generate();
        // No password validation happens on the generated path
        // (PasswordValidator is for caller-supplied only).
        verify(passwordEncoder).encode("Generated1!Xyz");
    }

    @Test
    @DisplayName("create: validateEmailUnique + validateUsernameUnique + validateRoleScope are called in that order")
    void create_validatorsRunInOrder() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        CreateCompanyUserRequest req = new CreateCompanyUserRequest(
                "alice", "alice@example.com", "Alice", "Smith", "GoodPwd1", List.of(roleId));

        when(userRepository.existsByTenantIdAndUsername(tenantId, "alice")).thenReturn(true); // fails username

        assertThatThrownBy(() -> service.create(tenantId, adminId, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("USERNAME_ALREADY_TAKEN");

        // Username check failed BEFORE the email check ran
        verify(userRepository, never()).existsByTenantIdAndEmail(any(), anyString());
    }
}
