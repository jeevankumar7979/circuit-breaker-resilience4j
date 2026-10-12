package com.jeevankumar.orderservice;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Configuration
public class InventoryClientConfig {

    @Bean
    RestClient inventoryRestClient(@Value("${inventory.base-url}") String inventoryBaseUrl) {
        return RestClient.builder()
                .baseUrl(inventoryBaseUrl)
                .build();
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(exception -> exception instanceof RestClientException)
                .build();

        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    CircuitBreaker inventoryCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("inventory");
    }

    @Bean
    RateLimiter orderCreationRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(5)
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .timeoutDuration(Duration.ZERO)
                .build();
        return RateLimiterRegistry.of(config).rateLimiter("order-created");
    }
}
