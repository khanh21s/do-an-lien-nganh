package com.parroto.subscription.controller;

import com.parroto.subscription.dto.AdminPlanRequest;
import com.parroto.subscription.dto.AdminPlanResponse;
import com.parroto.common.security.AdminGuard;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.subscription.service.AdminPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** T14-SUB: admin plan management (no DELETE: plans are disabled with {@code isActive=false}). */
@RestController
@RequestMapping("/subscriptions/admin/plans")
public class AdminPlanController {

    private final AdminPlanService adminPlanService;
    private final AdminGuard adminGuard;

    public AdminPlanController(AdminPlanService adminPlanService, AdminGuard adminGuard) {
        this.adminPlanService = adminPlanService;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public ResponseEntity<List<AdminPlanResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(adminPlanService.listPlans());
    }

    @PostMapping
    public ResponseEntity<AdminPlanResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody AdminPlanRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(adminPlanService.createPlan(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminPlanResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable String id,
                                                    @Valid @RequestBody AdminPlanRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(adminPlanService.updatePlan(id, request));
    }
}
