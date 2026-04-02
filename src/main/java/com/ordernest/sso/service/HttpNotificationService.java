package com.ordernest.sso.service;

import com.ordernest.sso.config.AppProperties;
import com.ordernest.sso.event.SsoActionEvent;
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
    private final String ssoActionEventsTopic;

    public HttpNotificationService(
        KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper,
        AppProperties appProperties,
        @Value("${app.kafka.topic.sso-action-events:sso.action.event}") String ssoActionEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.ssoActionEventsTopic = ssoActionEventsTopic;
    }

    public void sendVerificationEmail(String recipientEmail, String verificationToken) {
        String verificationUrl = buildLink(
            appProperties.getVerification().getBaseUrl(),
            appProperties.getVerification().getPath(),
            verificationToken
        );
        publish(
            recipientEmail,
            "EMAIL_VERIFICATION_REQUESTED",
            verificationUrl,
            appProperties.getVerification().getTokenMinutes()
        );
    }

    public void sendPasswordResetEmail(String recipientEmail, String resetToken) {
        String resetUrl = buildLink(
            appProperties.getPasswordReset().getBaseUrl(),
            appProperties.getPasswordReset().getPath(),
            resetToken
        );
        publish(
            recipientEmail,
            "PASSWORD_RESET_REQUESTED",
            resetUrl,
            appProperties.getPasswordReset().getTokenMinutes()
        );
    }

    public void sendEmailVerifiedConfirmation(String recipientEmail) {
        publish(recipientEmail, "EMAIL_VERIFIED", null, null);
    }

    public void sendPasswordChangedConfirmation(String recipientEmail) {
        publish(recipientEmail, "PASSWORD_CHANGED", null, null);
    }

    private void publish(String to, String eventType, String actionUrl, Long expiryMinutes) {
        SsoActionEvent event = new SsoActionEvent(to, eventType, actionUrl, expiryMinutes, Instant.now());
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize auth email event for {}", to, ex);
            return;
        }

        kafkaTemplate.send(ssoActionEventsTopic, to, payload)
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
}
