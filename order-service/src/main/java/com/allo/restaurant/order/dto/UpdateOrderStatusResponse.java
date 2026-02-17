package com.allo.restaurant.order.dto;

import com.allo.restaurant.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusResponse {
    private String id;
    private OrderStatus status;
    private LocalDateTime updatedAt;
}
