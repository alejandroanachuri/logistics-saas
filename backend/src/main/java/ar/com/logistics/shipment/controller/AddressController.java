package ar.com.logistics.shipment.controller;

import ar.com.logistics.auth.security.JwtAuthentication;
import ar.com.logistics.shipment.domain.Address;
import ar.com.logistics.shipment.service.AddressService;
import ar.com.logistics.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD endpoints for {@link Address} under {@code /api/v1/addresses/**}
 * (spec §B, etapa-3-envios PR-4 Chunk B Part 1).
 *
 * <p>Three endpoints:
 * <ul>
 *   <li>{@code GET    /{id}}    — detail</li>
 *   <li>{@code POST   /}        — create</li>
 *   <li>{@code PATCH  /{id}}    — partial update</li>
 * </ul>
 *
 * <p>Disable is exposed in PR-3b together with the shipment-customer
 * cascade (not in this chunk — it ships in the second PR-4 chunk
 * alongside the Customer/Address attachment flows).
 *
 * <p>Auth: any authenticated company user can read / write addresses —
 * there is no per-role gating on inert reference data.
 */
@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    // -------------------------------------------------------------------
    //  GET /{id}             detail
    // -------------------------------------------------------------------

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR','COMPANY_VIEWER')")
    public ResponseEntity<AddressDto> get(
            @AuthenticationPrincipal JwtAuthentication auth, @PathVariable("id") UUID id) {
        UUID tenantId = currentTenantId();
        return ResponseEntity.ok(toDto(addressService.get(tenantId, id)));
    }

    // -------------------------------------------------------------------
    //  POST /                create
    // -------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR')")
    public ResponseEntity<AddressDto> create(
            @AuthenticationPrincipal JwtAuthentication auth, @Valid @RequestBody CreateAddressRequest req) {
        UUID tenantId = currentTenantId();
        AddressService.CreateAddressRequest serviceReq = new AddressService.CreateAddressRequest(
                req.street(),
                req.number(),
                req.floor(),
                req.apartment(),
                req.city(),
                req.province(),
                req.postalCode(),
                req.reference(),
                req.country());
        Address created = addressService.create(tenantId, serviceReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    // -------------------------------------------------------------------
    //  PATCH /{id}           partial update
    // -------------------------------------------------------------------

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR')")
    public ResponseEntity<AddressDto> update(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateAddressRequest req) {
        UUID tenantId = currentTenantId();
        AddressService.UpdateAddressRequest serviceReq = new AddressService.UpdateAddressRequest(
                req.street(),
                req.number(),
                req.floor(),
                req.apartment(),
                req.city(),
                req.province(),
                req.postalCode(),
                req.reference(),
                req.country());
        Address updated = addressService.update(tenantId, id, serviceReq);
        return ResponseEntity.ok(toDto(updated));
    }

    // -------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------

    private static UUID currentTenantId() {
        return TenantContext.currentTenantId();
    }

    private AddressDto toDto(Address a) {
        return new AddressDto(
                a.getId(),
                a.getStreet(),
                a.getNumber(),
                a.getFloor(),
                a.getApartment(),
                a.getCity(),
                a.getProvince(),
                a.getPostalCode(),
                a.getReference(),
                a.getCountry());
    }

    // -------------------------------------------------------------------
    //  DTOs (nested records)
    // -------------------------------------------------------------------

    /** Wire DTO for {@code GET /{id}} and the create / update responses. */
    public record AddressDto(
            UUID id,
            String street,
            String number,
            String floor,
            String apartment,
            String city,
            String province,
            String postalCode,
            String reference,
            String country) {}

    /** Wire DTO for {@code POST /}. */
    public record CreateAddressRequest(
            String street,
            String number,
            String floor,
            String apartment,
            String city,
            String province,
            String postalCode,
            String reference,
            String country) {}

    /** Wire DTO for {@code PATCH /{id}} — all fields optional. */
    public record UpdateAddressRequest(
            String street,
            String number,
            String floor,
            String apartment,
            String city,
            String province,
            String postalCode,
            String reference,
            String country) {}
}
