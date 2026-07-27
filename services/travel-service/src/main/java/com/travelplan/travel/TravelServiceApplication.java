package com.travelplan.travel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * No Spring Security dependency is on the classpath at this stage (no
 * authentication/authorization concern in this increment), so no
 * autoconfiguration exclusion is required here — same posture as
 * payment-service.
 */
@SpringBootApplication
public class TravelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelServiceApplication.class, args);
    }
}