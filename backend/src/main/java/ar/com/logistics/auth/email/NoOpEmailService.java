package ar.com.logistics.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op implementation of {@link EmailService}. Active when
 * {@code app.email.enabled=false} (the default). Logs a single WARN
 * line per call so the operator sees what would have been sent. In
 * v2, replace this bean by setting {@code app.email.enabled=true} and
 * adding a real provider behind the same interface.
 */
@Component
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEmailService implements EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpEmailService.class);

    @Override
    public void sendVerificationEmail(String to, String firstName, String verificationToken) {
        LOG.warn(
                "[EmailService] Would send verification email to={} firstName={} token={}",
                to,
                firstName,
                verificationToken);
    }

    @Override
    public void sendPasswordResetEmail(String to, String firstName, String resetToken) {
        LOG.warn(
                "[EmailService] Would send password reset email to={} firstName={} token={}",
                to,
                firstName,
                resetToken);
    }

    @Override
    public void sendWelcomeEmail(String to, String firstName) {
        LOG.warn("[EmailService] Would send welcome email to={} firstName={}", to, firstName);
    }
}
