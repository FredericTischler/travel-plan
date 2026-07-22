package com.travelplan.identity.service;

import com.travelplan.identity.dto.LoginRequest;
import com.travelplan.identity.dto.LoginResponse;
import com.travelplan.identity.entity.User;
import com.travelplan.identity.exception.InvalidCredentialsException;
import com.travelplan.identity.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication logic for {@code POST /login}.
 *
 * No JWT, no session, no token: a successful call only confirms that the
 * given credentials match an active (non-deleted) user. Unknown email and
 * wrong password are deliberately indistinguishable to the caller — same
 * HTTP status, same message.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    // Fixed dummy password, hashed once at construction time (i.e. once per
    // application startup, not per request). Used to run a BCrypt comparison
    // even when the email is unknown, so an unknown-email response takes the
    // same time as a wrong-password response — this prevents account
    // enumeration via response-time measurement.
    private final String dummyHash;

    public AuthService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-safety");
    }

    /**
     * Verify email + password against active users.
     *
     * @throws InvalidCredentialsException if the email is unknown/soft-deleted
     *         or the password does not match — identical exception in both cases
     */
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail()).orElse(null);
        String hashToCheck = (user != null) ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.getPassword(), hashToCheck);

        if (user == null || !matches) {
            throw new InvalidCredentialsException();
        }

        return new LoginResponse(user.getId(), user.getEmail());
    }
}