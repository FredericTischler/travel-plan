package com.travelplan.payment.dto;

import com.travelplan.payment.entity.Payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API response for a payment resource.
 *
 * Intentionally omits {@code deleted_at}: that field is an internal
 * soft-delete implementation detail and must never be exposed over the API.
 */
public class PaymentResponse {

    private final UUID id;
    private final BigDecimal amount;
    private final String currency;
    private final String status;
    private final String externalReference;
    private final OffsetDateTime createdAt;

    private PaymentResponse(UUID id, BigDecimal amount, String currency, String status,
                             String externalReference, OffsetDateTime createdAt) {
        this.id = id;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.externalReference = externalReference;
        this.createdAt = createdAt;
    }

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getExternalReference(),
                payment.getCreatedAt());
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}