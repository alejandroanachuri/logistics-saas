package ar.com.logistics.shipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code public.addresses} (V15). A customer's delivery or
 * pickup address, soft-deletable and tenant-scoped via V16 RLS.
 *
 * <p>FK associations to {@link Customer} / {@link Shipment} are kept
 * as primitive UUID fields on purpose — service-layer code will
 * resolve them when those flows are built (PR-3a/3b), and avoiding
 * JPA {@code @ManyToOne} keeps this entity free of cycles and
 * unnecessary eager-loading risk.
 */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "street", length = 200, nullable = false)
    private String street;

    @Column(name = "number", length = 20, nullable = false)
    private String number;

    @Column(name = "floor", length = 20)
    private String floor;

    @Column(name = "apartment", length = 20)
    private String apartment;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Column(name = "province", length = 100, nullable = false)
    private String province;

    @Column(name = "postal_code", length = 10, nullable = false)
    private String postalCode;

    @Column(name = "reference", columnDefinition = "TEXT")
    private String reference;

    @Column(name = "country", length = 2, nullable = false)
    private String country;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Sets audit timestamps on insert. Mirrors the pattern used by
     * {@link ar.com.logistics.shared.BaseEntity} — duplicated here
     * because {@code Address} does not extend {@code BaseEntity}
     * (which would pull in {@code createdBy} / {@code updatedBy}
     * columns that {@code addresses} does not have).
     */
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
