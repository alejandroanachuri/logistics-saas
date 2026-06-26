package ar.com.logistics.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ar.com.logistics.common.exception.BusinessRuleException;
import ar.com.logistics.shipment.domain.Address;
import ar.com.logistics.shipment.repository.company.AddressRepository;
import ar.com.logistics.shipment.service.AddressService.CreateAddressRequest;
import ar.com.logistics.shipment.service.AddressService.UpdateAddressRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AddressService}. Addresses are inert reference
 * data — no audit, no business rule beyond tenant scoping and
 * not-found handling.
 */
@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ADDRESS_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private AddressService service;

    // -------------------------------------------------------------------
    //  create / get
    // -------------------------------------------------------------------

    @Test
    @DisplayName("create with a valid address succeeds and returns the saved row")
    void create_validAddress_succeeds() {
        CreateAddressRequest req = new CreateAddressRequest(
                "Av Corrientes", "1234", "5", "B", "CABA", "Buenos Aires", "C1043", "Puerta negra", "AR");

        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> {
            Address a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setCreatedAt(Instant.now());
            a.setUpdatedAt(Instant.now());
            return a;
        });

        Address saved = service.create(TENANT_ID, req);

        assertThat(saved.getStreet()).isEqualTo("Av Corrientes");
        assertThat(saved.getNumber()).isEqualTo("1234");
        assertThat(saved.getCountry()).isEqualTo("AR");
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("get with non-existent id throws BusinessRuleException(ADDRESS_NOT_FOUND)")
    void get_notFound_throws() {
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(TENANT_ID, ADDRESS_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("ADDRESS_NOT_FOUND");
    }

    // -------------------------------------------------------------------
    //  update
    // -------------------------------------------------------------------

    @Test
    @DisplayName("update with non-existent id throws ADDRESS_NOT_FOUND")
    void update_notFound_throws() {
        UpdateAddressRequest req =
                new UpdateAddressRequest("Av Corrientes", "1234", null, null, null, null, null, null, "AR");
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(TENANT_ID, ADDRESS_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("ADDRESS_NOT_FOUND");
    }

    @Test
    @DisplayName("update with existing id applies the supplied field changes")
    void update_existing_appliesChanges() {
        Address existing = new Address();
        existing.setId(ADDRESS_ID);
        existing.setTenantId(TENANT_ID);
        existing.setStreet("Old Street");
        existing.setNumber("111");
        existing.setCity("Old City");
        existing.setProvince("Old Province");
        existing.setPostalCode("0000");
        existing.setCountry("AR");

        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(existing));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAddressRequest req = new UpdateAddressRequest(
                "Av Corrientes", "1234", "5", "B", "CABA", "Buenos Aires", "C1043", null, "AR");

        Address updated = service.update(TENANT_ID, ADDRESS_ID, req);

        assertThat(updated.getStreet()).isEqualTo("Av Corrientes");
        assertThat(updated.getNumber()).isEqualTo("1234");
        assertThat(updated.getFloor()).isEqualTo("5");
        assertThat(updated.getApartment()).isEqualTo("B");
        assertThat(updated.getCity()).isEqualTo("CABA");
        assertThat(updated.getProvince()).isEqualTo("Buenos Aires");
        assertThat(updated.getPostalCode()).isEqualTo("C1043");
        assertThat(updated.getCountry()).isEqualTo("AR");
    }

    // -------------------------------------------------------------------
    //  disable
    // -------------------------------------------------------------------

    @Test
    @DisplayName("disable with valid id sets deletedAt = now")
    void disable_valid_setsDeletedAt() {
        Address existing = new Address();
        existing.setId(ADDRESS_ID);
        existing.setTenantId(TENANT_ID);
        existing.setStreet("Av Corrientes");
        existing.setNumber("1234");
        existing.setCity("CABA");
        existing.setProvince("Buenos Aires");
        existing.setPostalCode("C1043");
        existing.setCountry("AR");
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(existing));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        service.disable(TENANT_ID, ADDRESS_ID);

        assertThat(existing.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("disable with non-existent id throws ADDRESS_NOT_FOUND")
    void disable_notFound_throws() {
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable(TENANT_ID, ADDRESS_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("ADDRESS_NOT_FOUND");
    }
}
