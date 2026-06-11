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

/** Maps to {@code public.company_users} (V3). RLS-scoped by tenant. */
@Entity
@Table(name = "company_users")
@Getter
@NoArgsConstructor
public class CompanyUser extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "username", length = 30, nullable = false)
    private String username;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private UserStatus status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "verification_token")
    private UUID verificationToken;

    @Column(name = "verification_token_expires_at")
    private Instant verificationTokenExpiresAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    public enum UserStatus {
        PENDING_VERIFICATION,
        ACTIVE,
        DISABLED
    }

    /**
     * Setter for the BCrypt-hashed password. The registration
     * service BCrypts the plain-text password and sets the result
     * here. Kept package-private intentionally: callers outside
     * {@code auth.service} should not be able to bypass the
     * service's policy checks by setting the hash directly.
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Setter for the failed-login counter. Updated by
     * {@code LoginService} on every failed password match; reset to 0
     * on every successful login.
     */
    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    /**
     * Setter for the lockout timestamp. When non-null and in the
     * future, the login flow rejects the attempt with
     * {@code ACCOUNT_LOCKED}. The {@code LoginService} sets it on
     * the 5th failed attempt and clears it on the next successful
     * login.
     */
    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    /**
     * Setter for the last-login timestamp. Updated by
     * {@code LoginService} on every successful login.
     */
    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /**
     * Static factory used by the registration service to create a
     * fresh first-admin user. Sets the {@code id}, leaves the audit
     * timestamps to {@link BaseEntity}'s {@code @PrePersist}
     * hook, and pre-populates the verification token + 24-hour
     * expiry. The {@code passwordHash} is set by the service
     * after BCrypt encoding.
     */
    public static CompanyUser create(
            UUID tenantId, UUID roleId, String username, String email, String firstName, String lastName) {
        CompanyUser u = new CompanyUser();
        u.id = UUID.randomUUID();
        u.tenantId = tenantId;
        u.roleId = roleId;
        u.username = username;
        u.email = email;
        u.firstName = firstName;
        u.lastName = lastName;
        u.emailVerified = false;
        u.status = UserStatus.PENDING_VERIFICATION;
        u.verificationToken = UUID.randomUUID();
        u.verificationTokenExpiresAt = Instant.now().plus(java.time.Duration.ofHours(24));
        return u;
    }
}
