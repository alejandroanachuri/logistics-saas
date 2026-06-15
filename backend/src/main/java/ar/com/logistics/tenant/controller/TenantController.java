package ar.com.logistics.tenant.controller;

import ar.com.logistics.auth.service.RegistrationService;
import ar.com.logistics.tenant.dto.CuitAvailabilityResponse;
import ar.com.logistics.tenant.dto.SlugAvailabilityResponse;
import ar.com.logistics.tenant.dto.UsernameAvailabilityResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant availability endpoints. These live under
 * {@code /api/v1/tenants/me} per the spec (lines 590-622) but
 * they are conceptually cross-tenant (they evaluate against the
 * entire {@code tenants} table, not the caller's tenant). The
 * {@code /me} prefix is a forward-compatibility shim: in v1 the
 * user is anonymous, but in v2 a logged-in company user would
 * see "their own slug" by this same endpoint shape.
 *
 * <p>All endpoints here are declared {@code permitAll()} in
 * {@link ar.com.logistics.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/tenants/me")
public class TenantController {

    private final RegistrationService registrationService;

    public TenantController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/slug-availability")
    public SlugAvailabilityResponse slugAvailability(@RequestParam("slug") String slug) {
        return registrationService.checkSlugAvailability(slug);
    }

    @GetMapping("/cuit-availability")
    public CuitAvailabilityResponse cuitAvailability(@RequestParam("cuit") String cuit) {
        return registrationService.checkCuitAvailability(cuit);
    }

    @GetMapping("/username-availability")
    public UsernameAvailabilityResponse usernameAvailability(
            @RequestParam("slug") String slug, @RequestParam("username") String username) {
        return registrationService.checkUsernameAvailability(slug, username);
    }
}
