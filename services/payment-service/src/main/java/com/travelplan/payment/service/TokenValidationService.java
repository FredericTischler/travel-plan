package com.travelplan.payment.service;

import com.travelplan.payment.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
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
 * valid", not who the caller is — except for the one narrow case below,
 * where the subject IS read to scope a service-to-service token to a single
 * endpoint.
 */
@Service
public class TokenValidationService {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Subject identity-service mints for the one service-to-service token it
     * issues (see identity-service's {@code JwtService.generateServiceToken()}).
     * This subject is only ever accepted on {@code DELETE /payments/by-user/{userId}};
     * every other endpoint explicitly rejects it via {@link #requireValidToken}.
     */
    private static final String SERVICE_IDENTITY_SUBJECT = "service:identity";

    private final JwtService jwtService;

    public TokenValidationService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Reject the request unless the {@code Authorization} header carries a
     * Bearer token that identity-service signed, that has not expired, and
     * that is NOT the {@code service:identity} service-to-service token —
     * that token is scoped exclusively to
     * {@code DELETE /payments/by-user/{userId}} (see
     * {@link #requireUserOrServiceToken}) and must not grant access anywhere
     * else.
     *
     * @param authorizationHeader raw header value, may be {@code null}
     * @throws InvalidTokenException if the header is absent, not a
     *         {@code Bearer} value, the token fails signature/expiration
     *         validation, or the token is the service-to-service token
     */
    public void requireValidToken(String authorizationHeader) {
        Claims claims = validateAndParse(authorizationHeader);
        if (SERVICE_IDENTITY_SUBJECT.equals(claims.getSubject())) {
            throw new InvalidTokenException();
        }
    }

    /**
     * Reject the request unless the {@code Authorization} header carries a
     * Bearer token that identity-service signed and that has not expired —
     * no subject restriction, so this accepts either a normal user token or
     * the {@code service:identity} service-to-service token. Reserved for
     * {@code DELETE /payments/by-user/{userId}}, the sole endpoint
     * identity-service's cascade delete is allowed to call.
     *
     * @param authorizationHeader raw header value, may be {@code null}
     * @throws InvalidTokenException if the header is absent, not a
     *         {@code Bearer} value, or the token fails signature/expiration
     *         validation
     */
    public void requireUserOrServiceToken(String authorizationHeader) {
        validateAndParse(authorizationHeader);
    }

    private Claims validateAndParse(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new InvalidTokenException();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        try {
            return jwtService.validate(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException();
        }
    }
}