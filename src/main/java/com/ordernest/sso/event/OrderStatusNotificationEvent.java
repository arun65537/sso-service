package com.ordernest.sso.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderStatusNotificationEvent(
    String orderId,
    UUID userId,
    UUID productId,
    String productName,
    Integer quantity,
    BigDecimal totalAmount,
    String currency,
    String previousStatus,
    String currentStatus,
    String paymentStatus,
    String shipmentStatus,
    String reason,
    Instant timestamp
) {}
