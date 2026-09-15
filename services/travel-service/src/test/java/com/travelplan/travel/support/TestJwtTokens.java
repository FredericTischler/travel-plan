package com.travelplan.travel.support;

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
 * Mirrors identity-service's own {@code JwtService} (HS256, subject = user
 * id, short expiration) closely enough to exercise travel-service's
 * JwtService signature/expiration checks end-to-end, without needing
 * identity-service running anywhere in these tests. Copied from
 * payment-service's {@code TestJwtTokens} — same pattern, no
 * {@code serviceToken()} here since travel-service has no service-to-service
 * caller to impersonate.
 */
public final class TestJwtTokens {

    /** >= 32 bytes once UTF-8 encoded, as required by jjwt/HS256 (RFC 7518 §3.2). */
    public static final String SIGNING_KEY = "test-only-jwt-signing-key-for-travel-service-tests-0123456789";

    private TestJwtTokens() {
    }

    /**
     * A freshly-signed, currently-valid token (15 min validity, arbitrary
     * random subject — travel-service's JwtService never reads the subject,
     * only signature and expiration).
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
