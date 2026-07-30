package com.travelplan.payment.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Test-only JWT helper shared by integration tests.
 *
 * Signs tokens with {@link #SIGNING_KEY}, the same secret every test class
 * registers as {@code JWT_SIGNING_KEY} via {@code @DynamicPropertySource}.
 * This mirrors identity-service's own {@code JwtService} (HS256, subject =
 * user id, short expiration) closely enough to exercise
 * {@code TokenValidationService}'s real signature + expiration checks
 * end-to-end, without needing identity-service running anywhere in these
 * tests.
 */
public final class TestJwtTokens {

    /** >= 32 bytes once UTF-8 encoded, as required by jjwt/HS256 (RFC 7518 §3.2). */
    public static final String SIGNING_KEY = "test-only-jwt-signing-key-for-payment-tests-0123456789ABCDEF";

    private TestJwtTokens() {
    }

    /**
     * A freshly-signed, currently-valid token (15 min validity, arbitrary
     * random subject — payment-service's TokenValidationService never reads
     * the subject, only signature and expiration).
     */
    public static String validToken() {
        SecretKey key = Keys.hmacShaKeyFor(SIGNING_KEY.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(15))))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}