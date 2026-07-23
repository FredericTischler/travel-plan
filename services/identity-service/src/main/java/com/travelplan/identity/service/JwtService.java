package com.travelplan.identity.service;

import com.travelplan.identity.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Generates and validates JWTs for {@code POST /login} and {@code GET /me}.
 *
 * <p><b>Algorithm choice — HS256 (symmetric), not RS256:</b> RS256 (asymmetric)
 * would only be justified if another service ever needed to verify identity
 * JWTs without holding the signing secret itself (i.e. verify-only, no
 * issuance). Today identity-service is the sole issuer AND sole verifier of
 * its own tokens, so a shared symmetric secret is simpler and sufficient.
 * Reconsider RS256 if payment-service or travel-service ever need to
 * validate identity JWTs directly (they would then hold only the public key,
 * never the signing secret).</p>
 *
 * <p><b>Assumed debt — JWT_SIGNING_KEY is a plain environment variable, not
 * Vault-backed.</b> Unlike DB_* credentials (injected by Docker Compose from
 * Vault, see application.yml), this secret is read directly from the process
 * environment with no Vault client involved (this service never talks to
 * Vault directly — consistent with the "Ansible reads from Vault and renders"
 * model already used elsewhere). This mirrors the Neo4j Vault wiring deferred
 * in Phase 1: an explicit, assumed gap, not a silent shortcut.</p>
 *
 * <p>No refresh token, no revocation/blacklist, no roles/permissions in the
 * token — out of scope for this increment.</p>
 */
@Service
public class JwtService {

    private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(15);
    private static final String CLAIM_EMAIL = "email";

    @Value("${jwt.signing-key}")
    private String signingKeySecret;

    private SecretKey signingKey;

    /**
     * Builds the HMAC key once at startup. jjwt enforces RFC 7518 §3.2 for
     * HS256: the key must be >= 256 bits (32 bytes) once UTF-8 encoded, or
     * {@link io.jsonwebtoken.security.WeakKeyException} aborts startup here —
     * fail-fast, not a silent weak default.
     */
    @PostConstruct
    void buildSigningKey() {
        byte[] keyBytes = signingKeySecret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Issue a short-lived (15 min) token for a successfully authenticated user.
     * Subject = user id, single custom claim = email. No refresh token.
     */
    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_VALIDITY)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verify signature and expiration and return the claims.
     *
     * @throws JwtException if the token is malformed, expired, or the
     *         signature does not match — callers must map every subtype to
     *         the same generic 401 (no distinction leaked to the client)
     * @throws IllegalArgumentException if {@code token} is null/blank
     */
    public Claims validate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}