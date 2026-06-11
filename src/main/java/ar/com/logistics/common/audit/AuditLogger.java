package ar.com.logistics.common.audit;

import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes {@link AuditEvent}s to {@code public.audit_log}. The logger
 * uses the {@code systemDataSource} pool intentionally: audit_log has
 * no RLS so we want the write to land even if the request thread is
 * running under {@code app_user} (company pool) and the GUC has not
 * been set yet.
 *
 * <p>{@link #logAsync(AuditEvent)} runs the insert on a background
 * thread so a slow audit insert never blocks the request. Failures
 * are logged at ERROR but never re-thrown — an audit miss must not
 * break the user-facing flow.
 */
@Component
public class AuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogger.class);

    private final JdbcTemplate systemJdbc;
    private final ObjectMapper objectMapper;

    public AuditLogger(@Qualifier("systemDataSource") DataSource systemDataSource, ObjectMapper objectMapper) {
        this.systemJdbc = new JdbcTemplate(systemDataSource);
        this.objectMapper = objectMapper;
    }

    public void log(AuditEvent event) {
        try {
            systemJdbc.update(
                    "INSERT INTO public.audit_log "
                            + "(event_type, user_id, user_scope, tenant_id, ip_address, user_agent, metadata) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)",
                    event.eventType(),
                    event.userId(),
                    event.userScope() == null ? null : event.userScope().name(),
                    event.tenantId(),
                    event.ipAddress(),
                    event.userAgent(),
                    serializeMetadata(event.metadata()));
        } catch (RuntimeException ex) {
            // The caller chose to log synchronously; surface the error
            // so they can decide whether to compensate.
            LOG.error("Failed to write audit event {}", event.eventType(), ex);
            throw ex;
        }
    }

    @Async
    public void logAsync(AuditEvent event) {
        try {
            log(event);
        } catch (RuntimeException ex) {
            LOG.error("Async audit write failed for {}", event.eventType(), ex);
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException ex) {
            LOG.warn("Could not serialize audit metadata, falling back to empty map", ex);
            return "{}";
        }
    }
}
