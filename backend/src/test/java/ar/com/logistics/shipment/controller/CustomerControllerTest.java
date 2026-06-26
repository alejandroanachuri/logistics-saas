package ar.com.logistics.shipment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.com.logistics.shipment.domain.Customer;
import ar.com.logistics.shipment.service.CustomerService;
import ar.com.logistics.shipment.service.CustomerService.CustomerListFilters;
import ar.com.logistics.tenant.TenantContext;
import ar.com.logistics.tenant.TenantContextEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller tests for {@code /api/v1/customers/**} (PR-4 Chunk B
 * Part 1). Five endpoints, role-gated by {@code @PreAuthorize}
 * (ADMIN+OPERATOR for create/update; ADMIN only for disable;
 * ADMIN+OPERATOR+VIEWER for read).
 *
 * <p>Uses {@code MockMvcBuilders.standaloneSetup} — the project
 * convention from {@code CompanyUsersControllerTest}. Security
 * enforcement is covered by the IT suite; here we focus on
 * controller wiring + service delegation + JSON shape.
 */
@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private CustomerController controller;

    private UUID tenantId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        tenantId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        TenantContext.set(tenantId, TenantContextEntry.Scope.COMPANY);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        adminId,
                        "n/a",
                        List.of(
                                new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"),
                                new SimpleGrantedAuthority("SCOPE_COMPANY"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/customers: returns 200 with PageResponse<CustomerSummaryDto>")
    void list_returnsPageResponse() throws Exception {
        UUID id = UUID.randomUUID();
        Customer c = new Customer();
        c.setId(id);
        c.setTenantId(tenantId);
        c.setPersonType("FISICA");
        c.setFirstName("Alice");
        c.setLastName("Smith");
        c.setDni("12345678");
        c.setTaxCondition("CONSUMIDOR_FINAL");
        c.setPhone("+5491100000000");
        c.setEmail("alice@example.com");
        c.setDataConsent(true);
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());

        Page<Customer> page = new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1);
        when(customerService.list(eq(tenantId), any(CustomerListFilters.class), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/customers/{id}: returns 200 with CustomerDetailDto")
    void get_returnsDetail() throws Exception {
        UUID id = UUID.randomUUID();
        Customer c = new Customer();
        c.setId(id);
        c.setTenantId(tenantId);
        c.setPersonType("JURIDICA");
        c.setRazonSocial("ACME S.A.");
        c.setCuitCuil("30712345678");
        c.setTaxCondition("RESPONSABLE_INSCRIPTO");
        c.setPhone("+5491100000000");
        c.setEmail("billing@acme.example");
        c.setDataConsent(true);
        c.setConsentDate(Instant.now());
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());

        when(customerService.get(tenantId, id)).thenReturn(c);

        mockMvc.perform(get("/api/v1/customers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.personType").value("JURIDICA"))
                .andExpect(jsonPath("$.razonSocial").value("ACME S.A."))
                .andExpect(jsonPath("$.taxCondition").value("RESPONSABLE_INSCRIPTO"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/customers: returns 201 with CustomerDetailDto")
    void create_returns201WithDetail() throws Exception {
        UUID id = UUID.randomUUID();
        Customer created = new Customer();
        created.setId(id);
        created.setTenantId(tenantId);
        created.setPersonType("FISICA");
        created.setFirstName("Bob");
        created.setLastName("Doe");
        created.setDni("40123456");
        created.setTaxCondition("CONSUMIDOR_FINAL");
        created.setPhone("+5491100000000");
        created.setEmail("bob@example.com");
        created.setDataConsent(true);
        created.setConsentDate(Instant.now());
        created.setCreatedAt(Instant.now());
        created.setUpdatedAt(Instant.now());

        when(customerService.create(eq(tenantId), eq(adminId), any())).thenReturn(created);

        CustomerController.CreateCustomerRequest req = new CustomerController.CreateCustomerRequest(
                "FISICA",
                "Bob",
                "Doe",
                null,
                "40123456",
                null,
                "CONSUMIDOR_FINAL",
                "+5491100000000",
                "bob@example.com",
                true,
                null,
                "v1",
                null);

        mockMvc.perform(post("/api/v1/customers")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.personType").value("FISICA"))
                .andExpect(jsonPath("$.firstName").value("Bob"));
    }

    @Test
    @DisplayName("PATCH /api/v1/customers/{id}: returns 200 with the updated detail")
    void patch_returnsUpdatedDetail() throws Exception {
        UUID id = UUID.randomUUID();
        Customer updated = new Customer();
        updated.setId(id);
        updated.setTenantId(tenantId);
        updated.setPersonType("FISICA");
        updated.setFirstName("Bob");
        updated.setLastName("Doe");
        updated.setDni("40123456");
        updated.setTaxCondition("CONSUMIDOR_FINAL");
        updated.setPhone("+5491100000000");
        updated.setEmail("bob.new@example.com");
        updated.setDataConsent(true);
        updated.setCreatedAt(Instant.now());
        updated.setUpdatedAt(Instant.now());

        when(customerService.update(eq(tenantId), eq(adminId), eq(id), any())).thenReturn(updated);

        CustomerController.UpdateCustomerRequest req = new CustomerController.UpdateCustomerRequest(
                "Bob", null, null, null, null, "bob.new@example.com", null);

        mockMvc.perform(patch("/api/v1/customers/" + id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bob.new@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/customers/{id}/disable: returns 204 and delegates to the service")
    void disable_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(customerService).disable(tenantId, adminId, id);

        mockMvc.perform(post("/api/v1/customers/" + id + "/disable")).andExpect(status().isNoContent());
    }
}
