package com.parroto.subscription.controller;

import com.parroto.subscription.dto.AdminStatsResponse;
import com.parroto.subscription.dto.AdminTransactionResponse;
import com.parroto.common.dto.PageResponse;
import com.parroto.subscription.dto.UserPremiumStatusResponse;
import com.parroto.subscription.dto.UserStatusRequest;
import com.parroto.common.security.AdminGuard;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.subscription.service.AdminReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** T14-SUB: admin transactions, revenue stats and premium status lookup for the users page. */
@RestController
@RequestMapping("/subscriptions/admin")
public class AdminReportController {

    private final AdminReportService adminReportService;
    private final AdminGuard adminGuard;

    public AdminReportController(AdminReportService adminReportService, AdminGuard adminGuard) {
        this.adminReportService = adminReportService;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/transactions")
    public ResponseEntity<PageResponse<AdminTransactionResponse>> transactions(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(adminReportService.listTransactions(status, method, q, from, to, page, limit));
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> stats(@AuthenticationPrincipal AuthenticatedUser principal) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(adminReportService.stats());
    }

    @PostMapping("/user-status")
    public ResponseEntity<List<UserPremiumStatusResponse>> userStatus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UserStatusRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(adminReportService.userStatus(request.userIds()));
    }
}
