package com.allo.restaurant.order.dto;

import com.allo.restaurant.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusNotification {
    private String orderId;
    private String fullName;
    private String address;
    private String email;
    private OrderStatus status;
}
