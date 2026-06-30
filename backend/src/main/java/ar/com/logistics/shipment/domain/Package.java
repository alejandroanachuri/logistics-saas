package ar.com.logistics.shipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code public.packages} (V15). One shipment can carry N
 * packages. The FSM (see {@code PackageFsm}) operates on this entity,
 * not on {@code Shipment}.
 *
 * <p>Field name {@code Package} shadows {@code java.lang.Package}
 * inside this file but does not break compilation — Java allows
 * same-named classes in different packages, and {@code Package} here
 * is the entity class. Hibernate maps the {@code @Table(name="packages")}
 * without needing a {@code @Column(name="package")} escape; the
 * table name does not collide with any keyword.
 *
 * <p>CHECK constraints enforced by the DB:
 * <ul>
 *   <li>{@code weight_kg > 0} — declared on the column.</li>
 *   <li>{@code category IN (...)} — see the V15 DDL.</li>
 *   <li>{@code reception_condition IN ('BUENO','DAÑADO_EXTERNO','ABIERTO')}</li>
 * </ul>
 *
 * <p>Note: this entity does NOT have a {@code deleted_at} column —
 * packages are not soft-deleted; lifecycle ends via FSM transition to
 * {@code ENTREGADO}, {@code DEVUELTO}, etc.
 */
@Entity
@Table(name = "packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Package {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "qr_code", length = 255, nullable = false, unique = true)
    private String qrCode;

    @Column(name = "previous_status", length = 60)
    private String previousStatus;

    @Column(name = "status", length = 60, nullable = false)
    private String status;

    @Column(name = "weight_kg", precision = 8, scale = 2, nullable = false)
    private BigDecimal weightKg;

    @Column(name = "volume_cm3", precision = 10, scale = 2)
    private BigDecimal volumeCm3;

    @Column(name = "dimensions_cm", length = 30)
    private String dimensionsCm;

    @Column(name = "content_description", columnDefinition = "TEXT", nullable = false)
    private String contentDescription;

    @Column(name = "declared_value", precision = 12, scale = 2)
    private BigDecimal declaredValue;

    @Column(name = "declared_currency", length = 3, nullable = false)
    private String declaredCurrency;

    @Column(name = "has_insurance", nullable = false)
    private boolean hasInsurance;

    @Column(name = "insurance_premium", precision = 10, scale = 2)
    private BigDecimal insurancePremium;

    @Column(name = "is_fragile", nullable = false)
    private boolean isFragile;

    @Column(name = "is_urgent", nullable = false)
    private boolean isUrgent;

    @Column(name = "requires_signature", nullable = false)
    private boolean requiresSignature;

    @Column(name = "requires_id_check", nullable = false)
    private boolean requiresIdCheck;

    @Column(name = "category", length = 30, nullable = false)
    private String category;

    @Column(name = "reception_condition", length = 20)
    private String receptionCondition;

    @Column(name = "reception_notes", columnDefinition = "TEXT")
    private String receptionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
