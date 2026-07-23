package com.travelplan.payment.controller;

import com.travelplan.payment.dto.CreateManualPaymentRequest;
import com.travelplan.payment.dto.PaymentResponse;
import com.travelplan.payment.dto.UpdateStatusRequest;
import com.travelplan.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the payment resource.
 *
 * No business logic here — all decisions are delegated to {@link PaymentService}.
 * Exception-to-HTTP mapping is handled by
 * {@link com.travelplan.payment.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Create a new manual payment (always starts as PENDING).
     *
     * @return 201 Created with the created payment, 400 if the request body fails validation
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreateManualPaymentRequest request) {
        PaymentResponse created = paymentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get an active payment by id.
     *
     * @return 200 with the payment, 404 if absent or soft-deleted
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.findById(id));
    }

    /**
     * List all active payments.
     *
     * @return 200 with the list (empty list if none)
     */
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAll() {
        return ResponseEntity.ok(paymentService.findAll());
    }

    /**
     * Transition a payment's status to COMPLETED or FAILED.
     *
     * @return 200 with the updated payment, 400 if the target value is invalid,
     *         409 if the payment is already in a terminal status, 404 if absent or soft-deleted
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<PaymentResponse> updateStatus(@PathVariable UUID id,
                                                          @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(paymentService.updateStatus(id, request));
    }

    /**
     * Soft-delete an active payment.
     *
     * @return 204 No Content on success, 404 if absent or already soft-deleted
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        paymentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}