package com.allo.restaurant.order.exception;

public class MenuItemNotFoundException extends RuntimeException {
    public MenuItemNotFoundException(String id) {
        super("Menu Item not found with id: " + id);
    }
}
