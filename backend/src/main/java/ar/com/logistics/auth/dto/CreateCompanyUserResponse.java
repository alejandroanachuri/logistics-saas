package ar.com.logistics.auth.dto;

import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/company-users}. Wraps the
 * {@link CompanyUserDetailDto} (so the admin can navigate straight
 * to the new user's detail page) plus the cleartext
 * {@code temporaryPassword} shown exactly once and the warning
 * string the frontend renders in the password-reveal modal.
 *
 * <p>{@code passwordWarning} is intentionally a separate field so
 * the i18n string lives server-side and the frontend doesn't have
 * to mirror it. It is the same constant for create + reset-password.
 */
public record CreateCompanyUserResponse(CompanyUserDetailDto user, String temporaryPassword, String passwordWarning) {

    /** Constant warning shown in the password-reveal modal. Matches the one in {@code CompanyUsersService}. */
    public static final String DEFAULT_PASSWORD_WARNING =
            "Compartí esta contraseña con el usuario por un canal seguro. No se volverá a mostrar.";

    /** Convenience factory used by the controller when wrapping the service's result. */
    public static CreateCompanyUserResponse of(CompanyUserDetailDto user, String temporaryPassword) {
        return new CreateCompanyUserResponse(user, temporaryPassword, DEFAULT_PASSWORD_WARNING);
    }

    /** Convenience for tests / callers that already have the warning string. */
    public static CreateCompanyUserResponse of(CompanyUserDetailDto user, String temporaryPassword, String warning) {
        return new CreateCompanyUserResponse(user, temporaryPassword, warning);
    }

    /** Dummy UUID used by tests that don't care about the user shape. */
    public static UUID ignoreUserId() {
        return new UUID(0L, 0L);
    }
}
