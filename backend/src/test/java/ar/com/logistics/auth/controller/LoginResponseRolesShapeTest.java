package ar.com.logistics.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.logistics.auth.dto.LoginResponse;
import ar.com.logistics.auth.dto.MeResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the etapa-2 PR-3 breaking change:
 * {@code LoginResponse.User.role: String → roles: List<String>} and
 * {@code MeResponse.User.role: String → roles: List<String>}.
 *
 * <p>The test pins the wire shape contract:
 * <ul>
 *   <li>The {@code roles} field is a non-null {@link List} of role
 *       names (never a single string, never null).</li>
 *   <li>Single-role users receive a singleton list (the migration
 *       is contract-preserving for v1 tokens).</li>
 *   <li>Multi-role users see every role in the list, in the order
 *       assigned (caller's contract from {@code RoleAssignmentService}).</li>
 *   <li>The {@code scope} and other fields stay identical — only
 *       the role field changed.</li>
 * </ul>
 *
 * <p>Strict TDD: every test below pins a piece of the contract
 * from spec §D before the implementation exists.
 */
class LoginResponseRolesShapeTest {

    @Test
    @DisplayName("LoginResponse.User exposes roles as a non-null List<String> (the new wire contract)")
    void loginResponse_userHasRolesList() {
        LoginResponse.User user = new LoginResponse.User(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "acme",
                "alice",
                "alice@example.com",
                "Alice",
                "Smith",
                List.of("COMPANY_ADMIN"),
                "COMPANY",
                true);

        assertThat(user.roles())
                .as("the singular role field is GONE; replaced by roles[]")
                .isInstanceOf(List.class)
                .containsExactly("COMPANY_ADMIN");
    }

    @Test
    @DisplayName("Single-role users receive a singleton list (migration is contract-preserving)")
    void singleRoleWrappedInSingleton() {
        LoginResponse.User user = new LoginResponse.User(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "acme",
                "bob",
                "bob@example.com",
                "Bob",
                "Doe",
                List.of("COMPANY_OPERATOR"),
                "COMPANY",
                false);

        assertThat(user.roles()).hasSize(1);
        assertThat(user.roles().get(0)).isEqualTo("COMPANY_OPERATOR");
    }

    @Test
    @DisplayName("Multi-role users see every role in the list, order preserved")
    void multiRoleOrderPreserved() {
        List<String> sourceRoles = List.of("COMPANY_ADMIN", "COMPANY_OPERATOR", "COMPANY_DRIVER");

        LoginResponse.User user = new LoginResponse.User(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "acme",
                "carol",
                "carol@example.com",
                "Carol",
                "X",
                sourceRoles,
                "COMPANY",
                true);

        assertThat(user.roles()).containsExactlyElementsOf(sourceRoles);
    }

    @Test
    @DisplayName("LoginResponse does NOT have a singular 'role' field on the wire (regression guard)")
    void loginResponseHasNoRoleField() {
        // Use reflection so a future accidental addition of `role`
        // would be caught at compile/verify time. The contract is
        // "role is gone from the wire — only roles[] remains".
        var recordComponents = LoginResponse.User.class.getRecordComponents();
        assertThat(recordComponents).extracting("name").doesNotContain("role");
        assertThat(recordComponents).extracting("name").contains("roles");
    }

    @Test
    @DisplayName("MeResponse.User exposes roles as a non-null List<String> (the new wire contract)")
    void meResponse_userHasRolesList() {
        MeResponse.User user = new MeResponse.User(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "acme",
                "alice",
                List.of("COMPANY_ADMIN", "COMPANY_OPERATOR"),
                "COMPANY",
                900L);

        assertThat(user.roles()).containsExactly("COMPANY_ADMIN", "COMPANY_OPERATOR");
    }

    @Test
    @DisplayName("MeResponse does NOT have a singular 'role' field on the wire (regression guard)")
    void meResponseHasNoRoleField() {
        var recordComponents = MeResponse.User.class.getRecordComponents();
        assertThat(recordComponents).extracting("name").doesNotContain("role");
        assertThat(recordComponents).extracting("name").contains("roles");
    }
}
