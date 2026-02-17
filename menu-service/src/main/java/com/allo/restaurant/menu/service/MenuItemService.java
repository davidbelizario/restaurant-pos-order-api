package com.allo.restaurant.menu.service;

import com.allo.restaurant.menu.dto.*;
import com.allo.restaurant.menu.entity.MenuItem;
import com.allo.restaurant.menu.exception.MenuItemNotFoundException;
import com.allo.restaurant.menu.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;

    public MenuItemResponse createMenuItem(CreateMenuItemRequest request) {
        MenuItem menuItem = MenuItem.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .createdAt(LocalDateTime.now())
                .build();

        MenuItem savedMenuItem = menuItemRepository.save(menuItem);
        return mapToResponse(savedMenuItem);
    }

    public MenuItemResponse updateMenuItem(String id, UpdateMenuItemRequest request) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new MenuItemNotFoundException(id));

        menuItem.setName(request.getName());
        menuItem.setDescription(request.getDescription());
        menuItem.setPrice(request.getPrice());
        menuItem.setUpdatedAt(LocalDateTime.now());

        MenuItem updatedMenuItem = menuItemRepository.save(menuItem);
        return mapToResponse(updatedMenuItem);
    }

    public DeleteMenuItemResponse deleteMenuItem(String id) {
        if (!menuItemRepository.existsById(id)) {
            throw new MenuItemNotFoundException(id);
        }

        menuItemRepository.deleteById(id);
        return DeleteMenuItemResponse.builder()
                .message("Menu item deleted successfully")
                .id(id)
                .build();
    }

    public MenuItemListResponse getAllMenuItems(int limit, int offset) {
        int pageNumber = offset / limit;
        int offsetInPage = offset % limit;
        
        PageRequest pageRequest = PageRequest.of(pageNumber, limit);
        Page<MenuItem> page = menuItemRepository.findAll(pageRequest);
        
        List<MenuItem> allItems = new java.util.ArrayList<>(page.getContent());
        
        if (offsetInPage > 0 && page.hasNext()) {
            Page<MenuItem> nextPage = menuItemRepository.findAll(PageRequest.of(pageNumber + 1, limit));
            allItems.addAll(nextPage.getContent());
        }
        
        List<MenuItemResponse> items = allItems.stream()
                .skip(offsetInPage)
                .limit(limit)
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return MenuItemListResponse.builder()
                .items(items)
                .totalRecords(page.getTotalElements())
                .build();
    }

    public MenuItemResponse getMenuItemById(String id) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new MenuItemNotFoundException(id));
        return mapToResponse(menuItem);
    }

    private MenuItemResponse mapToResponse(MenuItem menuItem) {
        return MenuItemResponse.builder()
                .id(menuItem.getId())
                .name(menuItem.getName())
                .description(menuItem.getDescription())
                .price(menuItem.getPrice())
                .createdAt(menuItem.getCreatedAt())
                .updatedAt(menuItem.getUpdatedAt())
                .build();
    }
}
