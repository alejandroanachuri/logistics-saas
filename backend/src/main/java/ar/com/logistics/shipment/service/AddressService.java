package ar.com.logistics.shipment.service;

import ar.com.logistics.common.exception.BusinessRuleException;
import ar.com.logistics.shipment.domain.Address;
import ar.com.logistics.shipment.repository.company.AddressRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-tenant-aware CRUD service for {@link Address}. Addresses are
 * inert reference data attached to a {@link ar.com.logistics.shipment.domain.Customer}
 * — no audit, no business-rule validation beyond tenant scoping and
 * not-found handling.
 *
 * <p>Transaction boundary: every public method runs in a single
 * {@code @Transactional("companyTransactionManager")} block. Reads are
 * RLS-scoped via V16.
 */
@Service
public class AddressService {

    /** Canonical wire-format code (GlobalExceptionHandler maps to 404). */
    static final String CODE_ADDRESS_NOT_FOUND = "ADDRESS_NOT_FOUND";

    /** Default country per the addresses DB column (CHAR(2)). */
    static final String DEFAULT_COUNTRY = "AR";

    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    // -------------------------------------------------------------------
    //  get
    // -------------------------------------------------------------------

    @Transactional("companyTransactionManager")
    public Address get(UUID tenantId, UUID addressId) {
        return addressRepository
                .findById(addressId)
                .filter(a -> a.getTenantId() != null && a.getTenantId().equals(tenantId))
                .orElseThrow(() ->
                        new BusinessRuleException(CODE_ADDRESS_NOT_FOUND, Map.of("addressId", addressId.toString())));
    }

    // -------------------------------------------------------------------
    //  create
    // -------------------------------------------------------------------

    @Transactional("companyTransactionManager")
    public Address create(UUID tenantId, CreateAddressRequest req) {
        Address a = new Address();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setStreet(req.street());
        a.setNumber(req.number());
        a.setFloor(req.floor());
        a.setApartment(req.apartment());
        a.setCity(req.city());
        a.setProvince(req.province());
        a.setPostalCode(req.postalCode());
        a.setReference(req.reference());
        a.setCountry(req.country() == null || req.country().isBlank() ? DEFAULT_COUNTRY : req.country());
        // createdAt / updatedAt are stamped by the entity's @PrePersist.
        return addressRepository.save(a);
    }

    // -------------------------------------------------------------------
    //  update
    // -------------------------------------------------------------------

    @Transactional("companyTransactionManager")
    public Address update(UUID tenantId, UUID addressId, UpdateAddressRequest req) {
        Address a = addressRepository
                .findById(addressId)
                .filter(x -> x.getTenantId() != null && x.getTenantId().equals(tenantId))
                .orElseThrow(() ->
                        new BusinessRuleException(CODE_ADDRESS_NOT_FOUND, Map.of("addressId", addressId.toString())));

        if (req.street() != null) {
            a.setStreet(req.street());
        }
        if (req.number() != null) {
            a.setNumber(req.number());
        }
        if (req.floor() != null) {
            a.setFloor(req.floor());
        }
        if (req.apartment() != null) {
            a.setApartment(req.apartment());
        }
        if (req.city() != null) {
            a.setCity(req.city());
        }
        if (req.province() != null) {
            a.setProvince(req.province());
        }
        if (req.postalCode() != null) {
            a.setPostalCode(req.postalCode());
        }
        if (req.reference() != null) {
            a.setReference(req.reference());
        }
        if (req.country() != null && !req.country().isBlank()) {
            a.setCountry(req.country());
        }
        return addressRepository.save(a);
    }

    // -------------------------------------------------------------------
    //  disable
    // -------------------------------------------------------------------

    @Transactional("companyTransactionManager")
    public void disable(UUID tenantId, UUID addressId) {
        Address a = addressRepository
                .findById(addressId)
                .filter(x -> x.getTenantId() != null && x.getTenantId().equals(tenantId))
                .orElseThrow(() ->
                        new BusinessRuleException(CODE_ADDRESS_NOT_FOUND, Map.of("addressId", addressId.toString())));
        a.setDeletedAt(Instant.now());
        addressRepository.save(a);
    }

    // -------------------------------------------------------------------
    //  Records
    // -------------------------------------------------------------------

    /** Request body for {@link #create}. */
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

    /** Request body for {@link #update}. Any null field is left unchanged. */
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
