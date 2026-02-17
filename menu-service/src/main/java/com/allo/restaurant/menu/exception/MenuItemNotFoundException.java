package com.allo.restaurant.menu.exception;

public class MenuItemNotFoundException extends RuntimeException {
    public MenuItemNotFoundException(String id) {
        super("Menu item not found with id: " + id);
    }
}
