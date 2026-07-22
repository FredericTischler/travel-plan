package com.travelplan.identity.controller;

import com.travelplan.identity.dto.LoginRequest;
import com.travelplan.identity.dto.LoginResponse;
import com.travelplan.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication.
 *
 * No business logic here — credential verification is delegated to
 * {@link AuthService}. Exception-to-HTTP mapping is handled by
 * {@link com.travelplan.identity.exception.GlobalExceptionHandler}.
 * No JWT, no session, no token: a successful call only confirms the
 * credentials match an active user.
 */
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Verify email + password for an active user.
     *
     * @return 200 with minimal identity (id, email) on success, 401 with a
     *         generic message on any failure (unknown email or wrong
     *         password produce the exact same response)
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}