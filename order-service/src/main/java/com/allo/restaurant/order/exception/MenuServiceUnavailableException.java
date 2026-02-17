package com.allo.restaurant.order.exception;

public class MenuServiceUnavailableException extends RuntimeException {
    public MenuServiceUnavailableException(String message) {
        super(message);
    }
}
