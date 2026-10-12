package com.parroto.content.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.content.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String requestId = String.valueOf(request.getAttribute("requestId"));
        ErrorResponse body = ErrorResponse.of("Authentication required", HttpStatus.UNAUTHORIZED.value(), requestId);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
