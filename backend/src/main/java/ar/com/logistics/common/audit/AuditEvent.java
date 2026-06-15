package ar.com.logistics.common.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Single audit record. Immutable; carries the wire-format fields that
 * land in {@code public.audit_log} per V6 (eventType, userId,
 * userScope, tenantId, ipAddress, userAgent, metadata JSONB).
 *
 * <p>Nullable fields represent "not applicable for this event":
 * e.g. {@code TENANT_REGISTERED} has {@code userId} but not
 * {@code tenantId} before the insert.
 */
public record AuditEvent(
        String eventType,
        UUID userId,
        UserScope userScope,
        UUID tenantId,
        String ipAddress,
        String userAgent,
        Map<String, Object> metadata) {

    public enum UserScope {
        COMPANY,
        PLATFORM,
        ANONYMOUS
    }
}
