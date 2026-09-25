package com.order.api.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

/**
 * How long a checked-out order holds its stock while it waits to be paid, and the
 * scheduling that expires it once that time is up.
 */
@Configuration
@EnableScheduling
public class CheckoutConfig {

    private final Duration paymentTimeout;

    public CheckoutConfig(@Value("${checkout.payment-timeout}") Duration paymentTimeout) {
        this.paymentTimeout = paymentTimeout;
    }

    @Bean
    public CheckoutProperties checkoutProperties() {
        return new CheckoutProperties(paymentTimeout);
    }

    /**
     * @param paymentTimeout how long after checkout a pending order may still be paid;
     *                       past it the order no longer holds stock
     */
    public record CheckoutProperties(Duration paymentTimeout) {
    }
}
