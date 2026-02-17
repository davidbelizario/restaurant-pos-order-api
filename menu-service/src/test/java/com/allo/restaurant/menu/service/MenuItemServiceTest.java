package com.allo.restaurant.menu.service;

import com.allo.restaurant.menu.dto.*;
import com.allo.restaurant.menu.entity.MenuItem;
import com.allo.restaurant.menu.exception.MenuItemNotFoundException;
import com.allo.restaurant.menu.repository.MenuItemRepository;
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
class MenuItemServiceTest {

    @Mock
    private MenuItemRepository menuItemRepository;

    @InjectMocks
    private MenuItemService menuItemService;

    private MenuItem savedMenuItem;

    @BeforeEach
    void setUp() {
        savedMenuItem = MenuItem.builder()
                .id("item-1")
                .name("Pizza")
                .description("Delicious cheese pizza")
                .price(new BigDecimal("12.90"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("createMenuItem")
    class CreateMenuItemTests {

        @Test
        @DisplayName("Should create menu item successfully")
        void shouldCreateMenuItemSuccessfully() {
            CreateMenuItemRequest request = CreateMenuItemRequest.builder()
                    .name("Pizza")
                    .description("Delicious cheese pizza")
                    .price(new BigDecimal("12.90"))
                    .build();

            when(menuItemRepository.save(any(MenuItem.class))).thenReturn(savedMenuItem);

            MenuItemResponse response = menuItemService.createMenuItem(request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("item-1");
            assertThat(response.getName()).isEqualTo("Pizza");
            assertThat(response.getDescription()).isEqualTo("Delicious cheese pizza");
            assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("12.90"));
            assertThat(response.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("updateMenuItem")
    class UpdateMenuItemTests {

        @Test
        @DisplayName("Should update menu item successfully")
        void shouldUpdateMenuItemSuccessfully() {
            UpdateMenuItemRequest request = UpdateMenuItemRequest.builder()
                    .name("Burguer")
                    .description("Updated Burguer")
                    .price(new BigDecimal("15.90"))
                    .build();

            MenuItem updatedMenuItem = MenuItem.builder()
                    .id("item-1")
                    .name("Burguer")
                    .description("Updated Burguer")
                    .price(new BigDecimal("15.90"))
                    .createdAt(savedMenuItem.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(menuItemRepository.findById("item-1")).thenReturn(Optional.of(savedMenuItem));
            when(menuItemRepository.save(any(MenuItem.class))).thenReturn(updatedMenuItem);

            MenuItemResponse response = menuItemService.updateMenuItem("item-1", request);

            assertThat(response.getId()).isEqualTo("item-1");
            assertThat(response.getName()).isEqualTo("Burguer");
            assertThat(response.getDescription()).isEqualTo("Updated Burguer");
            assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("15.90"));
            assertThat(response.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should throw MenuItemNotFoundException when item not found")
        void shouldThrowWhenItemNotFound() {
            UpdateMenuItemRequest request = UpdateMenuItemRequest.builder()
                    .name("Updated")
                    .description("Updated desc")
                    .price(new BigDecimal("10.00"))
                    .build();

            when(menuItemRepository.findById("non-existent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> menuItemService.updateMenuItem("non-existent", request))
                    .isInstanceOf(MenuItemNotFoundException.class)
                    .hasMessageContaining("non-existent");

            verify(menuItemRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteMenuItem")
    class DeleteMenuItemTests {

        @Test
        @DisplayName("Should delete menu item successfully")
        void shouldDeleteMenuItemSuccessfully() {
            when(menuItemRepository.existsById("item-1")).thenReturn(true);

            DeleteMenuItemResponse response = menuItemService.deleteMenuItem("item-1");

            assertThat(response.getId()).isEqualTo("item-1");
            assertThat(response.getMessage()).isEqualTo("Menu item deleted successfully");
            verify(menuItemRepository).deleteById("item-1");
        }

        @Test
        @DisplayName("Should throw MenuItemNotFoundException when item not found")
        void shouldThrowWhenItemNotFound() {
            when(menuItemRepository.existsById("non-existent")).thenReturn(false);

            assertThatThrownBy(() -> menuItemService.deleteMenuItem("non-existent"))
                    .isInstanceOf(MenuItemNotFoundException.class)
                    .hasMessageContaining("non-existent");

            verify(menuItemRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("getAllMenuItems")
    class GetAllMenuItemsTests {

        @Test
        @DisplayName("Should return paginated menu items")
        void shouldReturnPaginatedMenuItems() {
            MenuItem item1 = MenuItem.builder()
                    .id("item-1").name("Burger").description("Burger desc")
                    .price(new BigDecimal("12.90")).createdAt(LocalDateTime.now()).build();

            MenuItem item2 = MenuItem.builder()
                    .id("item-2").name("Fries").description("Fries desc")
                    .price(new BigDecimal("5.50")).createdAt(LocalDateTime.now()).build();

            Page<MenuItem> page = new PageImpl<>(List.of(item1, item2), PageRequest.of(0, 10), 2);

            when(menuItemRepository.findAll(any(PageRequest.class))).thenReturn(page);

            MenuItemListResponse response = menuItemService.getAllMenuItems(10, 0);

            assertThat(response.getItems()).hasSize(2);
            assertThat(response.getTotalRecords()).isEqualTo(2);
            assertThat(response.getItems().get(0).getName()).isEqualTo("Burger");
            assertThat(response.getItems().get(1).getName()).isEqualTo("Fries");
        }

        @Test
        @DisplayName("Should return empty list when no items exist")
        void shouldReturnEmptyListWhenNoItems() {
            Page<MenuItem> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

            when(menuItemRepository.findAll(any(PageRequest.class))).thenReturn(emptyPage);

            MenuItemListResponse response = menuItemService.getAllMenuItems(10, 0);

            assertThat(response.getItems()).isEmpty();
            assertThat(response.getTotalRecords()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getMenuItemById")
    class GetMenuItemByIdTests {

        @Test
        @DisplayName("Should return menu item when found")
        void shouldReturnMenuItemWhenFound() {
            when(menuItemRepository.findById("item-1")).thenReturn(Optional.of(savedMenuItem));

            MenuItemResponse response = menuItemService.getMenuItemById("item-1");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("item-1");
            assertThat(response.getName()).isEqualTo("Pizza");
            assertThat(response.getDescription()).isEqualTo("Delicious cheese pizza");
            assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("12.90"));
        }

        @Test
        @DisplayName("Should throw MenuItemNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(menuItemRepository.findById("non-existent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> menuItemService.getMenuItemById("non-existent"))
                    .isInstanceOf(MenuItemNotFoundException.class)
                    .hasMessageContaining("non-existent");
        }
    }
}
