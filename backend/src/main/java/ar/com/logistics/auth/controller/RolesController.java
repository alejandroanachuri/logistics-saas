package ar.com.logistics.auth.controller;

import ar.com.logistics.auth.dto.RoleDto;
import ar.com.logistics.auth.service.RoleAssignmentService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only role-catalog endpoint for the frontend. Returns the
 * COMPANY-scope role list (4 seeded roles: ADMIN, OPERATOR, DRIVER,
 * VIEWER) so the create/edit user screens can populate their
 * multi-select without hard-coding role names.
 *
 * <p>PLATFORM-scope roles are NOT returned — only COMPANY-scope
 * per spec §B.8. The query parameter {@code scope} is accepted for
 * forward-compatibility but only COMPANY is wired in v1.
 *
 * <p>Auth: {@code @PreAuthorize("hasRole('COMPANY_ADMIN')")} — the
 * role catalog is admin-only because non-admins have no UI to
 * assign roles and the endpoint exists for the admin's multi-select.
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RolesController {

    private final RoleAssignmentService roleAssignmentService;

    public RolesController(RoleAssignmentService roleAssignmentService) {
        this.roleAssignmentService = roleAssignmentService;
    }

    @GetMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<List<RoleDto>> listCompanyRoles(
            @RequestParam(name = "scope", defaultValue = "COMPANY") String scope) {
        // v1: only the COMPANY scope is wired. A future PLATFORM-side
        // variant would route to a separate service; the query
        // parameter is accepted here so the frontend can prepare for
        // the v2 expansion without a contract change.
        if (!"COMPANY".equalsIgnoreCase(scope)) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(roleAssignmentService.listCompanyRoles());
    }
}
