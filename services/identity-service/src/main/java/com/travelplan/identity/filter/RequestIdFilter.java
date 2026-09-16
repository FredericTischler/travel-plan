package com.travelplan.identity.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Request correlation filter: reads/generates {@code X-Request-Id}, exposes
 * it in every log line emitted while this request is being processed, and
 * echoes it back on the response.
 *
 * <p>Registered the same way as any other {@link OncePerRequestFilter} bean
 * in this codebase: a plain {@code @Component} picked up by Spring Boot's
 * servlet filter auto-registration — no {@code SecurityFilterChain} needed,
 * consistent with this service having none (see
 * {@link com.travelplan.identity.config.SecurityConfig}).</p>
 *
 * <ul>
 *   <li>If the incoming request carries an {@code X-Request-Id} header, it is
 *       reused as-is (propagated from an upstream caller, e.g. Traefik or
 *       another service).</li>
 *   <li>Otherwise a new random UUID is generated.</li>
 *   <li>The value is placed in the SLF4J {@link MDC} under {@link #MDC_KEY},
 *       which {@code logback-spring.xml}'s JSON encoder includes in every log
 *       line automatically.</li>
 *   <li>The value is set on the response under {@link #REQUEST_ID_HEADER} so
 *       the caller can see it too.</li>
 *   <li>The MDC entry is always removed in a {@code finally} block, so it
 *       never leaks into a later request reusing the same worker thread.</li>
 * </ul>
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
