package com.parroto.auth.controller;

import com.parroto.auth.dto.AdminUpdateUserRequest;
import com.parroto.auth.dto.AdminUserResponse;
import com.parroto.auth.dto.AdminUserStatsResponse;
import com.parroto.auth.dto.PageResponse;
import com.parroto.auth.security.AdminGuard;
import com.parroto.auth.security.AuthenticatedUser;
import com.parroto.auth.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** T14-AUTH admin console endpoints. Bearer token required (SecurityConfig) + ADMIN_EMAILS (AdminGuard → 403). */
@RestController
@RequestMapping("/auth/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminGuard adminGuard;

    public AdminUserController(AdminUserService adminUserService, AdminGuard adminGuard) {
        this.adminUserService = adminUserService;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(adminUserService.listUsers(q, status, page, limit));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable UUID id) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(adminUserService.getUser(id));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable UUID id,
                                                    @Valid @RequestBody AdminUpdateUserRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(adminUserService.setActive(id, request.isActive(), principal.userUuid()));
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminUserStatsResponse> stats(@AuthenticationPrincipal AuthenticatedUser principal) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(adminUserService.stats());
    }
}
