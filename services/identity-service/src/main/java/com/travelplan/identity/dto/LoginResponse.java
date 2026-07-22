package com.travelplan.identity.dto;

import java.util.UUID;

/**
 * API response for a successful {@code POST /login}.
 *
 * Intentionally minimal: no JWT, no session token, no role — just enough to
 * confirm which identity was authenticated. Never includes the password hash.
 */
public class LoginResponse {

    private final UUID id;
    private final String email;

    public LoginResponse(UUID id, String email) {
        this.id = id;
        this.email = email;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
}