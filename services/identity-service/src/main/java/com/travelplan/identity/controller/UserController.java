package com.travelplan.identity.controller;

import com.travelplan.identity.dto.CreateUserRequest;
import com.travelplan.identity.dto.UserResponse;
import com.travelplan.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the user resource.
 *
 * No business logic here — all decisions are delegated to {@link UserService}.
 * Exception-to-HTTP mapping is handled by
 * {@link com.travelplan.identity.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Create a new user.
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
     * Get an active user by id.
     *
     * @return 200 with the user, 404 if absent or soft-deleted
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    /**
     * List all active users.
     *
     * @return 200 with the list (empty list if none)
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    /**
     * Soft-delete an active user.
     *
     * @return 204 No Content on success, 404 if absent or already soft-deleted
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}