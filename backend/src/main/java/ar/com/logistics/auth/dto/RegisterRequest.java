package ar.com.logistics.auth.dto;

import ar.com.logistics.tenant.domain.TaxType;
import ar.com.logistics.tenant.reference.Province;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/register}. The endpoint
 * is the single self-service onboarding entry point: a tenant plus
 * its first admin user in one atomic transaction.
 *
 * <p>Validation is enforced in two layers:
 * <ol>
 *   <li><b>Bean Validation</b> on the fields of the nested records
 *       (lengths, regexes, required). Failures surface as
 *       {@code 400 VALIDATION_ERROR} with a per-field details map
 *       (see {@code GlobalExceptionHandler}).</li>
 *   <li><b>Domain validators</b> in {@code RegistrationService}
 *       (CuitValidator mod-11, SlugValidator, PasswordValidator,
 *       UsernameValidator). The bean-validation layer is the
 *       first line of defence; the service layer is the source of
 *       truth for the complex rules (CUIT check-digit, reserved
 *       slugs, password policy).</li>
 * </ol>
 */
public record RegisterRequest(@Valid @NotNull CompanyDto company, @Valid @NotNull AdminDto admin) {

    /**
     * Tenant (company) block of the registration payload. The
     * {@code address} sub-record is its own type so the validation
     * annotations live close to the fields they apply to.
     */
    public record CompanyDto(
            @NotBlank @Size(min = 3, max = 255) String legalName,
            @Size(max = 255) String commercialName,
            @NotBlank String cuit,
            @NotNull TaxType taxType,
            @NotBlank @Size(min = 2, max = 12) String slug,
            @Email @NotBlank @Size(max = 255) String contactEmail,
            @Size(max = 50) String contactPhone,
            @NotNull @Valid AddressDto address) {}

    /**
     * Address block. The country is a 2-letter ISO code; the
     * province is one of the 24 {@link Province} enum constants.
     * Floor / apartment / postalCode are optional in the PRD
     * (not all Argentine addresses have them).
     */
    public record AddressDto(
            @NotBlank @Size(min = 2, max = 2) String country,
            @NotNull Province province,
            @Size(max = 100) String city,
            @NotBlank @Size(max = 255) String line,
            @NotBlank @Size(max = 20) String number,
            @Size(max = 10) String floor,
            @Size(max = 10) String apartment,
            @Size(max = 20) String postalCode) {}

    /**
     * First admin user block. The password is plain text on the
     * wire; the service BCrypts it before persisting. The
     * {@code username} and {@code email} are per-tenant unique
     * (checked in the service against {@code company_users}).
     */
    public record AdminDto(
            @NotBlank @Size(min = 3, max = 30) String username,
            @Email @NotBlank @Size(max = 255) String email,
            @NotBlank @Size(min = 2, max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(min = 8, max = 128) String password) {}
}
