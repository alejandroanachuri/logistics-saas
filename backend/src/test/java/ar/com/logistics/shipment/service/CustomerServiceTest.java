package ar.com.logistics.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.logistics.common.audit.AuditEvent;
import ar.com.logistics.common.audit.AuditLogger;
import ar.com.logistics.common.exception.BusinessRuleException;
import ar.com.logistics.shipment.domain.Customer;
import ar.com.logistics.shipment.repository.company.CustomerRepository;
import ar.com.logistics.shipment.service.CustomerService.CreateCustomerRequest;
import ar.com.logistics.shipment.service.CustomerService.CustomerListFilters;
import ar.com.logistics.shipment.service.CustomerService.UpdateCustomerRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CustomerService}. Strict TDD — every test pins a
 * piece of the contract from spec §B before the implementation exists.
 *
 * <p>Contract highlights:
 * <ul>
 *   <li>{@code create} for a FISICA customer validates DNI (7-8 digits,
 *       per-tenant uniqueness) and stamps the row to ACTIVE.</li>
 *   <li>{@code create} for a JURIDICA customer validates CUIT (mod-11
 *       verifier + per-tenant uniqueness) and requires razonSocial.</li>
 *   <li>{@code dataConsent=true} auto-defaults {@code consentDate} when
 *       the caller omits it; {@code dataConsent=false} rejects a
 *       non-null {@code consentDate} as inconsistent.</li>
 *   <li>Every mutation emits an audit event with the right metadata.</li>
 *   <li>Missing rows throw {@link BusinessRuleException} with code
 *       {@code CUSTOMER_NOT_FOUND}.</li>
 *   <li>{@code disable} soft-deletes via {@code status=DISABLED} +
 *       {@code deletedAt=now()}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private CustomerService service;

    // -------------------------------------------------------------------
    //  create — FISICA happy / sad paths
    // -------------------------------------------------------------------

    @Test
    @DisplayName("create with valid FISICA + DNI succeeds and emits CUSTOMER_CREATED audit")
    void create_fisica_valid_succeeds() {
        CreateCustomerRequest req = new CreateCustomerRequest(
                "FISICA",
                "Juan",
                "Pérez",
                null, // razonSocial
                "12345678",
                null, // cuitCuil
                "CONSUMIDOR_FINAL",
                "+5491155554444",
                "juan@example.com",
                false,
                null,
                "v1",
                null // notes
                );

        when(customerRepository.findByTenantIdAndDni(TENANT_ID, "12345678")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        Customer saved = service.create(TENANT_ID, ADMIN_ID, req);

        assertThat(saved.getPersonType()).isEqualTo("FISICA");
        assertThat(saved.getDni()).isEqualTo("12345678");
        // ACTIVE rows have deletedAt == null (the entity has no status column;
        // soft-delete is signalled by deletedAt).
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getCreatedBy()).isEqualTo(ADMIN_ID);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger, times(1)).logAsync(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo("CUSTOMER_CREATED");
        assertThat(ev.tenantId()).isEqualTo(TENANT_ID);
        assertThat(ev.userId()).isEqualTo(saved.getId());
        assertThat(ev.metadata()).containsEntry("createdBy", ADMIN_ID.toString());
    }

    @Test
    @DisplayName("create with FISICA + invalid DNI (5 digits) throws BusinessRuleException(DNI_INVALID)")
    void create_fisica_invalidDni_throws() {
        CreateCustomerRequest req = new CreateCustomerRequest(
                "FISICA",
                "Juan",
                "Pérez",
                null,
                "12345",
                null,
                "CONSUMIDOR_FINAL",
                "+5491155554444",
                "j@e.com",
                false,
                null,
                "v1",
                null);

        assertThatThrownBy(() -> service.create(TENANT_ID, ADMIN_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("DNI_INVALID");

        verify(customerRepository, never()).save(any());
        verify(auditLogger, never()).logAsync(any());
    }

    @Test
    @DisplayName("create with FISICA + duplicate DNI within tenant throws DNI_ALREADY_EXISTS")
    void create_fisica_duplicateDni_throws() {
        CreateCustomerRequest req = new CreateCustomerRequest(
                "FISICA",
                "Juan",
                "Pérez",
                null,
                "12345678",
                null,
                "CONSUMIDOR_FINAL",
                "+5491155554444",
                "j@e.com",
                false,
                null,
                "v1",
                null);

        when(customerRepository.findByTenantIdAndDni(TENANT_ID, "12345678")).thenReturn(Optional.of(new Customer()));

        assertThatThrownBy(() -> service.create(TENANT_ID, ADMIN_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("DNI_ALREADY_EXISTS");

        verify(customerRepository, never()).save(any());
    }

    // -------------------------------------------------------------------
    //  create — JURIDICA happy / sad paths
    // -------------------------------------------------------------------

    @Test
    @DisplayName("create with valid JURIDICA + CUIT succeeds")
    void create_juridica_valid_succeeds() {
        CreateCustomerRequest req = new CreateCustomerRequest(
                "JURIDICA",
                null,
                null,
                "ACME SRL",
                null,
                "12345678903", // valid mod-11
                "RESPONSABLE_INSCRIPTO",
                "+541144556677",
                "ops@acme.com",
                false,
                null,
                "v1",
                "Cliente VIP");

        // Existing pattern mirrors CompanyUsersService.list: uniqueness via
        // findAll() then tenant+CUIT filter in code (CustomerRepository does
        // not yet expose a findByTenantIdAndCuitCuil derived query).
        when(customerRepository.findAll()).thenReturn(List.of());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        Customer saved = service.create(TENANT_ID, ADMIN_ID, req);

        assertThat(saved.getPersonType()).isEqualTo("JURIDICA");
        assertThat(saved.getCuitCuil()).isEqualTo("12345678903");
        assertThat(saved.getRazonSocial()).isEqualTo("ACME SRL");
        assertThat(saved.getDeletedAt()).isNull();

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger, times(1)).logAsync(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("CUSTOMER_CREATED");
    }

    @Test
    @DisplayName("create with JURIDICA + invalid CUIT (wrong mod-11 digit) throws CUIT_INVALID")
    void create_juridica_invalidCuit_throws() {
        CreateCustomerRequest req = new CreateCustomerRequest(
                "JURIDICA",
                null,
                null,
                "ACME SRL",
                null,
                "12345678900", // wrong verifier digit
                "RESPONSABLE_INSCRIPTO",
                "+541144556677",
                "ops@acme.com",
                false,
                null,
                "v1",
                null);

        assertThatThrownBy(() -> service.create(TENANT_ID, ADMIN_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("CUIT_INVALID");

        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("create with JURIDICA + duplicate CUIT within tenant throws CUIT_ALREADY_EXISTS")
    void create_juridica_duplicateCuit_throws() {
        CreateCustomerRequest req = new CreateCustomerRequest(
                "JURIDICA",
                null,
                null,
                "ACME SRL",
                null,
                "12345678903",
                "RESPONSABLE_INSCRIPTO",
                "+541144556677",
                "ops@acme.com",
                false,
                null,
                "v1",
                null);

        when(customerRepository.findAll()).thenReturn(List.of(existingCuitCustomer()));

        assertThatThrownBy(() -> service.create(TENANT_ID, ADMIN_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("CUIT_ALREADY_EXISTS");
    }

    // -------------------------------------------------------------------
    //  create — data consent rules
    // -------------------------------------------------------------------

    @Test
    @DisplayName("create with dataConsent=true + consentDate=null succeeds (server defaults to now)")
    void create_dataConsentTrue_nullDate_autoDefaults() {
        CreateCustomerRequest req = new CreateCustomerRequest(
                "FISICA",
                "Juan",
                "Pérez",
                null,
                "12345678",
                null,
                "CONSUMIDOR_FINAL",
                "+5491155554444",
                "j@e.com",
                true,
                null,
                "v1",
                null);

        when(customerRepository.findByTenantIdAndDni(any(), anyString())).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        Customer saved = service.create(TENANT_ID, ADMIN_ID, req);

        assertThat(saved.isDataConsent()).isTrue();
        assertThat(saved.getConsentDate()).isNotNull();
    }

    @Test
    @DisplayName("create with dataConsent=true + consentDate set explicitly uses the supplied value")
    void create_dataConsentTrue_explicitDate_preserved() {
        Instant explicitDate = Instant.parse("2026-01-15T10:00:00Z");
        CreateCustomerRequest req = new CreateCustomerRequest(
                "FISICA",
                "Juan",
                "Pérez",
                null,
                "12345678",
                null,
                "CONSUMIDOR_FINAL",
                "+5491155554444",
                "j@e.com",
                true,
                explicitDate,
                "v1",
                null);

        when(customerRepository.findByTenantIdAndDni(any(), anyString())).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer saved = service.create(TENANT_ID, ADMIN_ID, req);

        assertThat(saved.getConsentDate()).isEqualTo(explicitDate);
    }

    @Test
    @DisplayName("create with dataConsent=false + consentDate set throws NO_DATA_CONSENT (mismatch)")
    void create_dataConsentFalse_withDate_throws() {
        Instant orphanDate = Instant.parse("2026-01-15T10:00:00Z");
        CreateCustomerRequest req = new CreateCustomerRequest(
                "FISICA",
                "Juan",
                "Pérez",
                null,
                "12345678",
                null,
                "CONSUMIDOR_FINAL",
                "+5491155554444",
                "j@e.com",
                false,
                orphanDate,
                "v1",
                null);

        assertThatThrownBy(() -> service.create(TENANT_ID, ADMIN_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("NO_DATA_CONSENT");
    }

    // -------------------------------------------------------------------
    //  get / update / disable — not-found paths
    // -------------------------------------------------------------------

    @Test
    @DisplayName("get with non-existent id throws BusinessRuleException(CUSTOMER_NOT_FOUND)")
    void get_notFound_throws() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(TENANT_ID, CUSTOMER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("CUSTOMER_NOT_FOUND");
    }

    @Test
    @DisplayName("update with non-existent id throws CUSTOMER_NOT_FOUND")
    void update_notFound_throws() {
        UpdateCustomerRequest req = new UpdateCustomerRequest("Juan", "Pérez", null, null, null, null, null);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(TENANT_ID, ADMIN_ID, CUSTOMER_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("CUSTOMER_NOT_FOUND");

        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("disable with non-existent id throws CUSTOMER_NOT_FOUND")
    void disable_notFound_throws() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable(TENANT_ID, ADMIN_ID, CUSTOMER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("CUSTOMER_NOT_FOUND");
    }

    @Test
    @DisplayName("disable with existing id sets deletedAt=now + updatedBy, emits CUSTOMER_DISABLED audit")
    void disable_existing_setsDeletedAt() {
        Customer existing = new Customer();
        existing.setId(CUSTOMER_ID);
        existing.setTenantId(TENANT_ID);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.disable(TENANT_ID, ADMIN_ID, CUSTOMER_ID);

        // Soft-delete is signalled by deletedAt (the entity has no status column).
        assertThat(existing.getDeletedAt()).isNotNull();
        assertThat(existing.getUpdatedBy()).isEqualTo(ADMIN_ID);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger, times(1)).logAsync(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("CUSTOMER_DISABLED");
    }

    // -------------------------------------------------------------------
    //  helpers
    // -------------------------------------------------------------------

    private static Customer existingCuitCustomer() {
        Customer c = new Customer();
        c.setTenantId(TENANT_ID);
        c.setCuitCuil("12345678903");
        return c;
    }

    // -------------------------------------------------------------------
    //  list — sanity
    // -------------------------------------------------------------------

    @Test
    @DisplayName("list returns the tenant-scoped rows from the repository")
    void list_returnsPage() {
        Customer c = new Customer();
        c.setId(CUSTOMER_ID);
        c.setTenantId(TENANT_ID);
        c.setFirstName("Juan");
        when(customerRepository.findAll()).thenReturn(List.of(c));

        var page = service.list(
                TENANT_ID, new CustomerListFilters(null, null), org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(CUSTOMER_ID);
    }
}
