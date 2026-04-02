package com.ordernest.sso.event;

import java.time.Instant;

public record SsoActionEvent(
    String to,
    String eventType,
    String actionUrl,
    Long expiryMinutes,
    Instant timestamp
) {}
