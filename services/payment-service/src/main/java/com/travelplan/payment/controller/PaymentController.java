package com.travelplan.payment.controller;

import com.travelplan.payment.dto.CreateManualPaymentRequest;
import com.travelplan.payment.dto.PaymentResponse;
import com.travelplan.payment.dto.UpdateStatusRequest;
import com.travelplan.payment.service.PaymentService;
import com.travelplan.payment.service.TokenValidationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the payment resource.
 *
 * No business logic here — all decisions are delegated to {@link PaymentService}.
 * Bearer token validation is delegated to {@link TokenValidationService} (the
 * exact same manual mechanism identity-service uses — no Spring Security
 * filter chain in this codebase). Exception-to-HTTP mapping is handled by
 * {@link com.travelplan.payment.exception.GlobalExceptionHandler}.
 *
 * Every endpoint requires a valid Bearer token issued by identity-service:
 * this service holds sensitive payment records, and there is no anonymous
 * use case for any of create/read/update/delete here — unlike identity's
 * {@code POST /users}, there is no chicken-and-egg reason to leave any of
 * these open.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final TokenValidationService tokenValidationService;

    public PaymentController(PaymentService paymentService, TokenValidationService tokenValidationService) {
        this.paymentService = paymentService;
        this.tokenValidationService = tokenValidationService;
    }

    /**
     * Create a new manual payment (always starts as PENDING). Requires a
     * valid Bearer token — see class-level note.
     *
     * @return 201 Created with the created payment, 400 if the request body fails validation,
     *         401 with a generic message if the Authorization header is missing/invalid/expired
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody CreateManualPaymentRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        tokenValidationService.requireValidToken(authorizationHeader);
        PaymentResponse created = paymentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get an active payment by id. Requires a valid Bearer token — see class-level note.
     *
     * @return 200 with the payment, 404 if absent or soft-deleted,
     *         401 with a generic message if the Authorization header is missing/invalid/expired
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getById(
            @PathVariable UUID id,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        tokenValidationService.requireValidToken(authorizationHeader);
        return ResponseEntity.ok(paymentService.findById(id));
    }

    /**
     * List all active payments. Requires a valid Bearer token — see class-level note.
     *
     * @return 200 with the list (empty list if none),
     *         401 with a generic message if the Authorization header is missing/invalid/expired
     */
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAll(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        tokenValidationService.requireValidToken(authorizationHeader);
        return ResponseEntity.ok(paymentService.findAll());
    }

    /**
     * Transition a payment's status to COMPLETED or FAILED. Requires a valid
     * Bearer token — see class-level note.
     *
     * @return 200 with the updated payment, 400 if the target value is invalid,
     *         409 if the payment is already in a terminal status, 404 if absent or soft-deleted,
     *         401 with a generic message if the Authorization header is missing/invalid/expired
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<PaymentResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        tokenValidationService.requireValidToken(authorizationHeader);
        return ResponseEntity.ok(paymentService.updateStatus(id, request));
    }

    /**
     * Soft-delete an active payment. Requires a valid Bearer token — see class-level note.
     *
     * @return 204 No Content on success, 404 if absent or already soft-deleted,
     *         401 with a generic message if the Authorization header is missing/invalid/expired
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        tokenValidationService.requireValidToken(authorizationHeader);
        paymentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}