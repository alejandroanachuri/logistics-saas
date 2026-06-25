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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps to {@code public.tracking_events} (V15). APPEND-ONLY — there
 * is no {@code updated_at} or {@code deleted_at} column on the
 * table. Once a row is inserted, it is immutable; corrections happen
 * via a follow-up event referencing the original via {@code metadata}.
 *
 * <p>Idempotency is enforced by the {@code event_hash} UNIQUE column
 * (length 64, SHA-256 hex). The hash is computed by
 * {@code EventHashCalculator} (PR-2 utility) — see its javadoc for
 * the canonicalization rules.
 *
 * <p>{@code metadata} is stored as {@code JSONB} on the DB side;
 * Hibernate 6+ maps it transparently via
 * {@link JdbcTypeCode} {@link SqlTypes#JSON}. The Java type is left
 * as {@code String} so callers can either pass pre-serialized JSON
 * or use Jackson in the service layer to assemble the payload. The
 * DB column has no default, so null is allowed (events without
 * auxiliary metadata simply omit the field).
 *
 * <p>{@code source_ip} carries up to 45 chars to fit an IPv6
 * address (RFC 4291 §2.2 — 8 groups × 4 hex digits + 7 colons).
 */
@Entity
@Table(name = "tracking_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackingEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "event_type", length = 60, nullable = false)
    private String eventType;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_source", length = 20, nullable = false)
    private String eventSource;

    @Column(name = "event_hash", length = 64, nullable = false, unique = true)
    private String eventHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
