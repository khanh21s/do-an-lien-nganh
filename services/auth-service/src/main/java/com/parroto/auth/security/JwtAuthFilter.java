package com.parroto.auth.security;

import com.parroto.auth.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

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
                Claims claims = jwtService.parse(token);
                if (!blacklistService.isBlacklisted(claims.getId())) {
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
}
