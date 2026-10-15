package com.parroto.subscription.controller;

import com.parroto.common.dto.PageResponse;
import com.parroto.subscription.dto.*;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.subscription.service.PlanService;
import com.parroto.subscription.service.SepayService;
import com.parroto.subscription.service.SubscriptionService;
import com.parroto.subscription.service.VnpayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;
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
    private final SepayService sepayService;
    private final String frontendUrl;

    public SubscriptionController(PlanService planService, SubscriptionService subscriptionService,
                                   SepayService sepayService,
                                   @org.springframework.beans.factory.annotation.Value("${cors.allowed-origin}") String frontendUrl) {
        this.planService = planService;
        this.subscriptionService = subscriptionService;
        this.sepayService = sepayService;
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

    /**
     * SePay checkout (public, opened by the browser from {@code paymentUrl}): SePay only accepts a POSTed
     * signed form, so this page auto-submits it. The transaction id is an unguessable UUID and the page
     * only works while that transaction is pending.
     */
    @GetMapping(value = "/sepay/checkout/{txnRef}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> sepayCheckout(@PathVariable String txnRef) {
        Map<String, String> fields = subscriptionService.sepayCheckoutFields(txnRef);
        if (fields == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(frontendUrl + "/subscription/failed?reason=not_found"))
                    .build();
        }
        StringBuilder html = new StringBuilder()
                .append("<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>Đang chuyển đến SePay…</title></head>")
                .append("<body style=\"font-family:system-ui,sans-serif;text-align:center;padding:48px 16px\">")
                .append("<p>Đang chuyển đến cổng thanh toán SePay…</p>")
                .append("<form id=\"sepay\" method=\"POST\" action=\"")
                .append(HtmlUtils.htmlEscape(sepayService.checkoutInitUrl())).append("\">");
        fields.forEach((name, value) -> html.append("<input type=\"hidden\" name=\"")
                .append(HtmlUtils.htmlEscape(name)).append("\" value=\"")
                .append(HtmlUtils.htmlEscape(value)).append("\">"));
        html.append("<noscript><button type=\"submit\">Tiếp tục thanh toán</button></noscript></form>")
                .append("<script>document.getElementById('sepay').submit();</script></body></html>");
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Content-Security-Policy",
                        "default-src 'none'; script-src 'unsafe-inline'; form-action https://*.sepay.vn")
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .body(html.toString());
    }

    /** SePay success/error/cancel redirect (public). The real result is read back from SePay's API. */
    @GetMapping("/sepay/return")
    public void sepayReturn(@RequestParam(name = "txn", required = false) String txnRef,
                            @RequestParam(name = "result", required = false) String result,
                            HttpServletResponse response) throws IOException {
        response.sendRedirect(frontendUrl + subscriptionService.handleSepayReturn(txnRef, result));
    }

    /**
     * SePay IPN (public; authenticated by the {@code X-Secret-Key} header). The payload only tells us
     * which order changed: the status is confirmed through SePay's API like the browser return.
     */
    @PostMapping("/sepay/ipn")
    public ResponseEntity<Map<String, Object>> sepayIpn(
            @RequestHeader(name = "X-Secret-Key", required = false) String secretKey,
            @RequestBody(required = false) Map<String, Object> payload) {
        if (!sepayService.isValidIpnKey(secretKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        Object order = payload == null ? null : payload.get("order");
        Object invoice = order instanceof Map<?, ?> o ? o.get("order_invoice_number") : null;
        try {
            String status = invoice == null ? null : subscriptionService.processSepayOrder(invoice.toString());
            log.info("SePay IPN {} for invoice {} -> {}", payload == null ? null : payload.get("notification_type"), invoice, status);
        } catch (RuntimeException e) {
            log.error("SePay IPN processing failed for invoice {}", invoice, e);
        }
        return ResponseEntity.ok(Map.of("success", true));
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
