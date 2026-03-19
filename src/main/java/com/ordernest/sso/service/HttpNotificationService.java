package com.ordernest.sso.service;

import com.ordernest.sso.config.AppProperties;
import com.ordernest.sso.event.EmailNotificationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class HttpNotificationService {

    private static final Logger log = LoggerFactory.getLogger(HttpNotificationService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final String emailEventsTopic;

    public HttpNotificationService(
        KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper,
        AppProperties appProperties,
        @Value("${app.kafka.topic.email-events:notification.email.events}") String emailEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.emailEventsTopic = emailEventsTopic;
    }

    public void sendVerificationEmail(String recipientEmail, String verificationToken) {
        String verificationUrl = buildLink(
            appProperties.getVerification().getBaseUrl(),
            appProperties.getVerification().getPath(),
            verificationToken
        );
        String subject = "Verify your OrderNest account";
        String body = buildVerificationTemplate(verificationUrl, appProperties.getVerification().getTokenMinutes());
        publish(recipientEmail, subject, body, "EMAIL_VERIFICATION_REQUESTED");
    }

    public void sendPasswordResetEmail(String recipientEmail, String resetToken) {
        String resetUrl = buildLink(
            appProperties.getPasswordReset().getBaseUrl(),
            appProperties.getPasswordReset().getPath(),
            resetToken
        );
        String subject = "Reset your OrderNest password";
        String body = buildPasswordResetTemplate(resetUrl, appProperties.getPasswordReset().getTokenMinutes());
        publish(recipientEmail, subject, body, "PASSWORD_RESET_REQUESTED");
    }

    public void sendEmailVerifiedConfirmation(String recipientEmail) {
        String subject = "Your email is verified";
        String body = buildEmailVerifiedTemplate();
        publish(recipientEmail, subject, body, "EMAIL_VERIFIED");
    }

    public void sendPasswordChangedConfirmation(String recipientEmail) {
        String subject = "Your password was changed";
        String body = buildPasswordChangedTemplate();
        publish(recipientEmail, subject, body, "PASSWORD_CHANGED");
    }

    private void publish(String to, String subject, String body, String eventType) {
        EmailNotificationEvent event = new EmailNotificationEvent(to, subject, body, eventType, Instant.now());
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize auth email event for {}", to, ex);
            return;
        }

        kafkaTemplate.send(emailEventsTopic, to, payload)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish auth email event for {}", to, ex);
                } else {
                    log.info("Published auth email event={}, topic={}, partition={}, offset={}, recipient={}",
                        eventType,
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        to
                    );
                }
            });
    }

    private String buildLink(String baseUrl, String path, String token) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Notification base URL is not configured");
        }
        String normalizedBase = baseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        String normalizedPath = (path == null || path.isBlank()) ? "" : path.trim();
        if (!normalizedPath.isEmpty() && !normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }

        String url = normalizedBase + normalizedPath;
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "token=" + token;
    }

    private String buildVerificationTemplate(String verificationUrl, long expiryMinutes) {
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Verify your email</title>
            </head>
            <body style="margin:0;padding:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f7fb;padding:24px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border-radius:14px;overflow:hidden;box-shadow:0 8px 28px rgba(16,24,40,0.10);">
                      <tr>
                        <td style="background:linear-gradient(135deg,#0f766e,#0ea5e9);padding:28px 32px;color:#ffffff;">
                          <h1 style="margin:0;font-size:24px;line-height:1.3;">Confirm your email</h1>
                          <p style="margin:10px 0 0 0;font-size:14px;opacity:0.95;">Welcome to OrderNest</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:32px;">
                          <p style="margin:0 0 14px 0;color:#1f2937;font-size:15px;line-height:1.7;">
                            Thanks for signing up. Please verify your email address to activate your account.
                          </p>
                          <p style="margin:0 0 20px 0;color:#4b5563;font-size:14px;line-height:1.7;">
                            This verification link expires in %d minutes.
                          </p>
                          <p style="margin:0 0 22px 0;">
                            <a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;font-weight:bold;font-size:14px;">Verify Email</a>
                          </p>
                          <p style="margin:0;color:#6b7280;font-size:12px;line-height:1.7;">
                            If the button does not work, copy and paste this URL:
                          </p>
                          <p style="margin:8px 0 0 0;word-break:break-all;color:#0f766e;font-size:12px;line-height:1.6;">%s</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:18px 32px;background:#f9fafb;border-top:1px solid #e5e7eb;color:#6b7280;font-size:12px;">
                          If you did not create this account, you can safely ignore this email.
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(expiryMinutes, verificationUrl, verificationUrl);
    }

    private String buildPasswordResetTemplate(String resetUrl, long expiryMinutes) {
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Reset your password</title>
            </head>
            <body style="margin:0;padding:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f7fb;padding:24px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border-radius:14px;overflow:hidden;box-shadow:0 8px 28px rgba(16,24,40,0.10);">
                      <tr>
                        <td style="background:linear-gradient(135deg,#9333ea,#ec4899);padding:28px 32px;color:#ffffff;">
                          <h1 style="margin:0;font-size:24px;line-height:1.3;">Password reset request</h1>
                          <p style="margin:10px 0 0 0;font-size:14px;opacity:0.95;">OrderNest account security</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:32px;">
                          <p style="margin:0 0 14px 0;color:#1f2937;font-size:15px;line-height:1.7;">
                            We received a request to reset your password.
                          </p>
                          <p style="margin:0 0 20px 0;color:#4b5563;font-size:14px;line-height:1.7;">
                            This reset link expires in %d minutes.
                          </p>
                          <p style="margin:0 0 22px 0;">
                            <a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;font-weight:bold;font-size:14px;">Reset Password</a>
                          </p>
                          <p style="margin:0;color:#6b7280;font-size:12px;line-height:1.7;">
                            If the button does not work, copy and paste this URL:
                          </p>
                          <p style="margin:8px 0 0 0;word-break:break-all;color:#9333ea;font-size:12px;line-height:1.6;">%s</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:18px 32px;background:#f9fafb;border-top:1px solid #e5e7eb;color:#6b7280;font-size:12px;">
                          If you did not request a password reset, please ignore this email.
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(expiryMinutes, resetUrl, resetUrl);
    }

    private String buildEmailVerifiedTemplate() {
        return """
            <p>Your OrderNest email has been verified successfully.</p>
            <p>You can now login and continue using your account.</p>
            """;
    }

    private String buildPasswordChangedTemplate() {
        return """
            <p>Your OrderNest password was changed successfully.</p>
            <p>If you did not perform this action, please reset your password immediately.</p>
            """;
    }
}
