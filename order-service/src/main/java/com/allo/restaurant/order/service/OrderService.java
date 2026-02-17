package com.allo.restaurant.order.service;

import com.allo.restaurant.order.client.MenuServiceClient;
import com.allo.restaurant.order.dto.*;
import com.allo.restaurant.order.entity.*;
import com.allo.restaurant.order.messaging.OrderStatusPublisher;
import com.allo.restaurant.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MenuServiceClient menuServiceClient;
    private final OrderStatusPublisher orderStatusPublisher;

    public OrderResponse createOrder(CreateOrderRequest request) {
        Customer customer = Customer.builder()
                .fullName(request.getCustomer().getFullName())
                .address(request.getCustomer().getAddress())
                .email(request.getCustomer().getEmail())
                .build();

        List<OrderItem> orderItems = request.getOrderItems().stream()
                .map(itemRequest -> {
                    MenuItemResponse menuItem = menuServiceClient.getMenuItemById(itemRequest.getProductId());
                    return OrderItem.builder()
                            .productId(menuItem.getId())
                            .name(menuItem.getName())
                            .quantity(itemRequest.getQuantity())
                            .price(menuItem.getPrice())
                            .build();
                })
                .collect(Collectors.toList());

        BigDecimal totalAmount = orderItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .customer(customer)
                .orderItems(orderItems)
                .totalAmount(totalAmount)
                .status(OrderStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .build();

        Order savedOrder = orderRepository.save(order);
        return mapToOrderResponse(savedOrder);
    }

    public UpdateOrderStatusResponse updateOrderStatus(String orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));

        order.setStatus(request.getStatus());
        order.setUpdatedAt(LocalDateTime.now());

        Order updatedOrder = orderRepository.save(order);

        OrderStatusNotification notification = OrderStatusNotification.builder()
                .orderId(updatedOrder.getId())
                .fullName(updatedOrder.getCustomer().getFullName())
                .address(updatedOrder.getCustomer().getAddress())
                .email(updatedOrder.getCustomer().getEmail())
                .status(updatedOrder.getStatus())
                .build();
        orderStatusPublisher.publishOrderStatusChange(notification);

        return UpdateOrderStatusResponse.builder()
                .id(updatedOrder.getId())
                .status(updatedOrder.getStatus())
                .updatedAt(updatedOrder.getUpdatedAt())
                .build();
    }

    public OrderHistoryResponse getOrderHistory(int limit, int offset) {
        int pageNumber = offset / limit;
        int offsetInPage = offset % limit;
        
        PageRequest pageRequest = PageRequest.of(pageNumber, limit);
        Page<Order> page = orderRepository.findAll(pageRequest);
        
        List<Order> allOrders = new java.util.ArrayList<>(page.getContent());
        
        if (offsetInPage > 0 && page.hasNext()) {
            Page<Order> nextPage = orderRepository.findAll(PageRequest.of(pageNumber + 1, limit));
            allOrders.addAll(nextPage.getContent());
        }
        
        List<OrderResponse> orders = allOrders.stream()
                .skip(offsetInPage)
                .limit(limit)
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());

        return OrderHistoryResponse.builder()
                .orders(orders)
                .limit(limit)
                .offset(offset)
                .totalRecords(page.getTotalElements())
                .build();
    }

    public OrderResponse getOrderById(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
        return mapToOrderResponse(order);
    }

    private OrderResponse mapToOrderResponse(Order order) {
        CustomerRequest customerRequest = CustomerRequest.builder()
                .fullName(order.getCustomer().getFullName())
                .address(order.getCustomer().getAddress())
                .email(order.getCustomer().getEmail())
                .build();

        List<OrderItemResponse> orderItemResponses = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .productId(item.getProductId())
                        .name(item.getName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .customer(customerRequest)
                .orderItems(orderItemResponses)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
