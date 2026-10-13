package com.parroto.subscription.controller;

import com.parroto.subscription.dto.ActivationCodeResponse;
import com.parroto.subscription.dto.CreateActivationCodesRequest;
import com.parroto.subscription.dto.CreateActivationCodesResponse;
import com.parroto.subscription.dto.PageResponse;
import com.parroto.subscription.security.AdminGuard;
import com.parroto.subscription.security.AuthenticatedUser;
import com.parroto.subscription.service.ActivationCodeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** ADM-06: admin management of activation codes. */
@RestController
@RequestMapping("/subscriptions/admin/activation-codes")
public class AdminActivationCodeController {

    private final ActivationCodeService activationCodeService;
    private final AdminGuard adminGuard;

    public AdminActivationCodeController(ActivationCodeService activationCodeService, AdminGuard adminGuard) {
        this.activationCodeService = activationCodeService;
        this.adminGuard = adminGuard;
    }

    @PostMapping
    public ResponseEntity<CreateActivationCodesResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @Valid @RequestBody CreateActivationCodesRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(activationCodeService.createCodes(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ActivationCodeResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Boolean used,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(activationCodeService.listCodes(used, page, limit));
    }
}
