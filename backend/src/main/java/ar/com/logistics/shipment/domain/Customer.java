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
 * Maps to {@code public.customers} (V15). Sender or receiver of a
 * {@link Shipment}. RLS-scoped via V16.
 *
 * <p>{@code personType} is a {@code VARCHAR(10)} constrained to
 * {@code FISICA} / {@code JURIDICA} at the DB level — the CHECK
 * constraints
 * ({@code chk_fisica_dni}, {@code chk_juridica_cuit}, {@code chk_consent_date})
 * enforce the business rules from PRD §11 RN-LOGI-011
 * (FISICA needs DNI; JURIDICA needs CUIT + razon_social; consent
 * date must be set when data_consent is true).
 *
 * <p>Audit columns {@code createdBy} / {@code updatedBy} are present
 * on this table (unlike {@code Address}), so we keep the full
 * {@code BaseEntity} shape inline rather than extending
 * {@link ar.com.logistics.shared.BaseEntity} (which would still work
 * but couples us to a shared parent class — left to PR-3a when
 * services need a uniform mapping).
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "person_type", length = 10, nullable = false)
    private String personType;

    @Column(name = "first_name", length = 100, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "razon_social", length = 200)
    private String razonSocial;

    @Column(name = "dni", length = 8)
    private String dni;

    @Column(name = "cuit_cuil", length = 11)
    private String cuitCuil;

    @Column(name = "tax_condition", length = 30, nullable = false)
    private String taxCondition;

    @Column(name = "phone", length = 30, nullable = false)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "default_address_id")
    private UUID defaultAddressId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "data_consent", nullable = false)
    private boolean dataConsent;

    @Column(name = "consent_date")
    private Instant consentDate;

    @Column(name = "consent_version", length = 20)
    private String consentVersion;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

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
