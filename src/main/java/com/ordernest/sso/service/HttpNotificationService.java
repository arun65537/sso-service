package com.ordernest.sso.service;

import com.ordernest.sso.config.AppProperties;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class HttpNotificationService {

    private final RestClient restClient;
    private final AppProperties appProperties;

    public HttpNotificationService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.restClient = RestClient.create();
    }

    public void sendVerificationEmail(String recipientEmail, String verificationToken) {
        String verificationUrl = buildLink(
            appProperties.getVerification().getBaseUrl(),
            appProperties.getVerification().getPath(),
            verificationToken
        );
        String subject = "Verify your OrderNest account";
        String body = buildVerificationTemplate(verificationUrl, appProperties.getVerification().getTokenMinutes());
        send(recipientEmail, subject, body);
    }

    public void sendPasswordResetEmail(String recipientEmail, String resetToken) {
        String resetUrl = buildLink(
            appProperties.getPasswordReset().getBaseUrl(),
            appProperties.getPasswordReset().getPath(),
            resetToken
        );
        String subject = "Reset your OrderNest password";
        String body = buildPasswordResetTemplate(resetUrl, appProperties.getPasswordReset().getTokenMinutes());
        send(recipientEmail, subject, body);
    }

    private void send(String to, String subject, String body) {
        restClient.post()
            .uri(resolveEmailEndpoint())
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("to", to, "subject", subject, "body", body))
            .retrieve()
            .toBodilessEntity();
    }

    private String resolveEmailEndpoint() {
        String baseUrl = appProperties.getNotification().getBaseUrl();
        String emailPath = appProperties.getNotification().getEmailPath();

        if (baseUrl != null && !baseUrl.isBlank()) {
            String normalizedBase = baseUrl.trim();
            if (normalizedBase.endsWith("/")) {
                normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
            }

            String normalizedPath = (emailPath == null || emailPath.isBlank()) ? "/notifications/email" : emailPath.trim();
            if (!normalizedPath.startsWith("/")) {
                normalizedPath = "/" + normalizedPath;
            }
            return normalizedBase + normalizedPath;
        }

        throw new IllegalStateException("Notification service base URL is not configured");
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
}
