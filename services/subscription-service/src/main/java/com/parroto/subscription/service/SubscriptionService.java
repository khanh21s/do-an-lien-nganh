package com.parroto.subscription.service;

import com.parroto.common.dto.PageResponse;
import com.parroto.subscription.dto.*;
import com.parroto.subscription.entity.ActivationCode;
import com.parroto.subscription.entity.PaymentTransaction;
import com.parroto.subscription.entity.Plan;
import com.parroto.subscription.entity.Subscription;
import com.parroto.common.exception.ApiException;
import com.parroto.subscription.repository.ActivationCodeRepository;
import com.parroto.subscription.repository.PaymentTransactionRepository;
import com.parroto.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final long PAYMENT_URL_TTL_SECONDS = VNPayService.PAYMENT_TTL_MINUTES * 60;

    static final String STATUS_PENDING = "pending";
    static final String STATUS_SUCCESS = "success";
    static final String STATUS_FAILED = "failed";

    static final String PROVIDER_VNPAY = "vnpay";
    static final String PROVIDER_SEPAY = "sepay";

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final ActivationCodeRepository activationCodeRepository;
    private final PlanService planService;
    private final VNPayService vnPayService;
    private final SepayService sepayService;
    private final String configuredProvider;
    private final EmailService emailService;
    private final AuthServiceClient authServiceClient;
    private final PremiumCacheEvictor premiumCacheEvictor;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                PaymentTransactionRepository transactionRepository,
                                ActivationCodeRepository activationCodeRepository,
                                PlanService planService, VNPayService vnPayService, SepayService sepayService,
                                EmailService emailService, AuthServiceClient authServiceClient,
                                PremiumCacheEvictor premiumCacheEvictor,
                                @Value("${payment.provider:}") String configuredProvider) {
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
        this.activationCodeRepository = activationCodeRepository;
        this.planService = planService;
        this.vnPayService = vnPayService;
        this.sepayService = sepayService;
        this.configuredProvider = configuredProvider == null ? "" : configuredProvider.trim().toLowerCase(Locale.ROOT);
        this.emailService = emailService;
        this.authServiceClient = authServiceClient;
        this.premiumCacheEvictor = premiumCacheEvictor;
    }

    @Transactional(readOnly = true)
    public UserSubscriptionResponse getUserSubscription(UUID userId) {
        Optional<Subscription> active = subscriptionRepository
                .findFirstByUserIdAndStatusAndEndAtAfterOrderByEndAtDesc(userId, "active", OffsetDateTime.now());
        if (active.isEmpty()) {
            return new UserSubscriptionResponse(null, false);
        }
        Subscription sub = active.get();
        long daysRemaining = Duration.between(OffsetDateTime.now(), sub.getEndAt()).toDays();
        var info = UserSubscriptionResponse.SubscriptionInfo.builder()
                .id(sub.getId() != null ? sub.getId().toString() : null)
                .planId(sub.getPlan().getId())
                .planName(sub.getPlan().getName())
                .startAt(sub.getStartAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .endAt(sub.getEndAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .status(sub.getStatus())
                .daysRemaining(Math.max(daysRemaining, 0))
                .autoRenew(false)
                .build();
        return new UserSubscriptionResponse(info, true);
    }

    @Transactional(readOnly = true)
    public boolean isPremium(UUID userId) {
        return subscriptionRepository
                .findFirstByUserIdAndStatusAndEndAtAfterOrderByEndAtDesc(userId, "active", OffsetDateTime.now())
                .isPresent();
    }

    @Transactional
    public CreatePaymentResponse createPayment(UUID userId, String planId, String clientIp) {
        Plan plan = planService.getActivePlanOrThrow(planId);
        if (isPremium(userId)) {
            throw ApiException.conflict("User already has an active Premium subscription");
        }
        String provider = paymentProvider();
        if (provider == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No payment gateway is configured (SEPAY_MERCHANT_ID / SEPAY_SECRET_KEY or VNPAY_MERCHANT_ID / VNPAY_SECRET)");
        }

        PaymentTransaction transaction = PaymentTransaction.builder()
                .userId(userId)
                .planId(plan.getId())
                .amount(plan.getPrice())
                .method(provider)
                .status(STATUS_PENDING)
                .description("Nâng cấp gói " + plan.getName())
                .build();
        transaction = transactionRepository.save(transaction);

        String txnRef = transaction.getId().toString();
        transaction.setTransactionId(txnRef);
        transactionRepository.save(transaction);

        String paymentUrl = PROVIDER_SEPAY.equals(provider)
                ? sepayService.checkoutPageUrl(txnRef)
                : vnPayService.buildPaymentUrl(plan.getPrice(), txnRef, transaction.getDescription(), clientIp);

        return new CreatePaymentResponse(txnRef, paymentUrl, PAYMENT_URL_TTL_SECONDS, plan.getPrice());
    }

    /**
     * SUB-01/02: single entry point for VNPay results, shared by the server-to-server IPN and the
     * browser return URL. Idempotent: the transaction row is locked and only a {@code pending}
     * transaction is ever updated, so a replayed or concurrent call returns {@code 02}.
     */
    @Transactional
    public VnpayResult processVnpayResult(Map<String, String> params) {
        if (!vnPayService.verifySignature(params)) {
            return VnpayResult.of(VnpayResult.INVALID_SIGNATURE, "Invalid signature", null);
        }
        UUID transactionId = parseUuid(params.get("vnp_TxnRef"));
        if (transactionId == null) {
            return VnpayResult.of(VnpayResult.ORDER_NOT_FOUND, "Order not found", null);
        }
        PaymentTransaction transaction = transactionRepository.findByIdForUpdate(transactionId).orElse(null);
        if (transaction == null) {
            return VnpayResult.of(VnpayResult.ORDER_NOT_FOUND, "Order not found", null);
        }
        if (!STATUS_PENDING.equals(transaction.getStatus())) {
            return VnpayResult.of(VnpayResult.ALREADY_CONFIRMED, "Order already confirmed", transaction.getStatus());
        }

        Long paidAmount = parseLong(params.get("vnp_Amount"));
        if (paidAmount == null || paidAmount != transaction.getAmount() * 100) {
            log.warn("VNPay amount mismatch for transaction {}: expected {} got {}",
                    transactionId, transaction.getAmount() * 100, params.get("vnp_Amount"));
            transaction.setStatus(STATUS_FAILED);
            transactionRepository.save(transaction);
            return VnpayResult.of(VnpayResult.INVALID_AMOUNT, "Invalid amount", STATUS_FAILED);
        }

        boolean paid = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));
        if (!paid) {
            transaction.setStatus(STATUS_FAILED);
            transactionRepository.save(transaction);
            return VnpayResult.of(VnpayResult.OK, "Confirm Success", STATUS_FAILED);
        }

        completePayment(transaction);
        return VnpayResult.of(VnpayResult.OK, "Confirm Success", STATUS_SUCCESS);
    }

    /**
     * Gateway used for new payments: PAYMENT_PROVIDER when set and configured, otherwise SePay if
     * configured, otherwise VNPay if configured; {@code null} when none is.
     */
    String paymentProvider() {
        if (PROVIDER_SEPAY.equals(configuredProvider)) {
            return sepayService.isConfigured() ? PROVIDER_SEPAY : null;
        }
        if (PROVIDER_VNPAY.equals(configuredProvider)) {
            return vnPayService.isConfigured() ? PROVIDER_VNPAY : null;
        }
        if (sepayService.isConfigured()) {
            return PROVIDER_SEPAY;
        }
        return vnPayService.isConfigured() ? PROVIDER_VNPAY : null;
    }

    /** Marks a locked pending transaction paid, grants the plan and mails a receipt after commit. */
    private void completePayment(PaymentTransaction transaction) {
        transaction.setStatus(STATUS_SUCCESS);
        transactionRepository.save(transaction);

        Plan plan = planService.getPlanOrThrow(transaction.getPlanId());
        Subscription subscription = activateSubscription(transaction.getUserId(), plan, transaction.getMethod(),
                transaction.getTransactionId());

        UUID userId = transaction.getUserId();
        String endDate = subscription.getEndAt().format(DateTimeFormatter.ISO_LOCAL_DATE);
        AfterCommit.run(() -> emailService.sendPaymentConfirmation(
                authServiceClient.findEmail(userId), plan.getName(), endDate));
    }

    /**
     * Signed SePay checkout form for a pending SePay transaction (the browser auto-submits it).
     *
     * @return the fields in posting order, or {@code null} if the transaction is unknown or no longer pending
     */
    @Transactional(readOnly = true)
    public Map<String, String> sepayCheckoutFields(String txnRef) {
        UUID id = parseUuid(txnRef);
        PaymentTransaction transaction = id == null ? null : transactionRepository.findById(id).orElse(null);
        if (transaction == null || !PROVIDER_SEPAY.equals(transaction.getMethod())
                || !STATUS_PENDING.equals(transaction.getStatus()) || !sepayService.isConfigured()) {
            return null;
        }
        return sepayService.checkoutFields(transaction.getTransactionId(), transaction.getAmount(),
                transaction.getDescription());
    }

    /**
     * Settles a SePay transaction from the order status read back from SePay's API (never from the
     * unsigned browser redirect). Idempotent: only a locked {@code pending} transaction changes.
     *
     * @return the transaction status afterwards, or {@code null} if the transaction is unknown
     */
    @Transactional
    public String processSepayOrder(String txnRef) {
        UUID id = parseUuid(txnRef);
        PaymentTransaction transaction = id == null ? null : transactionRepository.findByIdForUpdate(id).orElse(null);
        if (transaction == null || !PROVIDER_SEPAY.equals(transaction.getMethod())) {
            return null;
        }
        if (!STATUS_PENDING.equals(transaction.getStatus())) {
            return transaction.getStatus();
        }
        SepayService.SepayOrder order = sepayService.fetchOrder(transaction.getTransactionId());
        if (order == null) {
            return STATUS_PENDING;
        }
        if (order.isPaid()) {
            if (order.amount() == null || !order.amount().equals(transaction.getAmount())) {
                log.warn("SePay amount mismatch for transaction {}: expected {} got {}",
                        id, transaction.getAmount(), order.amount());
                transaction.setStatus(STATUS_FAILED);
                transactionRepository.save(transaction);
                return STATUS_FAILED;
            }
            completePayment(transaction);
            return STATUS_SUCCESS;
        }
        if (SepayService.ORDER_CANCELLED.equals(order.status())) {
            transaction.setStatus(STATUS_FAILED);
            transactionRepository.save(transaction);
            return STATUS_FAILED;
        }
        return STATUS_PENDING;
    }

    /**
     * SePay success/error/cancel redirect: decides where to send the browser.
     *
     * @param result "success" | "error" | "cancel" (from our own return URL, informational only)
     */
    @Transactional
    public String handleSepayReturn(String txnRef, String result) {
        String status = processSepayOrder(txnRef);
        if (status == null) {
            return "/subscription/failed?reason=not_found";
        }
        return switch (status) {
            case STATUS_SUCCESS -> "/subscription/success";
            case STATUS_PENDING -> "success".equals(result)
                    ? "/subscription/success"
                    : "/subscription/failed?reason=" + ("cancel".equals(result) ? "payment_cancelled" : "payment_declined");
            default -> "/subscription/failed?reason=payment_declined";
        };
    }

    /**
     * Return URL (browser redirect). Only decides where to send the user; if the IPN has not
     * arrived yet the result is processed here exactly like the IPN would (idempotent).
     *
     * @return the frontend path to redirect the browser to ("/subscription/success" or "/subscription/failed?...")
     */
    @Transactional
    public String handlePaymentCallback(Map<String, String> params) {
        VnpayResult result = processVnpayResult(params);
        return switch (result.rspCode()) {
            case VnpayResult.INVALID_SIGNATURE -> "/subscription/failed?reason=invalid_signature";
            case VnpayResult.ORDER_NOT_FOUND -> "/subscription/failed?reason=not_found";
            case VnpayResult.INVALID_AMOUNT -> "/subscription/failed?reason=invalid_amount";
            default -> STATUS_SUCCESS.equals(result.transactionStatus())
                    ? "/subscription/success"
                    : "/subscription/failed?reason=payment_declined";
        };
    }

    /**
     * SUB-05: redeems an activation code; time accumulates onto an active subscription.
     */
    @Transactional
    public UserSubscriptionResponse activateCode(UUID userId, String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        ActivationCode activationCode = activationCodeRepository.findByCodeForUpdate(normalized)
                .orElseThrow(() -> ApiException.notFound("Mã không hợp lệ"));
        if (activationCode.getUsedByUserId() != null) {
            throw ApiException.badRequest("Mã đã được sử dụng");
        }
        if (activationCode.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw ApiException.badRequest("Mã đã hết hạn");
        }
        PlanService.requireOnSale(activationCode.getPlan());

        activationCode.setUsedByUserId(userId);
        activationCode.setUsedAt(OffsetDateTime.now());
        activationCodeRepository.save(activationCode);

        activateSubscription(userId, activationCode.getPlan(), "activation_code", activationCode.getCode());

        return getUserSubscription(userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getTransactions(UUID userId, int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), limit);
        Page<PaymentTransaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        Page<TransactionResponse> mapped = transactions.map(TransactionResponse::from);
        return PageResponse.of(mapped, page, limit);
    }

    /**
     * Grants {@code plan.durationDays} of premium. If the user already has an active subscription its
     * {@code end_at} is extended (accumulates); otherwise a new period starts now. Evicts the
     * vocabulary-service premium cache after commit (SUB-04).
     */
    Subscription activateSubscription(UUID userId, Plan plan, String paymentMethod, String transactionRef) {
        OffsetDateTime now = OffsetDateTime.now();
        Subscription subscription = subscriptionRepository
                .findFirstByUserIdAndStatusAndEndAtAfterOrderByEndAtDesc(userId, "active", now)
                .orElseGet(() -> Subscription.builder().userId(userId).startAt(now).build());

        OffsetDateTime baseline = subscription.getEndAt() != null && subscription.getEndAt().isAfter(now)
                ? subscription.getEndAt() : now;

        subscription.setPlan(plan);
        subscription.setStatus("active");
        subscription.setPaymentMethod(paymentMethod);
        subscription.setTransactionId(transactionRef);
        if (subscription.getStartAt() == null) {
            subscription.setStartAt(now);
        }
        subscription.setEndAt(baseline.plusDays(plan.getDurationDays()));
        subscription = subscriptionRepository.save(subscription);

        premiumCacheEvictor.evictAfterCommit(userId);
        return subscription;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
