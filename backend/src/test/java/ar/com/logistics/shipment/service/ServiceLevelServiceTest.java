package ar.com.logistics.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.logistics.shipment.domain.ServiceLevel;
import ar.com.logistics.shipment.repository.company.ServiceLevelRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ServiceLevelService}. Service-level catalog
 * listing only — CRUD on service_levels is Etapa 4 scope. The
 * {@code STANDARD} service level is lazy-seeded by
 * {@code ShipmentService.create} (PR-3a Chunk B), so the
 * {@code list} endpoint may return an empty list for a
 * freshly-onboarded tenant before their first shipment.
 */
@ExtendWith(MockitoExtension.class)
class ServiceLevelServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    @Mock
    private ServiceLevelRepository serviceLevelRepository;

    @InjectMocks
    private ServiceLevelService service;

    @Test
    @DisplayName("list returns the active service levels for the tenant in repository order")
    void list_returnsTenantServiceLevels() {
        ServiceLevel standard = serviceLevel("STANDARD", "Entrega estándar");
        ServiceLevel express = serviceLevel("EXPRESS", "Entrega exprés");
        when(serviceLevelRepository.findByTenantIdAndIsActiveTrueOrderByCode(TENANT_ID))
                .thenReturn(List.of(standard, express));

        List<ServiceLevel> result = service.list(TENANT_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ServiceLevel::getCode).containsExactly("STANDARD", "EXPRESS");
        assertThat(result).extracting(ServiceLevel::getTenantId).containsOnly(TENANT_ID);
    }

    @Test
    @DisplayName("list returns an empty list when the tenant has no service levels")
    void list_returnsEmptyWhenNoServiceLevels() {
        when(serviceLevelRepository.findByTenantIdAndIsActiveTrueOrderByCode(TENANT_ID))
                .thenReturn(List.of());

        List<ServiceLevel> result = service.list(TENANT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("list delegates filtering of soft-deleted rows to the repository derived query")
    void list_excludesSoftDeleted() {
        // The repository's derived query name itself encodes the
        // soft-delete exclusion (IsActiveTrue) — there is no in-memory
        // filter in the service. This test asserts the service uses
        // that exact method (and that soft-deleted rows never reach
        // the call site because the repository contract excludes
        // them).
        ServiceLevel standard = serviceLevel("STANDARD", "Entrega estándar");
        when(serviceLevelRepository.findByTenantIdAndIsActiveTrueOrderByCode(TENANT_ID))
                .thenReturn(List.of(standard));

        List<ServiceLevel> result = service.list(TENANT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDeletedAt()).isNull();
        assertThat(result.get(0).isActive()).isTrue();
    }

    @Test
    @DisplayName("list passes the caller's tenantId through to the repository")
    void list_passesTenantId() {
        when(serviceLevelRepository.findByTenantIdAndIsActiveTrueOrderByCode(OTHER_TENANT_ID))
                .thenReturn(List.of());

        List<ServiceLevel> result = service.list(OTHER_TENANT_ID);

        assertThat(result).isEmpty();
        verify(serviceLevelRepository).findByTenantIdAndIsActiveTrueOrderByCode(eq(OTHER_TENANT_ID));
    }

    // -------------------------------------------------------------------
    //  helpers
    // -------------------------------------------------------------------

    private ServiceLevel serviceLevel(String code, String name) {
        ServiceLevel sl = new ServiceLevel();
        sl.setId(UUID.randomUUID());
        sl.setTenantId(TENANT_ID);
        sl.setCode(code);
        sl.setName(name);
        sl.setActive(true);
        sl.setCreatedAt(Instant.now());
        sl.setUpdatedAt(Instant.now());
        sl.setDeletedAt(null);
        return sl;
    }
}
