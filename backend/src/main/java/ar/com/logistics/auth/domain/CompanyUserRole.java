package ar.com.logistics.auth.domain;

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
 * Maps to {@code public.company_user_roles} (V12). The many-to-many
 * junction between {@link CompanyUser} and {@link Role} that replaced
 * the single {@code role_id} FK dropped in V13. RLS-scoped via V14.
 *
 * <p>The relationship between {@code CompanyUser} and {@code Role} is
 * carried as raw UUIDs (no JPA {@code @ManyToOne}) on purpose. The
 * service layer reads the junction rows via
 * {@code CompanyUserRoleRepository.findRoleIdsByUserId(userId)} rather
 * than a JPA-managed association — this avoids the circular
 * dependency between the two owning entities and keeps
 * {@link CompanyUser} free of junction-internal columns like
 * {@code assigned_at} / {@code assigned_by} that would otherwise leak
 * into every read of a {@code company_users} row.
 *
 * <p>The composite PK is expressed via {@link IdClass} (rather than
 * {@code @EmbeddedId}) because the role-catalog reads in PR-2
 * ({@code findRoleIdsByUserId}) and the {@code @PreAuthorize} checks
 * in PR-3 stay trivially JPQL-friendly. Matches the
 * {@code chainId} composite-key pattern already in
 * {@link RefreshToken} (F1 precedent).
 */
@Entity
@Table(name = "company_user_roles")
@IdClass(CompanyUserRole.CompanyUserRoleId.class)
@Getter
@Setter
@NoArgsConstructor
public class CompanyUserRole {

    @Id
    @Column(name = "company_user_id", nullable = false, updatable = false)
    private UUID companyUserId;

    @Id
    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    /**
     * Database default ({@code DEFAULT NOW()}). Marked
     * {@code insertable=false, updatable=false} so JPA never sends a
     * value on INSERT (the DB fills it) and never persists an
     * UPDATE on it either. Reads are still populated by Hibernate
     * after the row is re-fetched.
     */
    @Column(name = "assigned_at", insertable = false, updatable = false)
    private Instant assignedAt;

    /**
     * Nullable on purpose: registration-time rows have
     * {@code assigned_by = NULL} because {@code BaseEntity.@PrePersist}
     * does not set {@code createdBy} (the registration service does
     * not have an actor in scope). The {@code isFirstAdmin} business
     * rule depends on this nullability.
     */
    @Column(name = "assigned_by")
    private UUID assignedBy;

    /**
     * Composite-PK class required by {@link IdClass}. Carries the two
     * FK columns verbatim; JPA uses it as the key in the persistence
     * context. Must be {@link Serializable} and implement
     * {@link Object#equals(Object)} / {@link Object#hashCode()} per
     * the JPA spec.
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class CompanyUserRoleId implements Serializable {

        private UUID companyUserId;

        private UUID roleId;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CompanyUserRoleId that)) {
                return false;
            }
            return Objects.equals(companyUserId, that.companyUserId) && Objects.equals(roleId, that.roleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(companyUserId, roleId);
        }
    }
}
