package com.jeevankumar.inventoryservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/{sku}")
    public InventoryResponse getInventory(@PathVariable String sku) {
        InventoryItem item = service.getBySku(sku);

        return new InventoryResponse(
                item.getSku(),
                item.getAvailableQuantity()
        );
    }

    record InventoryResponse(String sku, Integer availableQuantity) {}

}
