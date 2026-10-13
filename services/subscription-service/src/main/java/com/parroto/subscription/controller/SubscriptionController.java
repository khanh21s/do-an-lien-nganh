package com.parroto.subscription.controller;

import com.parroto.subscription.dto.*;
import com.parroto.subscription.security.AuthenticatedUser;
import com.parroto.subscription.service.PlanService;
import com.parroto.subscription.service.SubscriptionService;
import com.parroto.subscription.service.VnpayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final PlanService planService;
    private final SubscriptionService subscriptionService;
    private final String frontendUrl;

    public SubscriptionController(PlanService planService, SubscriptionService subscriptionService,
                                   @org.springframework.beans.factory.annotation.Value("${cors.allowed-origin}") String frontendUrl) {
        this.planService = planService;
        this.subscriptionService = subscriptionService;
        this.frontendUrl = frontendUrl;
    }

    @GetMapping("/plans")
    public ResponseEntity<PlansListResponse> plans() {
        return ResponseEntity.ok(planService.listPlans());
    }

    @GetMapping("/user")
    public ResponseEntity<UserSubscriptionResponse> userSubscription(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(subscriptionService.getUserSubscription(principal.userUuid()));
    }

    @PostMapping("/create-payment")
    public ResponseEntity<CreatePaymentResponse> createPayment(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @Valid @RequestBody CreatePaymentRequest request,
                                                                 HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        return ResponseEntity.ok(subscriptionService.createPayment(principal.userUuid(), request.planId(), clientIp));
    }

    /**
     * SUB-02: VNPay server-to-server IPN (public; authenticated by the HMAC signature). Always answers
     * HTTP 200 with VNPay's {@code {RspCode, Message}} body so VNPay stops/continues retrying correctly.
     */
    @GetMapping("/vnpay-ipn")
    public ResponseEntity<VnpayIpnResponse> vnpayIpn(@RequestParam Map<String, String> params) {
        try {
            return ResponseEntity.ok(subscriptionService.processVnpayResult(params).toIpnResponse());
        } catch (RuntimeException e) {
            log.error("VNPay IPN processing failed for txnRef={}", params.get("vnp_TxnRef"), e);
            return ResponseEntity.ok(new VnpayIpnResponse(VnpayResult.UNKNOWN_ERROR, "Unknown error"));
        }
    }

    @GetMapping("/payment-callback")
    public void paymentCallback(@RequestParam Map<String, String> params, HttpServletResponse response) throws IOException {
        String path = subscriptionService.handlePaymentCallback(params);
        response.sendRedirect(frontendUrl + path);
    }

    @PostMapping("/activate-code")
    public ResponseEntity<UserSubscriptionResponse> activateCode(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                    @Valid @RequestBody ActivateCodeRequest request) {
        return ResponseEntity.ok(subscriptionService.activateCode(principal.userUuid(), request.code()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<PageResponse<TransactionResponse>> transactions(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(subscriptionService.getTransactions(principal.userUuid(), page, limit));
    }
}
