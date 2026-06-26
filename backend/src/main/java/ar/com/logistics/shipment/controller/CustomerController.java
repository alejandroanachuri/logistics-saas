package ar.com.logistics.shipment.controller;

import ar.com.logistics.auth.dto.PageResponse;
import ar.com.logistics.auth.security.JwtAuthentication;
import ar.com.logistics.shipment.domain.Customer;
import ar.com.logistics.shipment.service.CustomerService;
import ar.com.logistics.shipment.service.CustomerService.CustomerListFilters;
import ar.com.logistics.tenant.TenantContext;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD endpoints for {@link Customer} under {@code /api/v1/customers/**}
 * (spec §B, etapa-3-envios PR-4 Chunk B Part 1).
 *
 * <p>Five endpoints:
 * <ul>
 *   <li>{@code GET    /}           — list (paginated, search + status filters)</li>
 *   <li>{@code GET    /{id}}       — detail</li>
 *   <li>{@code POST   /}           — create</li>
 *   <li>{@code PATCH  /{id}}       — partial update</li>
 *   <li>{@code POST   /{id}/disable} — soft-delete (ADMIN only)</li>
 * </ul>
 *
 * <p>Auth (per spec §B + PR-3 admin convention):
 * <ul>
 *   <li>List / detail — ADMIN, OPERATOR, VIEWER</li>
 *   <li>Create / update — ADMIN, OPERATOR</li>
 *   <li>Disable — ADMIN only</li>
 * </ul>
 *
 * <p>Tenant resolution: tenant id from {@link TenantContext} (set by the
 * auth filter). The actor id ({@code currentUserId()}) is passed to the
 * service for audit. No tenant id is ever read from the URL.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    // -------------------------------------------------------------------
    //  GET /                 list (search, status, pagination)
    // -------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR','COMPANY_VIEWER')")
    public ResponseEntity<PageResponse<CustomerSummaryDto>> list(
            @AuthenticationPrincipal JwtAuthentication auth,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search) {
        UUID tenantId = currentTenantId();
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, parseSort(sort));
        Page<Customer> rows = customerService.list(tenantId, new CustomerListFilters(search, status), pageable);
        return ResponseEntity.ok(PageResponse.of(rows.map(this::toSummaryDto)));
    }

    // -------------------------------------------------------------------
    //  GET /{id}             detail
    // -------------------------------------------------------------------

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR','COMPANY_VIEWER')")
    public ResponseEntity<CustomerDetailDto> get(
            @AuthenticationPrincipal JwtAuthentication auth, @PathVariable("id") UUID id) {
        UUID tenantId = currentTenantId();
        return ResponseEntity.ok(toDetailDto(customerService.get(tenantId, id)));
    }

    // -------------------------------------------------------------------
    //  POST /                create
    // -------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR')")
    public ResponseEntity<CustomerDetailDto> create(
            @AuthenticationPrincipal JwtAuthentication auth, @Valid @RequestBody CreateCustomerRequest req) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId(auth);
        CustomerService.CreateCustomerRequest serviceReq = new CustomerService.CreateCustomerRequest(
                req.personType(),
                req.firstName(),
                req.lastName(),
                req.razonSocial(),
                req.dni(),
                req.cuitCuil(),
                req.taxCondition(),
                req.phone(),
                req.email(),
                req.dataConsent(),
                req.consentDate(),
                req.consentVersion(),
                req.notes());
        Customer created = customerService.create(tenantId, adminId, serviceReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDetailDto(created));
    }

    // -------------------------------------------------------------------
    //  PATCH /{id}           partial update
    // -------------------------------------------------------------------

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR')")
    public ResponseEntity<CustomerDetailDto> update(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateCustomerRequest req) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId(auth);
        CustomerService.UpdateCustomerRequest serviceReq = new CustomerService.UpdateCustomerRequest(
                req.firstName(),
                req.lastName(),
                req.razonSocial(),
                req.taxCondition(),
                req.phone(),
                req.email(),
                req.notes());
        Customer updated = customerService.update(tenantId, adminId, id, serviceReq);
        return ResponseEntity.ok(toDetailDto(updated));
    }

    // -------------------------------------------------------------------
    //  POST /{id}/disable    soft-disable (ADMIN only)
    // -------------------------------------------------------------------

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal JwtAuthentication auth, @PathVariable("id") UUID id) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId(auth);
        customerService.disable(tenantId, adminId, id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------

    private static UUID currentTenantId() {
        return TenantContext.currentTenantId();
    }

    private static UUID currentUserId(JwtAuthentication auth) {
        if (auth != null && auth.currentUserId() != null) {
            return auth.currentUserId();
        }
        // Fallback — pull from SecurityContextHolder (the principal
        // name in production is the JWT subject). Mirrors the pattern
        // in CompanyUsersController and lets the standalone test setup
        // (which seeds SecurityContextHolder but does not resolve
        // @AuthenticationPrincipal) still see the right id.
        org.springframework.security.core.Authentication a =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (a != null && a.getName() != null) {
            try {
                return UUID.fromString(a.getName());
            } catch (IllegalArgumentException ex) {
                // not a UUID subject — leave null and let the service
                // surface the issue.
            }
        }
        return null;
    }

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

    /** Customer summary for the list view. */
    private CustomerSummaryDto toSummaryDto(Customer c) {
        // The Customer entity has no status column — soft-delete is
        // signalled by deletedAt != null. Project to ACTIVE/DISABLED
        // for the wire shape.
        String status = c.getDeletedAt() == null ? "ACTIVE" : "DISABLED";
        return new CustomerSummaryDto(c.getId(), displayName(c), c.getEmail(), status);
    }

    /** Customer detail for get / create / update responses. */
    private CustomerDetailDto toDetailDto(Customer c) {
        String status = c.getDeletedAt() == null ? "ACTIVE" : "DISABLED";
        return new CustomerDetailDto(
                c.getId(),
                c.getPersonType(),
                c.getFirstName(),
                c.getLastName(),
                c.getRazonSocial(),
                c.getDni(),
                c.getCuitCuil(),
                c.getTaxCondition(),
                c.getPhone(),
                c.getEmail(),
                c.isDataConsent(),
                c.getConsentDate(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                status);
    }

    private static String displayName(Customer c) {
        if ("JURIDICA".equals(c.getPersonType()) && c.getRazonSocial() != null) {
            return c.getRazonSocial();
        }
        return ((c.getFirstName() == null ? "" : c.getFirstName())
                        + " "
                        + (c.getLastName() == null ? "" : c.getLastName()))
                .trim();
    }

    // -------------------------------------------------------------------
    //  DTOs (nested records — small wire shape)
    // -------------------------------------------------------------------

    /** Summary row for {@code GET /customers}. */
    public record CustomerSummaryDto(UUID id, String name, String email, String status) {}

    /** Full detail for {@code GET /customers/{id}} + create/update responses. */
    public record CustomerDetailDto(
            UUID id,
            String personType,
            String firstName,
            String lastName,
            String razonSocial,
            String dni,
            String cuitCuil,
            String taxCondition,
            String phone,
            String email,
            boolean dataConsent,
            Instant consentDate,
            Instant createdAt,
            Instant updatedAt,
            String status) {}

    /** Wire DTO for {@code POST /customers}. */
    public record CreateCustomerRequest(
            String personType,
            String firstName,
            String lastName,
            String razonSocial,
            String dni,
            String cuitCuil,
            String taxCondition,
            String phone,
            String email,
            boolean dataConsent,
            Instant consentDate,
            String consentVersion,
            String notes) {}

    /** Wire DTO for {@code PATCH /customers/{id}} — all fields optional. */
    public record UpdateCustomerRequest(
            String firstName,
            String lastName,
            String razonSocial,
            String taxCondition,
            String phone,
            String email,
            String notes) {}
}
