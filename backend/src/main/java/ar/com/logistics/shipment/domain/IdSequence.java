package ar.com.logistics.shipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code public.id_sequences} (V15). Per-tenant, per-key,
 * per-year monotonic counter. LGST codes are NOT sequenced — they
 * use {@code LgstGenerator}'s random 32-bit scheme — but internal
 * branch codes, internal order numbers, etc. do increment through
 * this table.
 *
 * <p>Composite PK {@code (tenant_id, sequence_key, year)} enforced
 * by the {@code UNIQUE} constraint in V15. Mapped via {@link IdClass},
 * same pattern as {@link ShipmentCustody}.
 */
@Entity
@Table(name = "id_sequences")
@IdClass(IdSequence.IdSequenceId.class)
@Getter
@Setter
@NoArgsConstructor
public class IdSequence {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Id
    @Column(name = "sequence_key", length = 50, nullable = false, updatable = false)
    private String sequenceKey;

    @Id
    @Column(name = "year", nullable = false, updatable = false)
    private int year;

    @Column(name = "current_value", nullable = false)
    private long currentValue;

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class IdSequenceId implements Serializable {

        private UUID tenantId;

        private String sequenceKey;

        private int year;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IdSequenceId that)) {
                return false;
            }
            return year == that.year
                    && Objects.equals(tenantId, that.tenantId)
                    && Objects.equals(sequenceKey, that.sequenceKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, sequenceKey, year);
        }
    }
}
