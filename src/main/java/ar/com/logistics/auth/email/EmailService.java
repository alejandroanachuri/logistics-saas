package ar.com.logistics.auth.email;

/**
 * Outbound email service. In v1 the implementation is the
 * {@link NoOpEmailService} bean; in v2 a real provider (Resend or
 * SendGrid) will replace it behind the same interface.
 *
 * <p>The interface exists from day one so the call sites in
 * registration / password-reset do not need to change when the
 * provider flips. The bean is registered with
 * {@code @ConditionalOnProperty(app.email.enabled=false)} so
 * production deployments opt-in explicitly.
 */
public interface EmailService {

    /** Verification email for a newly registered admin user. */
    void sendVerificationEmail(String to, String firstName, String verificationToken);

    /** Password reset email. {@code resetToken} is an opaque UUID. */
    void sendPasswordResetEmail(String to, String firstName, String resetToken);

    /** Welcome email. Sent once after the admin verifies their email. */
    void sendWelcomeEmail(String to, String firstName);
}
