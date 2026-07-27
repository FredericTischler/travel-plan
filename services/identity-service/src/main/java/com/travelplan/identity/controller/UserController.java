package com.travelplan.identity.controller;

import com.travelplan.identity.dto.CreateUserRequest;
import com.travelplan.identity.dto.UserResponse;
import com.travelplan.identity.service.AuthService;
import com.travelplan.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the user resource.
 *
 * No business logic here — data decisions are delegated to {@link UserService},
 * Bearer token validation to {@link AuthService} (the exact same manual
 * mechanism {@code GET /me} already uses — see {@link AuthController}, there
 * is no Spring Security filter chain in this codebase). Exception-to-HTTP
 * mapping is handled by {@link com.travelplan.identity.exception.GlobalExceptionHandler}.
 *
 * {@code POST /users} stays public: it is the only way to create the very
 * first account, so requiring a token here would be a chicken-and-egg
 * problem. {@code GET /users}, {@code GET /users/{id}} and
 * {@code DELETE /users/{id}} all require a valid Bearer token: the list and
 * the detail endpoint expose the same PII (email), and delete is a
 * destructive operation, so leaving either open while protecting the list
 * would just relocate the same vulnerability rather than close it.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    /**
     * Create a new user. Public — see class-level note.
     *
     * @return 201 Created with the created user, 409 if email is already active,
     *         400 if the request body fails validation
     */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get an active user by id. Requires a valid Bearer token — see class-level note.
     *
     * @return 200 with the user, 404 if absent or soft-deleted, 401 with a
     *         generic message if the Authorization header is missing/invalid/expired
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(
            @PathVariable UUID id,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        authService.getCurrentUser(authorizationHeader);
        return ResponseEntity.ok(userService.findById(id));
    }

    /**
     * List all active users. Requires a valid Bearer token — see class-level note.
     *
     * @return 200 with the list (empty list if none), 401 with a generic
     *         message if the Authorization header is missing/invalid/expired
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAll(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        authService.getCurrentUser(authorizationHeader);
        return ResponseEntity.ok(userService.findAll());
    }

    /**
     * Soft-delete an active user. Requires a valid Bearer token — see class-level note.
     *
     * @return 204 No Content on success, 404 if absent or already soft-deleted,
     *         401 with a generic message if the Authorization header is
     *         missing/invalid/expired
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        authService.getCurrentUser(authorizationHeader);
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}