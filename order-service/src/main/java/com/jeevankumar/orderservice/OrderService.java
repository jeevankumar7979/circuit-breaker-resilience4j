package com.jeevankumar.orderservice;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private final InventoryClient inventoryClient;
    private final CircuitBreaker inventoryCircuitBreaker;
    private final PurchaseOrderRepository orderRepository;

    public OrderService(InventoryClient inventoryClient, CircuitBreaker inventoryCircuitBreaker, PurchaseOrderRepository orderRepository) {
        this.inventoryClient = inventoryClient;
        this.inventoryCircuitBreaker = inventoryCircuitBreaker;
        this.orderRepository = orderRepository;
    }

    public PurchaseOrder createOrder(OrderRequest request) {
        try{
            InventoryClient.InventoryResponse inventory = inventoryCircuitBreaker.executeSupplier(
                    () -> inventoryClient.getInventory(request.sku())
            );

            if (request.quantity() > inventory.availableQuantity()) {
                return orderRepository.save(new PurchaseOrder(
                        request.sku(),
                        request.quantity(),
                        "REJECTED",
                        inventory.availableQuantity(),
                        "INSUFFICIENT QUANTITY"

                ));
            }

            return orderRepository.save(new PurchaseOrder(
                    request.sku(),
                    request.quantity(),
                    "ACCEPTED",
                    inventory.availableQuantity(),
                    "Order accepted"
            ));

        }catch (CallNotPermittedException | RestClientException exception) {
            return orderRepository.save(new PurchaseOrder(
                    request.sku(),
                    request.quantity(),
                    "INVENTORY_UNAVAILABLE",
                    null,
                    "Inventory Service Unavailable: fallback (" + exception.getClass().getSimpleName() + ")"
            ));
        }
    }

    public PurchaseOrder getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found with orderId: " + orderId
                ));
    }
}
