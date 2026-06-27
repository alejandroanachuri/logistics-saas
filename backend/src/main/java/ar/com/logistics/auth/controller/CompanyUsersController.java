package ar.com.logistics.auth.controller;

import ar.com.logistics.auth.dto.CompanyUserDetailDto;
import ar.com.logistics.auth.dto.CompanyUserSummaryDto;
import ar.com.logistics.auth.dto.CreateCompanyUserRequest;
import ar.com.logistics.auth.dto.CreateCompanyUserResponse;
import ar.com.logistics.auth.dto.PageResponse;
import ar.com.logistics.auth.dto.ResetPasswordResponse;
import ar.com.logistics.auth.dto.UpdateCompanyUserRequest;
import ar.com.logistics.auth.service.CompanyUsersService;
import ar.com.logistics.auth.service.RoleAssignmentService.RoleRef;
import ar.com.logistics.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The 7 admin endpoints under {@code /api/v1/company-users/**}
 * (spec §B.1–B.7). All gated by
 * {@code @PreAuthorize("hasRole('COMPANY_ADMIN')")} — the
 * business rules (first admin, last admin, self-edit) live in
 * {@code CompanyUsersService} and surface as the 4xx codes
 * mapped by {@code GlobalExceptionHandler}.
 *
 * <p>Tenant resolution: the caller's {@code tenantId} is read
 * from the {@link TenantContext} (set by the auth filter on
 * every request). The admin id comes from the JWT subject
 * (resolved by the controller from the {@link Authentication}).
 * No tenant id is ever taken from the URL — the URL path only
 * carries the user id.
 *
 * <p>Mapping from service records to DTOs: every
 * {@link CompanyUsersService.CompanyUserDetail} is projected to
 * {@link CompanyUserDetailDto} before being returned (the
 * service carries internal fields like {@code isFirstAdmin}
 * via {@link CompanyUsersService.CompanyUserSummary} but the
 * wire shape is owned by the DTO). {@code List<RoleRef>} from
 * the service is mapped to {@code List<RoleDto>} via the
 * {@link #toRoleDto} helper.
 */
@RestController
@RequestMapping("/api/v1/company-users")
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public class CompanyUsersController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CompanyUsersService service;

    public CompanyUsersController(CompanyUsersService service) {
        this.service = service;
    }

    // -------------------------------------------------------------------
    //  1. GET /                    list (filters, pagination)
    // -------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<PageResponse<CompanyUserSummaryDto>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "roleId", required = false) UUID roleId,
            @RequestParam(name = "search", required = false) String search) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId();
        // Defensive cap on page size — prevents accidental DOS by
        // a client requesting size=10000. Cap is generous (100 rows
        // per page is plenty for a small-team admin UI).
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        Sort sortSpec = parseSort(sort);
        Pageable pageable = PageRequest.of(safePage, safeSize, sortSpec);
        CompanyUsersService.ListFilters filters =
                new CompanyUsersService.ListFilters(parseStatus(status), roleId, search);
        Page<CompanyUsersService.CompanyUserSummary> rows = service.list(tenantId, filters, pageable);
        return ResponseEntity.ok(PageResponse.of(rows.map(this::toSummaryDto)));
    }

    // -------------------------------------------------------------------
    //  2. POST /                   create
    // -------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<CreateCompanyUserResponse> create(@Valid @RequestBody CreateCompanyUserRequest req) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId();
        CompanyUsersService.CreateCompanyUserResponse resp =
                service.create(tenantId, adminId, toServiceCreateRequest(req));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateCompanyUserResponse(
                        toDetailDto(resp.user()),
                        resp.temporaryPassword(),
                        CreateCompanyUserResponse.DEFAULT_PASSWORD_WARNING));
    }

    // -------------------------------------------------------------------
    //  3. GET /{id}                detail
    // -------------------------------------------------------------------

    @GetMapping("/{id}")
    public ResponseEntity<CompanyUserDetailDto> get(@PathVariable("id") UUID id) {
        UUID tenantId = currentTenantId();
        return ResponseEntity.ok(toDetailDto(service.get(tenantId, id)));
    }

    // -------------------------------------------------------------------
    //  4. PATCH /{id}              partial update
    // -------------------------------------------------------------------

    @PatchMapping("/{id}")
    public ResponseEntity<CompanyUserDetailDto> update(
            @PathVariable("id") UUID id, @Valid @RequestBody UpdateCompanyUserRequest req) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId();
        return ResponseEntity.ok(toDetailDto(service.update(
                tenantId,
                adminId,
                id,
                new CompanyUsersService.UpdateCompanyUserRequest(
                        req.firstName(), req.lastName(), req.email(), req.roleIds()))));
    }

    // -------------------------------------------------------------------
    //  5. POST /{id}/disable       soft disable (204)
    // -------------------------------------------------------------------

    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable("id") UUID id) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId();
        service.disable(tenantId, adminId, id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------
    //  6. POST /{id}/reactivate    undo soft delete
    // -------------------------------------------------------------------

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<CompanyUserDetailDto> reactivate(@PathVariable("id") UUID id) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId();
        return ResponseEntity.ok(toDetailDto(service.reactivate(tenantId, adminId, id)));
    }

    // -------------------------------------------------------------------
    //  7. POST /{id}/reset-password    one-shot cleartext
    // -------------------------------------------------------------------

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@PathVariable("id") UUID id) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId();
        CompanyUsersService.ResetPasswordResponse resp = service.resetPassword(tenantId, adminId, id);
        return ResponseEntity.ok(new ResetPasswordResponse(
                resp.userId(),
                resp.username(),
                resp.temporaryPassword(),
                CreateCompanyUserResponse.DEFAULT_PASSWORD_WARNING));
    }

    // -------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------

    private static UUID currentTenantId() {
        UUID tid = TenantContext.currentTenantId();
        org.slf4j.LoggerFactory.getLogger(CompanyUsersController.class)
                .info("[DEBUG-CONTROLLER] currentTenantId() returned {}", tid);
        return tid;
    }

    /**
     * Pull the current user id from the SecurityContext. In
     * production the principal is a {@link ar.com.logistics.auth.security.JwtAuthentication}
     * whose {@code getName()} returns the JWT subject (the user id).
     * The cast is defensive — production code never puts a
     * different Authentication type in the context.
     */
    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("No authenticated principal in SecurityContext");
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            // Fallback: the principal might be a JwtAuthentication
            // whose subject isn't a stringified UUID — should not
            // happen, but surface as 500 INTERNAL_ERROR rather than
            // a NPE.
            throw new IllegalStateException("Authenticated principal name is not a UUID: " + auth.getName(), ex);
        }
    }

    private static CompanyUsersService.ListFilters.StatusFilter parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return CompanyUsersService.ListFilters.StatusFilter.ACTIVE;
        }
        try {
            return CompanyUsersService.ListFilters.StatusFilter.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            // Unknown value — default to ACTIVE so a typo doesn't
            // accidentally expose DISABLED users.
            return CompanyUsersService.ListFilters.StatusFilter.ACTIVE;
        }
    }

    /**
     * Parse the {@code sort} query parameter (format:
     * {@code "property,direction"}) into a Spring Data
     * {@link Sort}. Accepts an unsorted {@code ""} or {@code "unsorted"}.
     */
    private static Sort parseSort(String raw) {
        if (raw == null || raw.isBlank() || "unsorted".equalsIgnoreCase(raw)) {
            return Sort.unsorted();
        }
        String[] parts = raw.split(",", 2);
        String property = parts[0].trim();
        Sort.Direction dir = Sort.Direction.DESC;
        if (parts.length == 2 && parts[1].trim().equalsIgnoreCase("asc")) {
            dir = Sort.Direction.ASC;
        }
        return Sort.by(dir, property);
    }

    /** Convert wire DTO to the service-layer request (record-to-record). */
    private static CompanyUsersService.CreateCompanyUserRequest toServiceCreateRequest(CreateCompanyUserRequest r) {
        return new CompanyUsersService.CreateCompanyUserRequest(
                r.username(), r.email(), r.firstName(), r.lastName(), r.password(), r.roleIds());
    }

    /** Convert service {@link RoleRef} to wire {@link ar.com.logistics.auth.dto.RoleDto}. */
    private ar.com.logistics.auth.dto.RoleDto toRoleDto(RoleRef ref) {
        // description is not on the service record (it stays on the
        // Role entity). The wire shape carries null for description
        // when the role is read from the junction alone. The frontend
        // handles null descriptions gracefully.
        return new ar.com.logistics.auth.dto.RoleDto(ref.id(), ref.name(), null);
    }

    private CompanyUserSummaryDto toSummaryDto(CompanyUsersService.CompanyUserSummary s) {
        List<ar.com.logistics.auth.dto.RoleDto> roles =
                s.roles().stream().map(this::toRoleDto).toList();
        return new CompanyUserSummaryDto(
                s.id(),
                s.username(),
                s.email(),
                s.firstName(),
                s.lastName(),
                s.status(),
                roles,
                s.isFirstAdmin(),
                s.lastLoginAt(),
                s.createdAt());
    }

    private CompanyUserDetailDto toDetailDto(CompanyUsersService.CompanyUserDetail d) {
        List<ar.com.logistics.auth.dto.RoleDto> roles =
                d.roles().stream().map(this::toRoleDto).toList();
        return new CompanyUserDetailDto(
                d.id(),
                d.username(),
                d.email(),
                d.firstName(),
                d.lastName(),
                d.status(),
                d.emailVerified(),
                roles,
                d.failedLoginAttempts(),
                d.lockedUntil(),
                d.lastLoginAt(),
                d.createdAt(),
                null, // updatedAt is not surfaced by the service in PR-3; field present for forward-compat
                d.isFirstAdmin());
    }
}
