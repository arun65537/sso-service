package com.ordernest.sso.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordernest.sso.event.OrderStatusNotificationEvent;
import com.ordernest.sso.service.HttpNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusNotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusNotificationEventListener.class);

    private final ObjectMapper objectMapper;
    private final HttpNotificationService httpNotificationService;

    public OrderStatusNotificationEventListener(
        ObjectMapper objectMapper,
        HttpNotificationService httpNotificationService
    ) {
        this.objectMapper = objectMapper;
        this.httpNotificationService = httpNotificationService;
    }

    @KafkaListener(
        topics = "${app.kafka.topic.order-status-events}",
        groupId = "${app.kafka.consumer.order-status-group-id}"
    )
    public void onOrderStatusEvent(String payload) {
        try {
            OrderStatusNotificationEvent event = objectMapper.readValue(payload, OrderStatusNotificationEvent.class);
            httpNotificationService.sendOrderStatusEmail(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse order status notification payload: {}", payload, ex);
        } catch (Exception ex) {
            log.error("Failed to process order status notification payload: {}", payload, ex);
        }
    }
}
