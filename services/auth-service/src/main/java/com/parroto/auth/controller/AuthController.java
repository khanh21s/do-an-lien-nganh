package com.parroto.auth.controller;

import com.parroto.auth.dto.*;
import com.parroto.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request.email(), request.password(), request.cefrLevel());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.email(), request.password()));
    }

    @PostMapping("/login-google")
    public ResponseEntity<AuthResponse> loginGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request.googleToken()));
    }

    @PostMapping("/login-apple")
    public ResponseEntity<AuthResponse> loginApple(@Valid @RequestBody AppleLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithApple(request.appleToken()));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest httpRequest) {
        String header = httpRequest.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            authService.logout(header.substring(7));
        }
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }
}
