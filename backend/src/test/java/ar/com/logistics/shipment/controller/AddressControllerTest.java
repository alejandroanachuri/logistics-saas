package ar.com.logistics.shipment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.com.logistics.shipment.domain.Address;
import ar.com.logistics.shipment.service.AddressService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller tests for {@code /api/v1/addresses/**} (PR-4 Chunk B
 * Part 1). Three endpoints (get / create / update), role-gated by
 * {@code @PreAuthorize} (any authenticated company user for read;
 * ADMIN+OPERATOR for write).
 */
@ExtendWith(MockitoExtension.class)
class AddressControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AddressService addressService;

    @InjectMocks
    private AddressController controller;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        tenantId = UUID.randomUUID();
        TenantContext.set(tenantId, TenantContextEntry.Scope.COMPANY);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID(),
                        "n/a",
                        List.of(
                                new SimpleGrantedAuthority("ROLE_COMPANY_OPERATOR"),
                                new SimpleGrantedAuthority("SCOPE_COMPANY"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/addresses/{id}: returns 200 with AddressDto")
    void get_returnsDetail() throws Exception {
        UUID id = UUID.randomUUID();
        Address a = new Address();
        a.setId(id);
        a.setTenantId(tenantId);
        a.setStreet("Av. Corrientes");
        a.setNumber("1234");
        a.setFloor("5");
        a.setApartment("B");
        a.setCity("CABA");
        a.setProvince("Buenos Aires");
        a.setPostalCode("C1043");
        a.setReference("Timbre 2");
        a.setCountry("AR");
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());

        when(addressService.get(tenantId, id)).thenReturn(a);

        mockMvc.perform(get("/api/v1/addresses/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.street").value("Av. Corrientes"))
                .andExpect(jsonPath("$.city").value("CABA"))
                .andExpect(jsonPath("$.country").value("AR"));
    }

    @Test
    @DisplayName("POST /api/v1/addresses: returns 201 with AddressDto")
    void create_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        Address created = new Address();
        created.setId(id);
        created.setTenantId(tenantId);
        created.setStreet("Av. Rivadavia");
        created.setNumber("5000");
        created.setCity("CABA");
        created.setProvince("Buenos Aires");
        created.setPostalCode("C1424");
        created.setCountry("AR");
        created.setCreatedAt(Instant.now());
        created.setUpdatedAt(Instant.now());

        when(addressService.create(eq(tenantId), any())).thenReturn(created);

        AddressController.CreateAddressRequest req = new AddressController.CreateAddressRequest(
                "Av. Rivadavia", "5000", null, null, "CABA", "Buenos Aires", "C1424", null, null);

        mockMvc.perform(post("/api/v1/addresses")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.street").value("Av. Rivadavia"))
                .andExpect(jsonPath("$.country").value("AR"));
    }

    @Test
    @DisplayName("PATCH /api/v1/addresses/{id}: returns 200 with the updated AddressDto")
    void update_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        Address updated = new Address();
        updated.setId(id);
        updated.setTenantId(tenantId);
        updated.setStreet("Av. Rivadavia");
        updated.setNumber("5500");
        updated.setCity("CABA");
        updated.setProvince("Buenos Aires");
        updated.setPostalCode("C1424");
        updated.setCountry("AR");
        updated.setCreatedAt(Instant.now());
        updated.setUpdatedAt(Instant.now());

        when(addressService.update(eq(tenantId), eq(id), any())).thenReturn(updated);

        AddressController.UpdateAddressRequest req =
                new AddressController.UpdateAddressRequest(null, "5500", null, null, null, null, null, null, null);

        mockMvc.perform(patch("/api/v1/addresses/" + id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("5500"));
    }
}
