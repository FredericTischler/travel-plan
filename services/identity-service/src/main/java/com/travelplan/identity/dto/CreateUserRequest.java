package com.travelplan.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /users}.
 *
 * Validated by {@code @Valid} in the controller. Constraint violations are
 * handled by {@link com.travelplan.identity.exception.GlobalExceptionHandler}
 * and returned as HTTP 400.
 */
public class CreateUserRequest {

    @NotBlank(message = "must not be blank")
    @Email(message = "must be a valid email address")
    private String email;

    public CreateUserRequest() {
        // required for Jackson deserialization
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}