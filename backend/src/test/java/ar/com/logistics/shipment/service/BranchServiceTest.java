package ar.com.logistics.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.logistics.shipment.domain.Branch;
import ar.com.logistics.shipment.repository.company.BranchRepository;
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
 * Unit tests for {@link BranchService}. Branch catalog listing only
 * — CRUD on branches is Etapa 4 scope. The {@code PRINCIPAL}
 * branch is lazy-seeded by {@code ShipmentService.create} (PR-3a
 * Chunk B), so the {@code list} endpoint may return an empty list
 * for a freshly-onboarded tenant before their first shipment.
 */
@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Mock
    private BranchRepository branchRepository;

    @InjectMocks
    private BranchService service;

    @Test
    @DisplayName("list returns the active branches for the tenant in repository order")
    void list_returnsTenantBranches() {
        Branch principal = branch("PRINCIPAL", "Casa Central", null);
        Branch secundario = branch("SECUNDARIO", "Sucursal Norte", null);
        when(branchRepository.findByTenantIdAndIsActiveTrueOrderByCode(TENANT_ID))
                .thenReturn(List.of(principal, secundario));

        List<Branch> result = service.list(TENANT_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Branch::getCode).containsExactly("PRINCIPAL", "SECUNDARIO");
        assertThat(result).extracting(Branch::getTenantId).containsOnly(TENANT_ID);
    }

    @Test
    @DisplayName("list returns an empty list when the tenant has no branches")
    void list_returnsEmptyWhenNoBranches() {
        when(branchRepository.findByTenantIdAndIsActiveTrueOrderByCode(TENANT_ID))
                .thenReturn(List.of());

        List<Branch> result = service.list(TENANT_ID);

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
        Branch active = branch("PRINCIPAL", "Casa Central", null);
        when(branchRepository.findByTenantIdAndIsActiveTrueOrderByCode(TENANT_ID))
                .thenReturn(List.of(active));

        List<Branch> result = service.list(TENANT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDeletedAt()).isNull();
        assertThat(result.get(0).isActive()).isTrue();
    }

    @Test
    @DisplayName("list passes the caller's tenantId through to the repository")
    void list_passesTenantId() {
        when(branchRepository.findByTenantIdAndIsActiveTrueOrderByCode(OTHER_TENANT_ID))
                .thenReturn(List.of());

        List<Branch> result = service.list(OTHER_TENANT_ID);

        assertThat(result).isEmpty();
        verify(branchRepository).findByTenantIdAndIsActiveTrueOrderByCode(eq(OTHER_TENANT_ID));
    }

    // -------------------------------------------------------------------
    //  helpers
    // -------------------------------------------------------------------

    private Branch branch(String code, String name, Instant deletedAt) {
        Branch b = new Branch();
        b.setId(UUID.randomUUID());
        b.setTenantId(TENANT_ID);
        b.setCode(code);
        b.setName(name);
        b.setActive(true);
        b.setCreatedAt(Instant.now());
        b.setUpdatedAt(Instant.now());
        b.setDeletedAt(deletedAt);
        return b;
    }
}
