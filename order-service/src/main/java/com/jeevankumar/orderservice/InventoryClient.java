package com.jeevankumar.orderservice;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryClient {
    private final RestClient restClient;

    public InventoryClient(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }


    public InventoryResponse getInventory(String sku) {
        return restClient.get()
                .uri("/api/v1/inventory/{sku}", sku)
                .retrieve()
                .body(InventoryResponse.class);
    }

    public record InventoryResponse(String sku, Integer availableQuantity) {}


}
