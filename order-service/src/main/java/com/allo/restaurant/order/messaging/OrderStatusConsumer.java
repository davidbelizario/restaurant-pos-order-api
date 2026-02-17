package com.allo.restaurant.order.messaging;

import com.allo.restaurant.order.dto.OrderStatusNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderStatusConsumer {

    @RabbitListener(queues = "${rabbitmq.queue.name}")
    public void consumeOrderStatusChange(OrderStatusNotification notification) {
        log.info("""
                ========== ORDER STATUS NOTIFICATION ==========
                Order ID: {}
                Customer: {}
                Address: {}
                Email: {}
                New Status: {}
                Simulating notification sent to customer {} at {}
                ================================================""",
                notification.getOrderId(),
                notification.getFullName(),
                notification.getAddress(),
                notification.getEmail(),
                notification.getStatus(),
                notification.getFullName(),
                notification.getEmail());
    }
}
