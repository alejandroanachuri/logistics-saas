package ar.com.logistics.shared;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Base class for soft-deletable, audit-friendly entities.
 *
 * <p>Defines the common {@code createdAt} / {@code updatedAt} / {@code deletedAt}
 * / {@code createdBy} / {@code updatedBy} columns. Subclasses map their own
 * UUID primary key.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity {

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

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Protected setter for {@code createdBy}. Used by services in the
     * same package (or subclass) as the concrete entity to stamp the
     * acting actor on rows inserted outside of {@code RegistrationService}
     * (e.g. {@code CompanyUsersService.create}). The registration
     * service intentionally leaves this NULL because the
     * {@code isFirstAdmin} derivation depends on it.
     */
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Protected setter for {@code deletedAt}. Used by services that
     * perform soft delete (e.g. {@code CompanyUsersService.disable})
     * or restore a soft-deleted row ({@code reactivate}).
     */
    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
