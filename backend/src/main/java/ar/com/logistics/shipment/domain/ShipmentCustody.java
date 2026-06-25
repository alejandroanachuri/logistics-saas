package ar.com.logistics.shipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code public.shipment_custody} (V15). Projection of which
 * tenant currently holds a package, updated on every handoff event.
 *
 * <p>The PK is composed of {@code (package_id, acquired_event_id)} —
 * a given custody arc is uniquely identified by when it began. The
 * secondary key {@code UNIQUE (package_id, acquired_event_id)} also
 * backs the {@code idx_custody_package_active} partial index.
 *
 * <p>Notice this table has NO {@code tenant_id} column — RLS resolves
 * the tenant via a subquery against {@code packages} (V16). The
 * composite PK is mapped via {@link IdClass}, mirroring the
 * {@link ar.com.logistics.auth.domain.CompanyUserRole} pattern.
 */
@Entity
@Table(name = "shipment_custody")
@IdClass(ShipmentCustody.ShipmentCustodyId.class)
@Getter
@Setter
@NoArgsConstructor
public class ShipmentCustody {

    @Id
    @Column(name = "package_id", nullable = false, updatable = false)
    private UUID packageId;

    @Id
    @Column(name = "acquired_event_id", nullable = false, updatable = false)
    private UUID acquiredEventId;

    @Column(name = "custodian_tenant_id", nullable = false)
    private UUID custodianTenantId;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    @Column(name = "released_event_id")
    private UUID releasedEventId;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    /**
     * Composite PK class required by {@link IdClass}. JPA uses it as
     * the key in the persistence context. Must be
     * {@link Serializable} and implement {@link Object#equals(Object)}
     * / {@link Object#hashCode()} per the JPA spec. Lombok
     * {@code @Data} is not used here because it triggers an infinite
     * recursion in the {@code toString} cycle that some Hibernate
     * versions flag — explicit equals / hashCode are simpler and
     * cheaper.
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class ShipmentCustodyId implements Serializable {

        private UUID packageId;

        private UUID acquiredEventId;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ShipmentCustodyId that)) {
                return false;
            }
            return Objects.equals(packageId, that.packageId) && Objects.equals(acquiredEventId, that.acquiredEventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageId, acquiredEventId);
        }
    }
}
