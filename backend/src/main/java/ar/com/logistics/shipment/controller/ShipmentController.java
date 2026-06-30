package ar.com.logistics.shipment.controller;

import ar.com.logistics.auth.dto.PageResponse;
import ar.com.logistics.auth.security.JwtAuthentication;
import ar.com.logistics.shipment.domain.Address;
import ar.com.logistics.shipment.domain.Customer;
import ar.com.logistics.shipment.domain.Package;
import ar.com.logistics.shipment.domain.Shipment;
import ar.com.logistics.shipment.domain.TrackingEvent;
import ar.com.logistics.shipment.repository.company.AddressRepository;
import ar.com.logistics.shipment.repository.company.BranchRepository;
import ar.com.logistics.shipment.repository.company.CustomerRepository;
import ar.com.logistics.shipment.repository.company.ServiceLevelRepository;
import ar.com.logistics.shipment.service.ShipmentService;
import ar.com.logistics.shipment.service.ShipmentService.CreatePackageRequest;
import ar.com.logistics.shipment.service.ShipmentService.CreateShipmentRequest;
import ar.com.logistics.shipment.service.ShipmentService.CreateShipmentResponse;
import ar.com.logistics.shipment.service.ShipmentService.ShipmentDetailDto;
import ar.com.logistics.shipment.service.ShipmentService.ShipmentListFilters;
import ar.com.logistics.shipment.service.ShipmentService.UpdateShipmentRequest;
import ar.com.logistics.tenant.TenantContext;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
 * Lifecycle endpoints for {@link Shipment} under
 * {@code /api/v1/shipments/**} (spec §B + §C, etapa-3-envios PR-4
 * Chunk B Part 2).
 *
 * <p>Eight endpoints:
 * <ul>
 *   <li>{@code POST  /}                  — create (ADMIN+OPERATOR)</li>
 *   <li>{@code GET   /}                  — list paginated (ADMIN+OPERATOR+VIEWER)</li>
 *   <li>{@code GET   /{id}}              — detail (ADMIN+OPERATOR+VIEWER)</li>
 *   <li>{@code GET   /{id}/timeline}     — event history (ADMIN+OPERATOR+VIEWER)</li>
 *   <li>{@code PATCH /{id}}              — partial update, only PRE_ALTA (ADMIN+OPERATOR)</li>
 *   <li>{@code POST  /{id}/validate}     — PRE_ALTA → CREADO (ADMIN+OPERATOR)</li>
 *   <li>{@code POST  /{id}/reject}       — PRE_ALTA → CANCELADO (ADMIN+OPERATOR)</li>
 *   <li>{@code POST  /{id}/cancel}       — any → CANCELADO (ADMIN only)</li>
 * </ul>
 *
 * <p>Tenant resolution: tenant id from {@link TenantContext} (set by
 * the auth filter). The actor id ({@code currentUserId()}) is passed
 * to the service for audit + createdBy/updatedBy. No tenant id is
 * ever read from the URL.
 *
 * <p>The detail DTOs enrich the entity references (sender, receiver,
 * delivery address, branches, service level) by joining against the
 * lookup repositories so the frontend gets a single round-trip. The
 * lookups are tenant-scoped on the controller side (we filter
 * {@code tenantId == currentTenantId()} before projecting).
 */
@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ShipmentService shipmentService;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final BranchRepository branchRepository;
    private final ServiceLevelRepository serviceLevelRepository;

    public ShipmentController(
            ShipmentService shipmentService,
            CustomerRepository customerRepository,
            AddressRepository addressRepository,
            BranchRepository branchRepository,
            ServiceLevelRepository serviceLevelRepository) {
        this.shipmentService = shipmentService;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.branchRepository = branchRepository;
        this.serviceLevelRepository = serviceLevelRepository;
    }

    // -------------------------------------------------------------------
    //  POST /                  create
    // -------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR')")
    public ResponseEntity<CreateShipmentResponseDto> create(
            @AuthenticationPrincipal JwtAuthentication auth, @Valid @RequestBody CreateShipmentRequestDto req) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId(auth);

        List<CreatePackageRequest> packages = new ArrayList<>();
        if (req.packages() != null) {
            for (PackageInput pkg : req.packages()) {
                packages.add(new CreatePackageRequest(
                        pkg.weightKg(),
                        pkg.volumeCm3(),
                        pkg.dimensionsCm(),
                        pkg.contentDescription(),
                        pkg.declaredValue(),
                        pkg.declaredCurrency(),
                        pkg.hasInsurance(),
                        pkg.insurancePremium(),
                        pkg.isFragile(),
                        pkg.isUrgent(),
                        pkg.requiresSignature(),
                        pkg.requiresIdCheck(),
                        pkg.category()));
            }
        }

        CreateShipmentRequest serviceReq = new CreateShipmentRequest(
                req.senderId(),
                req.receiverId(),
                req.deliveryAddressId(),
                req.shipmentType(),
                null, // originBranchCodeHint — ignored in v1
                req.deliveryMode(),
                req.deliveryInstructions(),
                req.paymentType(),
                packages,
                req.validateNow());

        CreateShipmentResponse resp = shipmentService.create(tenantId, adminId, serviceReq);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateShipmentResponseDto(toSummaryDto(resp.shipment()), toPackageDtos(resp.packages())));
    }

    // -------------------------------------------------------------------
    //  GET /                   list (search, status, date range, pagination)
    // -------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR','COMPANY_VIEWER')")
    public ResponseEntity<PageResponse<ShipmentSummaryDto>> list(
            @AuthenticationPrincipal JwtAuthentication auth,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "dateFrom", required = false) Instant dateFrom,
            @RequestParam(name = "dateTo", required = false) Instant dateTo,
            @RequestParam(name = "search", required = false) String search) {
        UUID tenantId = currentTenantId();
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        // Page is 1-indexed from the client (matches the frontend
        // convention). Convert to Spring Data's 0-indexed page for
        // PageRequest.of(safePage, safeSize, sort). Without this
        // conversion, page=1 maps to offset=10, missing the first
        // page of results.
        int safePage = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(safePage, safeSize, parseSort(sort));
        ShipmentListFilters filters = new ShipmentListFilters(status, dateFrom, dateTo, search);
        Page<Shipment> rows = shipmentService.list(tenantId, filters, pageable);
        return ResponseEntity.ok(PageResponse.of(rows.map(this::toSummaryDto)));
    }

    // -------------------------------------------------------------------
    //  GET /{id}               detail
    // -------------------------------------------------------------------

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR','COMPANY_VIEWER')")
    public ResponseEntity<ShipmentDetailResponseDto> get(
            @AuthenticationPrincipal JwtAuthentication auth, @PathVariable("id") UUID id) {
        UUID tenantId = currentTenantId();
        ShipmentDetailDto detail = shipmentService.get(tenantId, id);
        return ResponseEntity.ok(new ShipmentDetailResponseDto(toFullDetailDto(detail)));
    }

    // -------------------------------------------------------------------
    //  GET /{id}/timeline      event history
    // -------------------------------------------------------------------

    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR','COMPANY_VIEWER')")
    public ResponseEntity<List<TrackingEventDto>> getTimeline(
            @AuthenticationPrincipal JwtAuthentication auth, @PathVariable("id") UUID id) {
        UUID tenantId = currentTenantId();
        List<TrackingEvent> events = shipmentService.getTimeline(tenantId, id);
        List<TrackingEventDto> dtos = new ArrayList<>();
        for (TrackingEvent e : events) {
            dtos.add(toEventDto(e));
        }
        return ResponseEntity.ok(dtos);
    }

    // -------------------------------------------------------------------
    //  PATCH /{id}             partial update (PRE_ALTA only)
    // -------------------------------------------------------------------

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR')")
    public ResponseEntity<ShipmentDetailResponseDto> update(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateShipmentRequestDto req) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId(auth);
        UpdateShipmentRequest serviceReq = new UpdateShipmentRequest(
                req.deliveryInstructions(), req.paymentType(), req.deliveryMode(), req.promisedDeliveryDate());
        shipmentService.update(tenantId, adminId, id, serviceReq);
        // Re-fetch so the response carries packages + latest event
        // (service.update returns the bare Shipment).
        ShipmentDetailDto detail = shipmentService.get(tenantId, id);
        return ResponseEntity.ok(new ShipmentDetailResponseDto(toFullDetailDto(detail)));
    }

    // -------------------------------------------------------------------
    //  POST /{id}/validate     PRE_ALTA → CREADO
    // -------------------------------------------------------------------

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR')")
    public ResponseEntity<ShipmentDetailResponseDto> validate(
            @AuthenticationPrincipal JwtAuthentication auth, @PathVariable("id") UUID id) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId(auth);
        ShipmentDetailDto detail = shipmentService.validate(tenantId, adminId, id);
        return ResponseEntity.ok(new ShipmentDetailResponseDto(toFullDetailDto(detail)));
    }

    // -------------------------------------------------------------------
    //  POST /{id}/reject       PRE_ALTA → CANCELADO
    // -------------------------------------------------------------------

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR')")
    public ResponseEntity<ShipmentDetailResponseDto> reject(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable("id") UUID id,
            @Valid @RequestBody RejectRequest req) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId(auth);
        ShipmentDetailDto detail = shipmentService.reject(tenantId, adminId, id, req.rejectionReason());
        return ResponseEntity.ok(new ShipmentDetailResponseDto(toFullDetailDto(detail)));
    }

    // -------------------------------------------------------------------
    //  POST /{id}/cancel       any non-final → CANCELADO  (ADMIN only)
    // -------------------------------------------------------------------

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ShipmentDetailResponseDto> cancel(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable("id") UUID id,
            @Valid @RequestBody CancelRequest req) {
        UUID tenantId = currentTenantId();
        UUID adminId = currentUserId(auth);
        ShipmentDetailDto detail = shipmentService.cancel(tenantId, adminId, id, req.reason());
        return ResponseEntity.ok(new ShipmentDetailResponseDto(toFullDetailDto(detail)));
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

    /** Summary row for {@code GET /shipments}. */
    private ShipmentSummaryDto toSummaryDto(Shipment s) {
        String senderName = lookupCustomerName(s.getSenderId());
        String receiverName = lookupCustomerName(s.getReceiverId());
        return new ShipmentSummaryDto(
                s.getId(),
                s.getTrackingId(),
                s.getCode(),
                s.getStatus(),
                senderName,
                receiverName,
                s.getTotalWeightKg(),
                s.getCreatedAt());
    }

    /** Build the full detail DTO with all FK enrichments. */
    private FullShipmentDetailDto toFullDetailDto(ShipmentDetailDto detail) {
        Shipment s = detail.shipment();
        return new FullShipmentDetailDto(
                s.getId(),
                s.getTrackingId(),
                s.getCode(),
                s.getStatus(),
                toCustomerRef(s.getSenderId()),
                toCustomerRef(s.getReceiverId()),
                toAddressRef(s.getDeliveryAddressId()),
                toBranchRef(s.getOriginBranchId()),
                toBranchRef(s.getDestinationBranchId()),
                toServiceLevelRef(s.getServiceLevelId()),
                s.getPaymentType(),
                s.getDeliveryMode(),
                s.getDeliveryInstructions(),
                toPackageDtos(detail.packages()),
                s.getTotalWeightKg(),
                s.getTotalCost(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                detail.latestEvent() == null ? null : toEventDto(detail.latestEvent()));
    }

    private List<PackageDto> toPackageDtos(List<Package> packages) {
        List<PackageDto> out = new ArrayList<>();
        if (packages == null) {
            return out;
        }
        for (Package p : packages) {
            out.add(new PackageDto(
                    p.getId(),
                    p.getQrCode(),
                    p.getStatus(),
                    p.getWeightKg(),
                    p.getVolumeCm3(),
                    p.getDimensionsCm(),
                    p.getContentDescription(),
                    p.getDeclaredValue(),
                    p.getDeclaredCurrency(),
                    p.isFragile(),
                    p.isUrgent(),
                    p.isRequiresSignature(),
                    p.isRequiresIdCheck(),
                    p.getCategory()));
        }
        return out;
    }

    private TrackingEventDto toEventDto(TrackingEvent e) {
        return new TrackingEventDto(
                e.getId(),
                e.getPackageId(),
                e.getEventType(),
                e.getEventTimestamp(),
                e.getBranchId(),
                e.getUserId(),
                e.getEventSource(),
                e.getMetadata(),
                e.getCreatedAt());
    }

    private CustomerRef toCustomerRef(UUID id) {
        if (id == null) {
            return null;
        }
        return customerRepository
                .findById(id)
                .filter(c -> c.getTenantId() != null && c.getTenantId().equals(currentTenantId()))
                .map(c -> new CustomerRef(c.getId(), customerDisplayName(c)))
                .orElse(null);
    }

    private AddressRef toAddressRef(UUID id) {
        if (id == null) {
            return null;
        }
        return addressRepository
                .findById(id)
                .filter(a -> a.getTenantId() != null && a.getTenantId().equals(currentTenantId()))
                .map(a -> new AddressRef(a.getId(), displayAddress(a)))
                .orElse(null);
    }

    private BranchRef toBranchRef(UUID id) {
        if (id == null) {
            return null;
        }
        return branchRepository
                .findById(id)
                .filter(b -> b.getTenantId() != null && b.getTenantId().equals(currentTenantId()))
                .map(b -> new BranchRef(b.getId(), b.getCode(), b.getName()))
                .orElse(null);
    }

    private ServiceLevelRef toServiceLevelRef(UUID id) {
        if (id == null) {
            return null;
        }
        return serviceLevelRepository
                .findById(id)
                .filter(sl -> sl.getTenantId() != null && sl.getTenantId().equals(currentTenantId()))
                .map(sl -> new ServiceLevelRef(sl.getId(), sl.getCode(), sl.getName()))
                .orElse(null);
    }

    private String lookupCustomerName(UUID id) {
        if (id == null) {
            return null;
        }
        return customerRepository
                .findById(id)
                .filter(c -> c.getTenantId() != null && c.getTenantId().equals(currentTenantId()))
                .map(this::customerDisplayName)
                .orElse(null);
    }

    private String customerDisplayName(Customer c) {
        if ("JURIDICA".equals(c.getPersonType()) && c.getRazonSocial() != null) {
            return c.getRazonSocial();
        }
        return ((c.getFirstName() == null ? "" : c.getFirstName())
                        + " "
                        + (c.getLastName() == null ? "" : c.getLastName()))
                .trim();
    }

    private static String displayAddress(Address a) {
        return ((a.getStreet() == null ? "" : a.getStreet())
                        + " "
                        + (a.getNumber() == null ? "" : a.getNumber())
                        + ", "
                        + (a.getCity() == null ? "" : a.getCity()))
                .trim();
    }

    // -------------------------------------------------------------------
    //  DTOs (nested records)
    // -------------------------------------------------------------------

    /** Summary row for {@code GET /shipments}. */
    public record ShipmentSummaryDto(
            UUID id,
            String trackingId,
            String code,
            String status,
            String senderName,
            String receiverName,
            BigDecimal totalWeightKg,
            Instant createdAt) {}

    /** Wire shape for one package inside a shipment detail. */
    public record PackageDto(
            UUID id,
            String qrCode,
            String status,
            BigDecimal weightKg,
            BigDecimal volumeCm3,
            String dimensionsCm,
            String contentDescription,
            BigDecimal declaredValue,
            String declaredCurrency,
            boolean fragile,
            boolean urgent,
            boolean requiresSignature,
            boolean requiresIdCheck,
            String category) {}

    /** One customer reference projected to the wire shape. */
    public record CustomerRef(UUID id, String name) {}

    /** One address reference projected to the wire shape. */
    public record AddressRef(UUID id, String displayLabel) {}

    /** One branch reference projected to the wire shape. */
    public record BranchRef(UUID id, String code, String name) {}

    /** One service-level reference projected to the wire shape. */
    public record ServiceLevelRef(UUID id, String code, String name) {}

    /** Full detail DTO — wire shape for get/create/update/validate/reject/cancel. */
    public record FullShipmentDetailDto(
            UUID id,
            String trackingId,
            String code,
            String status,
            CustomerRef sender,
            CustomerRef receiver,
            AddressRef deliveryAddress,
            BranchRef originBranch,
            BranchRef destinationBranch,
            ServiceLevelRef serviceLevel,
            String paymentType,
            String deliveryMode,
            String deliveryInstructions,
            List<PackageDto> packages,
            BigDecimal totalWeightKg,
            BigDecimal totalCost,
            Instant createdAt,
            Instant updatedAt,
            TrackingEventDto latestEvent) {}

    /** Response envelope for {@code GET /shipments/{id}} + create/update/transition responses. */
    public record ShipmentDetailResponseDto(FullShipmentDetailDto shipment) {}

    /** Response envelope for {@code POST /shipments}. */
    public record CreateShipmentResponseDto(ShipmentSummaryDto shipment, List<PackageDto> packages) {}

    /** One tracking-event wire shape (shared with TrackingEventController). */
    public record TrackingEventDto(
            UUID id,
            UUID packageId,
            String eventType,
            Instant eventTimestamp,
            UUID branchId,
            UUID userId,
            String eventSource,
            String metadata,
            Instant createdAt) {}

    /** Wire shape for one package inside a create request. */
    public record PackageInput(
            BigDecimal weightKg,
            BigDecimal volumeCm3,
            String dimensionsCm,
            String contentDescription,
            BigDecimal declaredValue,
            String declaredCurrency,
            boolean hasInsurance,
            BigDecimal insurancePremium,
            boolean isFragile,
            boolean isUrgent,
            boolean requiresSignature,
            boolean requiresIdCheck,
            String category) {}

    /** Wire DTO for {@code POST /shipments}. */
    public record CreateShipmentRequestDto(
            UUID senderId,
            UUID receiverId,
            UUID deliveryAddressId,
            String shipmentType,
            String deliveryMode,
            String deliveryInstructions,
            String paymentType,
            List<PackageInput> packages,
            boolean validateNow) {}

    /** Wire DTO for {@code PATCH /shipments/{id}}. */
    public record UpdateShipmentRequestDto(
            String deliveryInstructions, String paymentType, String deliveryMode, LocalDate promisedDeliveryDate) {}

    /** Wire DTO for {@code POST /shipments/{id}/reject}. */
    public record RejectRequest(String rejectionReason) {}

    /** Wire DTO for {@code POST /shipments/{id}/cancel}. */
    public record CancelRequest(String reason) {}
}
