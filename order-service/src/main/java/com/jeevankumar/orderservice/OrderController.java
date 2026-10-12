package com.jeevankumar.orderservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public PurchaseOrder placeOrder(@RequestBody OrderRequest request) {
        return orderService.createOrder(request);
    }
    
    @GetMapping("/{orderId}")
    public PurchaseOrder getOrder(@PathVariable Long orderId) {
        return orderService.getOrderById(orderId);
    }
}
