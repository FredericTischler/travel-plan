package com.travelplan.payment.exception;

import java.util.UUID;

/**
 * Thrown when a payment is not found (absent or soft-deleted).
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID id) {
        super("Payment not found: " + id);
    }
}