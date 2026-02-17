package com.allo.restaurant.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemListResponse {
    private List<MenuItemResponse> items;
    private long totalRecords;
}
