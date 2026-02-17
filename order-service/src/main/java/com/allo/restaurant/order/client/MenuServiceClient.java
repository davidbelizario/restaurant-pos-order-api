package com.allo.restaurant.order.client;

import com.allo.restaurant.order.dto.MenuItemResponse;
import com.allo.restaurant.order.exception.MenuItemNotFoundException;
import com.allo.restaurant.order.exception.MenuServiceUnavailableException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class MenuServiceClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${menu.service.url}")
    private String menuServiceUrl;

    @CircuitBreaker(name = "menuService", fallbackMethod = "getMenuItemFallback")
    @Retry(name = "menuService")
    public MenuItemResponse getMenuItemById(String id) {
        log.info("Attempting to fetch menu item with id: {}", id);

        RestClient restClient = restClientBuilder.baseUrl(menuServiceUrl).build();

        try {
            return restClient
                    .get()
                    .uri("/menu-items/{id}", id)
                    .retrieve()
                    .body(MenuItemResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new MenuItemNotFoundException(id);
        }
    }

    private MenuItemResponse getMenuItemFallback(String id, Throwable t) {
        if (t instanceof MenuItemNotFoundException) {
            throw (MenuItemNotFoundException) t;
        }
        log.error("Circuit breaker activated for Menu Service. Error: {}", t.getMessage());
        throw new MenuServiceUnavailableException("Menu Service is currently unavailable. Please try again later.");
    }
}
