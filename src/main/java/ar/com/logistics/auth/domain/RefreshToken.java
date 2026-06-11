package ar.com.logistics.auth.domain;

import ar.com.logistics.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Maps to {@code public.refresh_tokens} (V5). Token hash = BCrypt(rawUuid). */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor
public class RefreshToken extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_scope", length = 20, nullable = false, updatable = false)
    private UserScope userScope;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    public enum UserScope {
        COMPANY,
        PLATFORM
    }

    /**
     * Setter for {@code revokedAt}. Set by
     * {@code RefreshTokenService.revoke} and
     * {@code RefreshTokenService.handleReuse}. The entity is
     * otherwise immutable to outside layers; only the refresh
     * service mutates it.
     */
    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    /**
     * Setter for {@code replacedBy}. Set by
     * {@code RefreshTokenService.validateAndRotate} when the
     * token is rotated.
     */
    public void setReplacedBy(UUID replacedBy) {
        this.replacedBy = replacedBy;
    }
}
