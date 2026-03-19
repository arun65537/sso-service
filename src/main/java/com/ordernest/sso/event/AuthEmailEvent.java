package com.ordernest.sso.event;

import java.time.Instant;

public record AuthEmailEvent(
    String to,
    String subject,
    String body,
    String eventType,
    Instant timestamp
) {}
