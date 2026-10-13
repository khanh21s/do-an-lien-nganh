package com.parroto.auth.security;

import com.parroto.auth.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;

    public JwtAuthFilter(JwtService jwtService, TokenBlacklistService blacklistService) {
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                // Only access tokens authenticate requests; refresh tokens go to /auth/refresh-token in the body.
                Claims claims = jwtService.parseAccessToken(token);
                if (!isRevoked(claims.getId())) {
                    AuthenticatedUser principal = new AuthenticatedUser(
                            claims.getSubject(),
                            claims.get("email", String.class),
                            claims.get("cefrLevel", String.class));
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException ignored) {
                // Leave SecurityContext empty; downstream authorization rules will reject as 401/403.
            }
        }
        chain.doFilter(request, response);
    }

    /** Tokens without a jti can't be revoked, so they are rejected. Redis errors fail open (architecture §5.2). */
    private boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return true;
        }
        try {
            return blacklistService.isBlacklisted(jti);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, skipping token blacklist check (fail-open): {}", e.getMessage());
            return false;
        }
    }
}
