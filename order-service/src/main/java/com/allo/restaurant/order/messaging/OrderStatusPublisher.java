package com.allo.restaurant.order.messaging;

import com.allo.restaurant.order.dto.OrderStatusNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.name}")
    private String exchangeName;

    @Value("${rabbitmq.routing.key}")
    private String routingKey;

    public void publishOrderStatusChange(OrderStatusNotification notification) {
        log.info("Publishing order status change: orderId={}, status={}", notification.getOrderId(), notification.getStatus());
        rabbitTemplate.convertAndSend(exchangeName, routingKey, notification);
    }
}
