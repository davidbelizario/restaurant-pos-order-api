package com.allo.restaurant.order.service;

import com.allo.restaurant.order.client.MenuServiceClient;
import com.allo.restaurant.order.dto.*;
import com.allo.restaurant.order.entity.*;
import com.allo.restaurant.order.messaging.OrderStatusPublisher;
import com.allo.restaurant.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MenuServiceClient menuServiceClient;

    @Mock
    private OrderStatusPublisher orderStatusPublisher;

    @InjectMocks
    private OrderService orderService;

    private CustomerRequest customerRequest;
    private Customer customer;
    private MenuItemResponse menuItemResponse;
    private Order savedOrder;

    @BeforeEach
    void setUp() {
        customerRequest = CustomerRequest.builder()
                .fullName("John Doe")
                .address("123 Main St")
                .email("john@email.com")
                .build();

        customer = Customer.builder()
                .fullName("John Doe")
                .address("123 Main St")
                .email("john@email.com")
                .build();

        menuItemResponse = MenuItemResponse.builder()
                .id("menu-1")
                .name("Classic Burger")
                .description("Artisan burger")
                .price(new BigDecimal("12.90"))
                .createdAt(LocalDateTime.now())
                .build();

        savedOrder = Order.builder()
                .id("order-1")
                .customer(customer)
                .orderItems(List.of(
                        OrderItem.builder()
                                .productId("menu-1")
                                .name("Classic Burger")
                                .quantity(2)
                                .price(new BigDecimal("12.90"))
                                .build()
                ))
                .totalAmount(new BigDecimal("25.80"))
                .status(OrderStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        @Test
        @DisplayName("Should create order successfully with valid request")
        void shouldCreateOrderSuccessfully() {
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .customer(customerRequest)
                    .orderItems(List.of(
                            OrderItemRequest.builder()
                                    .productId("menu-1")
                                    .quantity(2)
                                    .build()
                    ))
                    .build();

            when(menuServiceClient.getMenuItemById("menu-1")).thenReturn(menuItemResponse);
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            OrderResponse response = orderService.createOrder(request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("order-1");
            assertThat(response.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(response.getCustomer().getFullName()).isEqualTo("John Doe");
            assertThat(response.getCustomer().getEmail()).isEqualTo("john@email.com");
            assertThat(response.getOrderItems()).hasSize(1);
            assertThat(response.getOrderItems().get(0).getProductId()).isEqualTo("menu-1");
            assertThat(response.getOrderItems().get(0).getName()).isEqualTo("Classic Burger");
            assertThat(response.getOrderItems().get(0).getQuantity()).isEqualTo(2);
            assertThat(response.getOrderItems().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("12.90"));
            assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("25.80"));

            verify(menuServiceClient).getMenuItemById("menu-1");
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Should calculate total amount correctly with multiple items")
        void shouldCalculateTotalAmountWithMultipleItems() {
            MenuItemResponse secondItem = MenuItemResponse.builder()
                    .id("menu-2")
                    .name("Fries")
                    .description("Crispy fries")
                    .price(new BigDecimal("5.50"))
                    .build();

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .customer(customerRequest)
                    .orderItems(List.of(
                            OrderItemRequest.builder().productId("menu-1").quantity(2).build(),
                            OrderItemRequest.builder().productId("menu-2").quantity(3).build()
                    ))
                    .build();

            Order multiItemOrder = Order.builder()
                    .id("order-2")
                    .customer(customer)
                    .orderItems(List.of(
                            OrderItem.builder().productId("menu-1").name("Classic Burger").quantity(2).price(new BigDecimal("12.90")).build(),
                            OrderItem.builder().productId("menu-2").name("Fries").quantity(3).price(new BigDecimal("5.50")).build()
                    ))
                    .totalAmount(new BigDecimal("42.30"))
                    .status(OrderStatus.CREATED)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(menuServiceClient.getMenuItemById("menu-1")).thenReturn(menuItemResponse);
            when(menuServiceClient.getMenuItemById("menu-2")).thenReturn(secondItem);
            when(orderRepository.save(any(Order.class))).thenReturn(multiItemOrder);

            OrderResponse response = orderService.createOrder(request);

            assertThat(response.getOrderItems()).hasSize(2);
            assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("42.30"));

            verify(menuServiceClient).getMenuItemById("menu-1");
            verify(menuServiceClient).getMenuItemById("menu-2");
        }

        @Test
        @DisplayName("Should propagate exception when Menu Service fails")
        void shouldPropagateExceptionWhenMenuServiceFails() {
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .customer(customerRequest)
                    .orderItems(List.of(
                            OrderItemRequest.builder().productId("invalid-id").quantity(1).build()
                    ))
                    .build();

            when(menuServiceClient.getMenuItemById("invalid-id"))
                    .thenThrow(new RuntimeException("Menu Service unavailable"));

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Menu Service unavailable");

            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateOrderStatus")
    class UpdateOrderStatusTests {

        @Test
        @DisplayName("Should update order status successfully")
        void shouldUpdateOrderStatusSuccessfully() {
            Order existingOrder = Order.builder()
                    .id("order-1")
                    .customer(customer)
                    .orderItems(savedOrder.getOrderItems())
                    .totalAmount(savedOrder.getTotalAmount())
                    .status(OrderStatus.CREATED)
                    .createdAt(LocalDateTime.now())
                    .build();

            Order updatedOrder = Order.builder()
                    .id("order-1")
                    .customer(customer)
                    .orderItems(savedOrder.getOrderItems())
                    .totalAmount(savedOrder.getTotalAmount())
                    .status(OrderStatus.PREPARING)
                    .createdAt(existingOrder.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();

            UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                    .status(OrderStatus.PREPARING)
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Optional.of(existingOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);

            UpdateOrderStatusResponse response = orderService.updateOrderStatus("order-1", request);

            assertThat(response.getId()).isEqualTo("order-1");
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PREPARING);
            assertThat(response.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should publish notification to RabbitMQ when status is updated")
        void shouldPublishNotificationWhenStatusUpdated() {
            Order existingOrder = Order.builder()
                    .id("order-1")
                    .customer(customer)
                    .orderItems(savedOrder.getOrderItems())
                    .status(OrderStatus.CREATED)
                    .createdAt(LocalDateTime.now())
                    .build();

            Order updatedOrder = Order.builder()
                    .id("order-1")
                    .customer(customer)
                    .orderItems(savedOrder.getOrderItems())
                    .status(OrderStatus.DELIVERED)
                    .createdAt(existingOrder.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();

            UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                    .status(OrderStatus.DELIVERED)
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Optional.of(existingOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);

            orderService.updateOrderStatus("order-1", request);

            ArgumentCaptor<OrderStatusNotification> notificationCaptor =
                    ArgumentCaptor.forClass(OrderStatusNotification.class);
            verify(orderStatusPublisher).publishOrderStatusChange(notificationCaptor.capture());

            OrderStatusNotification notification = notificationCaptor.getValue();
            assertThat(notification.getOrderId()).isEqualTo("order-1");
            assertThat(notification.getFullName()).isEqualTo("John Doe");
            assertThat(notification.getEmail()).isEqualTo("john@email.com");
            assertThat(notification.getAddress()).isEqualTo("123 Main St");
            assertThat(notification.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("Should throw exception when order not found")
        void shouldThrowExceptionWhenOrderNotFound() {
            UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                    .status(OrderStatus.PREPARING)
                    .build();

            when(orderRepository.findById("non-existent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updateOrderStatus("non-existent", request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Order not found with id: non-existent");

            verify(orderRepository, never()).save(any());
            verify(orderStatusPublisher, never()).publishOrderStatusChange(any());
        }
    }

    @Nested
    @DisplayName("getOrderHistory")
    class GetOrderHistoryTests {

        @Test
        @DisplayName("Should return paginated order history")
        void shouldReturnPaginatedOrderHistory() {
            Order order1 = Order.builder()
                    .id("order-1")
                    .customer(customer)
                    .orderItems(savedOrder.getOrderItems())
                    .totalAmount(new BigDecimal("25.80"))
                    .status(OrderStatus.CREATED)
                    .createdAt(LocalDateTime.now())
                    .build();

            Order order2 = Order.builder()
                    .id("order-2")
                    .customer(customer)
                    .orderItems(savedOrder.getOrderItems())
                    .totalAmount(new BigDecimal("25.80"))
                    .status(OrderStatus.DELIVERED)
                    .createdAt(LocalDateTime.now().minusHours(1))
                    .build();

            Page<Order> page = new PageImpl<>(List.of(order1, order2), PageRequest.of(0, 10), 2);

            when(orderRepository.findAll(any(PageRequest.class))).thenReturn(page);

            OrderHistoryResponse response = orderService.getOrderHistory(10, 0);

            assertThat(response.getOrders()).hasSize(2);
            assertThat(response.getLimit()).isEqualTo(10);
            assertThat(response.getOffset()).isEqualTo(0);
            assertThat(response.getTotalRecords()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return empty list when no orders exist")
        void shouldReturnEmptyListWhenNoOrders() {
            Page<Order> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

            when(orderRepository.findAll(any(PageRequest.class))).thenReturn(emptyPage);

            OrderHistoryResponse response = orderService.getOrderHistory(10, 0);

            assertThat(response.getOrders()).isEmpty();
            assertThat(response.getTotalRecords()).isEqualTo(0);
        }

       
    }

    @Nested
    @DisplayName("getOrderById")
    class GetOrderByIdTests {

        @Test
        @DisplayName("Should return order when found")
        void shouldReturnOrderWhenFound() {
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(savedOrder));

            OrderResponse response = orderService.getOrderById("order-1");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("order-1");
            assertThat(response.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(response.getCustomer().getFullName()).isEqualTo("John Doe");
            assertThat(response.getOrderItems()).hasSize(1);
            assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("25.80"));
        }

        @Test
        @DisplayName("Should throw exception when order not found")
        void shouldThrowExceptionWhenOrderNotFound() {
            when(orderRepository.findById("non-existent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById("non-existent"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Order not found with id: non-existent");
        }
    }
}
