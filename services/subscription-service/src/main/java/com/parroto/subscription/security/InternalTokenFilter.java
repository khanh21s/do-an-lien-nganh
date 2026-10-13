package com.parroto.subscription.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.subscription.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * X-02: guards service-to-service endpoints under {@code /internal/**}. Callers must send
 * {@code X-Internal-Token} equal to the shared {@code INTERNAL_TOKEN}; anything else is a 401.
 * Other paths pass through untouched.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    static final String INTERNAL_PREFIX = "/internal";
    static final int RECOMMENDED_MIN_LENGTH = 32;

    private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);

    private final byte[] expectedDigest;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(@Value("${internal.token:}") String internalToken, ObjectMapper objectMapper) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("INTERNAL_TOKEN must be set (shared secret for /internal/** calls)");
        }
        if (internalToken.length() < RECOMMENDED_MIN_LENGTH) {
            log.warn("INTERNAL_TOKEN is shorter than {} characters; use a long random value", RECOMMENDED_MIN_LENGTH);
        }
        this.expectedDigest = sha256(internalToken);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Servlet path is already URL-decoded by the container, so encoded variants can't bypass this.
        String path = request.getServletPath();
        if (request.getPathInfo() != null) {
            path = path + request.getPathInfo();
        }
        return !(path.equals(INTERNAL_PREFIX) || path.startsWith(INTERNAL_PREFIX + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        // Compare fixed-length digests in constant time (no early exit, no length leak).
        if (provided != null && MessageDigest.isEqual(expectedDigest, sha256(provided))) {
            chain.doFilter(request, response);
            return;
        }
        Object requestId = request.getAttribute("requestId");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body = ErrorResponse.of("Invalid or missing internal token",
                HttpStatus.UNAUTHORIZED.value(), requestId != null ? requestId.toString() : "unknown");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
