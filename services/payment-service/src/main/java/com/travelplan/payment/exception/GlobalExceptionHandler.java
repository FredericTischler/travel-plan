package com.travelplan.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception-to-HTTP mapping.
 *
 * All error bodies follow the same structure:
 * <pre>
 * { "error": "...", "status": N }
 * </pre>
 *
 * No business logic lives here — only HTTP status assignment and body shaping.
 *
 * This is a generic skeleton: at this increment there is no controller and no
 * payment-specific exception yet, so only the framework-level validation
 * exception is handled. Business exceptions (e.g. payment not found) are
 * expected to be added here as they are introduced, following the same
 * pattern as identity-service's handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed — " + detail);
    }

    private static ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("status", status.value());
        return ResponseEntity.status(status).body(body);
    }
}