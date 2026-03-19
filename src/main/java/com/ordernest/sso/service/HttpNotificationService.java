package com.ordernest.sso.service;

import com.ordernest.sso.config.AppProperties;
import com.ordernest.sso.event.EmailNotificationEvent;
import com.ordernest.sso.event.OrderStatusNotificationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import com.ordernest.sso.model.User;
import com.ordernest.sso.repository.UserRepository;

@Service
public class HttpNotificationService {

    private static final Logger log = LoggerFactory.getLogger(HttpNotificationService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final String emailEventsTopic;
    private final String orderDetailsBaseUrl;
    private static final DateTimeFormatter ORDER_TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a z", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

    public HttpNotificationService(
        KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper,
        AppProperties appProperties,
        UserRepository userRepository,
        @Value("${app.kafka.topic.email-events:notification.email.events}") String emailEventsTopic,
        @Value("${app.order-status.base-url:http://localhost:5173}") String orderDetailsBaseUrl
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.userRepository = userRepository;
        this.emailEventsTopic = emailEventsTopic;
        this.orderDetailsBaseUrl = orderDetailsBaseUrl;
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

    public void sendOrderStatusEmail(OrderStatusNotificationEvent event) {
        if (event == null || event.userId() == null || event.orderId() == null || event.currentStatus() == null) {
            return;
        }

        Optional<User> userOptional = userRepository.findById(event.userId());
        if (userOptional.isEmpty()) {
            log.warn("Skipping order status email because user was not found for userId={}", event.userId());
            return;
        }

        User user = userOptional.get();
        String subject = switch (event.currentStatus()) {
            case "CONFIRMED" -> "Order confirmed and ready to move";
            case "CANCELLED" -> "Update on your OrderNest order";
            case "FAILED" -> "Your OrderNest order needs attention";
            case "SUCCESS" -> "Your OrderNest order is successful";
            default -> "Your OrderNest order status changed";
        };
        String body = buildOrderStatusTemplate(user.getEmail(), event);
        publish(user.getEmail(), subject, body, "ORDER_STATUS_UPDATED");
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

    private String buildOrderStatusTemplate(String recipientEmail, OrderStatusNotificationEvent event) {
        String accentStart = switch (event.currentStatus()) {
            case "CONFIRMED", "SUCCESS" -> "#0f766e";
            case "CANCELLED" -> "#b45309";
            case "FAILED" -> "#b91c1c";
            default -> "#1d4ed8";
        };
        String accentEnd = switch (event.currentStatus()) {
            case "CONFIRMED", "SUCCESS" -> "#14b8a6";
            case "CANCELLED" -> "#f59e0b";
            case "FAILED" -> "#ef4444";
            default -> "#60a5fa";
        };
        String badgeBackground = switch (event.currentStatus()) {
            case "CONFIRMED", "SUCCESS" -> "#dcfce7";
            case "CANCELLED" -> "#fef3c7";
            case "FAILED" -> "#fee2e2";
            default -> "#dbeafe";
        };
        String badgeColor = switch (event.currentStatus()) {
            case "CONFIRMED", "SUCCESS" -> "#166534";
            case "CANCELLED" -> "#92400e";
            case "FAILED" -> "#991b1b";
            default -> "#1d4ed8";
        };
        String orderUrl = buildOrderDetailsUrl(event.orderId());
        String reasonBlock = (event.reason() == null || event.reason().isBlank())
            ? ""
            : """
              <tr>
                <td style="padding-top:18px;">
                  <div style="background:#fff7ed;border:1px solid #fed7aa;border-radius:14px;padding:16px 18px;">
                    <p style="margin:0 0 6px 0;font-size:12px;letter-spacing:0.08em;text-transform:uppercase;color:#9a3412;font-weight:700;">What changed</p>
                    <p style="margin:0;color:#7c2d12;font-size:14px;line-height:1.7;">%s</p>
                  </div>
                </td>
              </tr>
              """.formatted(escapeHtml(event.reason()));

        String previousStatus = event.previousStatus() == null || event.previousStatus().isBlank()
            ? "New order"
            : escapeHtml(event.previousStatus());

        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Order status update</title>
            </head>
            <body style="margin:0;padding:0;background:#f6f7fb;font-family:Arial,Helvetica,sans-serif;color:#111827;">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:
                radial-gradient(circle at top left,#fff1f2 0%%,#f6f7fb 38%%,#eef6ff 100%%);padding:28px 14px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="640" cellspacing="0" cellpadding="0" style="max-width:640px;background:#ffffff;border-radius:28px;overflow:hidden;box-shadow:0 22px 80px rgba(15,23,42,0.14);">
                      <tr>
                        <td style="background:linear-gradient(135deg,%s 0%%,%s 100%%);padding:34px 36px 28px 36px;color:#ffffff;">
                          <div style="font-size:12px;letter-spacing:0.18em;text-transform:uppercase;opacity:0.82;font-weight:700;">OrderNest status update</div>
                          <h1 style="margin:14px 0 10px 0;font-size:30px;line-height:1.2;font-weight:800;">Your order is now %s</h1>
                          <p style="margin:0;max-width:470px;font-size:15px;line-height:1.7;opacity:0.94;">
                            We wanted you to know the latest update on your OrderNest purchase right away.
                          </p>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:30px 36px 34px 36px;">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                            <tr>
                              <td>
                                <span style="display:inline-block;padding:8px 14px;border-radius:999px;background:%s;color:%s;font-size:12px;font-weight:800;letter-spacing:0.08em;text-transform:uppercase;">%s</span>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding-top:20px;">
                                <p style="margin:0;color:#374151;font-size:15px;line-height:1.8;">
                                  Hello %s,
                                </p>
                                <p style="margin:14px 0 0 0;color:#4b5563;font-size:15px;line-height:1.8;">
                                  Order <strong>#%s</strong> moved from <strong>%s</strong> to <strong>%s</strong>.
                                </p>
                              </td>
                            </tr>
                            %s
                            <tr>
                              <td style="padding-top:22px;">
                                <div style="border:1px solid #e5e7eb;border-radius:22px;padding:22px;background:linear-gradient(180deg,#ffffff 0%%,#fafafa 100%%);">
                                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                                    <tr>
                                      <td style="font-size:13px;color:#6b7280;padding-bottom:14px;text-transform:uppercase;letter-spacing:0.08em;font-weight:700;">Order summary</td>
                                    </tr>
                                    <tr>
                                      <td style="padding:0 0 14px 0;border-bottom:1px solid #f3f4f6;">
                                        <div style="font-size:20px;line-height:1.4;font-weight:800;color:#111827;">%s</div>
                                        <div style="margin-top:6px;font-size:14px;color:#6b7280;">Quantity: %s</div>
                                      </td>
                                    </tr>
                                    <tr>
                                      <td style="padding-top:16px;">
                                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                                          <tr>
                                            <td style="padding:6px 0;color:#6b7280;font-size:14px;">Total amount</td>
                                            <td align="right" style="padding:6px 0;color:#111827;font-size:14px;font-weight:700;">%s %s</td>
                                          </tr>
                                          <tr>
                                            <td style="padding:6px 0;color:#6b7280;font-size:14px;">Payment</td>
                                            <td align="right" style="padding:6px 0;color:#111827;font-size:14px;font-weight:700;">%s</td>
                                          </tr>
                                          <tr>
                                            <td style="padding:6px 0;color:#6b7280;font-size:14px;">Shipment</td>
                                            <td align="right" style="padding:6px 0;color:#111827;font-size:14px;font-weight:700;">%s</td>
                                          </tr>
                                          <tr>
                                            <td style="padding:6px 0;color:#6b7280;font-size:14px;">Updated at</td>
                                            <td align="right" style="padding:6px 0;color:#111827;font-size:14px;font-weight:700;">%s</td>
                                          </tr>
                                        </table>
                                      </td>
                                    </tr>
                                  </table>
                                </div>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding-top:24px;">
                                <a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:14px 22px;border-radius:14px;font-weight:800;font-size:14px;">View Order Details</a>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding-top:14px;">
                                <p style="margin:0;color:#6b7280;font-size:12px;line-height:1.7;">
                                  If the button does not work, copy and paste this link into your browser:
                                </p>
                                <p style="margin:8px 0 0 0;word-break:break-all;color:%s;font-size:12px;line-height:1.7;">%s</p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:18px 36px 24px 36px;background:#f9fafb;border-top:1px solid #e5e7eb;color:#6b7280;font-size:12px;line-height:1.8;">
                          You are receiving this email because your OrderNest order status changed. If you need help, reply to this message or contact support.
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(
            accentStart,
            accentEnd,
            escapeHtml(event.currentStatus()),
            badgeBackground,
            badgeColor,
            escapeHtml(event.currentStatus()),
            escapeHtml(recipientEmail),
            escapeHtml(event.orderId()),
            previousStatus,
            escapeHtml(event.currentStatus()),
            reasonBlock,
            escapeHtml(event.productName() == null || event.productName().isBlank() ? "Your OrderNest item" : event.productName()),
            String.valueOf(event.quantity()),
            escapeHtml(event.currency() == null || event.currency().isBlank() ? "INR" : event.currency()),
            event.totalAmount() == null ? "0.00" : event.totalAmount().toPlainString(),
            escapeHtml(event.paymentStatus() == null ? "N/A" : event.paymentStatus()),
            escapeHtml(event.shipmentStatus() == null ? "N/A" : event.shipmentStatus()),
            ORDER_TIMESTAMP_FORMATTER.format(event.timestamp() == null ? Instant.now() : event.timestamp()),
            orderUrl,
            accentStart,
            orderUrl
        );
    }

    private String buildOrderDetailsUrl(String orderId) {
        String normalizedBase = orderDetailsBaseUrl == null || orderDetailsBaseUrl.isBlank()
            ? "http://localhost:5173"
            : orderDetailsBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        return normalizedBase + "/orders/" + orderId;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
