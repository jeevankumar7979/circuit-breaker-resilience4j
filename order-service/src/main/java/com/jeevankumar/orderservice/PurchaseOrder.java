package com.jeevankumar.orderservice;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private String status;

    private Integer availableQuantity;

    @Column(nullable = false)
    private String message;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(
            String sku,
            int quantity,
            String status,
            Integer availableQuantity,
            String message
    ) {
        this.sku = sku;
        this.quantity = quantity;
        this.status = status;
        this.availableQuantity = availableQuantity;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getStatus() {
        return status;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public String getMessage() {
        return message;
    }
}
