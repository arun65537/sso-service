package com.ordernest.sso.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordernest.sso.event.OrderStatusNotificationEvent;
import com.ordernest.sso.service.HttpNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusNotificationEventListener {

    private final ObjectMapper objectMapper;
    private final HttpNotificationService httpNotificationService;

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
