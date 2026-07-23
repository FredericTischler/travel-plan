package com.travelplan.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * No Spring Security dependency is on the classpath at this stage (payment-service
 * does not emit or validate any authentication token in this increment), so no
 * autoconfiguration exclusion is required here — unlike identity-service.
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}