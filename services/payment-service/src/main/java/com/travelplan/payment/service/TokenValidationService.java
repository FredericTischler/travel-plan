package com.travelplan.payment.service;

import com.travelplan.payment.exception.InvalidTokenException;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Service;

/**
 * Bearer token validation for {@link com.travelplan.payment.controller.PaymentController}.
 *
 * Mirrors the manual validation mechanism used by identity-service's own
 * {@code AuthService} (no Spring Security filter chain in this codebase
 * either), minus the final step: identity-service additionally looks up the
 * token's subject against its own user table, but payment-service has no
 * access to identity-service's database, so it stops at signature +
 * expiration validation. That is sufficient here — this service only needs
 * to know "is this a token identity-service really issued and is it still
 * valid", not who the caller is.
 */
@Service
public class TokenValidationService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public TokenValidationService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Reject the request unless the {@code Authorization} header carries a
     * Bearer token that identity-service signed and that has not expired.
     *
     * @param authorizationHeader raw header value, may be {@code null}
     * @throws InvalidTokenException if the header is absent, not a
     *         {@code Bearer} value, or the token fails signature/expiration
     *         validation
     */
    public void requireValidToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new InvalidTokenException();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        try {
            jwtService.validate(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException();
        }
    }
}