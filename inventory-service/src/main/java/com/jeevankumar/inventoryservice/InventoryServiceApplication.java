package com.jeevankumar.inventoryservice;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
         SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner addData(InventoryRepository repository) {
        return args -> {
            addIfMissing(repository, "Book", 25);
            addIfMissing(repository, "Book", 25);
            addIfMissing(repository, "Book", 25);

        };
    }

    private void addIfMissing(InventoryRepository repository, String sku, int quantity) {
        if (repository.findBySku(sku).isEmpty()) {
            repository.saveAll(List.of(new InventoryItem(sku, quantity)) );
        }
    }

}
