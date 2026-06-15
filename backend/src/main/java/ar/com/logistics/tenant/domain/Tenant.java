package ar.com.logistics.tenant.domain;

import ar.com.logistics.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tenant — the company that subscribes to the SaaS.
 *
 * <p>Maps to {@code public.tenants} (V1). The address fields are stored
 * as individual columns rather than an embedded type because Hibernate
 * 6.6 with a single table keeps the schema simple. Slug and CUIT are
 * globally unique.
 */
@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor
public class Tenant extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", length = 12, nullable = false, unique = true)
    private String slug;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "commercial_name")
    private String commercialName;

    @Column(name = "cuit", length = 13, nullable = false, unique = true)
    private String cuit;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type", length = 30, nullable = false)
    private TaxType taxType;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "country", length = 2, nullable = false)
    private String country;

    @Column(name = "province", length = 100)
    private String province;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "address_line")
    private String addressLine;

    @Column(name = "address_number", length = 20)
    private String addressNumber;

    @Column(name = "address_floor", length = 10)
    private String addressFloor;

    @Column(name = "address_apartment", length = 10)
    private String addressApartment;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TenantStatus status;

    /** Static factory used by the registration service. */
    public static Tenant create(String slug, String legalName, String cuit, TaxType taxType, String contactEmail) {
        Tenant t = new Tenant();
        t.id = UUID.randomUUID();
        t.slug = slug;
        t.legalName = legalName;
        t.cuit = cuit;
        t.taxType = taxType;
        t.contactEmail = contactEmail;
        t.country = "AR";
        t.status = TenantStatus.ACTIVE;
        return t;
    }

    /**
     * Static factory with the full set of fields the registration
     * service needs. The address fields are stored as individual
     * columns (V1) so we set them all here and let the
     * {@code @PrePersist} on {@link ar.com.logistics.shared.BaseEntity}
     * stamp the audit columns.
     */
    public static Tenant create(
            String slug,
            String legalName,
            String commercialName,
            String cuit,
            TaxType taxType,
            String contactEmail,
            String contactPhone,
            String country,
            String province,
            String city,
            String addressLine,
            String addressNumber,
            String addressFloor,
            String addressApartment,
            String postalCode) {
        Tenant t = create(slug, legalName, cuit, taxType, contactEmail);
        t.commercialName = commercialName;
        t.contactPhone = contactPhone;
        t.country = country == null ? "AR" : country;
        t.province = province;
        t.city = city;
        t.addressLine = addressLine;
        t.addressNumber = addressNumber;
        t.addressFloor = addressFloor;
        t.addressApartment = addressApartment;
        t.postalCode = postalCode;
        return t;
    }
}
