package com.parroto.subscription.service;

import com.parroto.subscription.dto.*;
import com.parroto.subscription.entity.ActivationCode;
import com.parroto.subscription.entity.PaymentTransaction;
import com.parroto.subscription.entity.Plan;
import com.parroto.subscription.entity.Subscription;
import com.parroto.subscription.exception.ApiException;
import com.parroto.subscription.repository.ActivationCodeRepository;
import com.parroto.subscription.repository.PaymentTransactionRepository;
import com.parroto.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final long PAYMENT_URL_TTL_SECONDS = 900;

    static final String STATUS_PENDING = "pending";
    static final String STATUS_SUCCESS = "success";
    static final String STATUS_FAILED = "failed";

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final ActivationCodeRepository activationCodeRepository;
    private final PlanService planService;
    private final VNPayService vnPayService;
    private final EmailService emailService;
    private final AuthServiceClient authServiceClient;
    private final PremiumCacheEvictor premiumCacheEvictor;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                PaymentTransactionRepository transactionRepository,
                                ActivationCodeRepository activationCodeRepository,
                                PlanService planService, VNPayService vnPayService,
                                EmailService emailService, AuthServiceClient authServiceClient,
                                PremiumCacheEvictor premiumCacheEvictor) {
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
        this.activationCodeRepository = activationCodeRepository;
        this.planService = planService;
        this.vnPayService = vnPayService;
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
        if (!vnPayService.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "VNPay is not configured (VNPAY_MERCHANT_ID / VNPAY_SECRET missing)");
        }

        PaymentTransaction transaction = PaymentTransaction.builder()
                .userId(userId)
                .planId(plan.getId())
                .amount(plan.getPrice())
                .method("vnpay")
                .status(STATUS_PENDING)
                .description("Nâng cấp gói " + plan.getName())
                .build();
        transaction = transactionRepository.save(transaction);

        String txnRef = transaction.getId().toString();
        transaction.setTransactionId(txnRef);
        transactionRepository.save(transaction);

        String paymentUrl = vnPayService.buildPaymentUrl(plan.getPrice(), txnRef, transaction.getDescription(), clientIp);

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

        transaction.setStatus(STATUS_SUCCESS);
        transactionRepository.save(transaction);

        Plan plan = planService.getPlanOrThrow(transaction.getPlanId());
        Subscription subscription = activateSubscription(transaction.getUserId(), plan, "vnpay",
                transaction.getTransactionId());

        UUID userId = transaction.getUserId();
        String endDate = subscription.getEndAt().format(DateTimeFormatter.ISO_LOCAL_DATE);
        AfterCommit.run(() -> emailService.sendPaymentConfirmation(
                authServiceClient.findEmail(userId), plan.getName(), endDate));

        return VnpayResult.of(VnpayResult.OK, "Confirm Success", STATUS_SUCCESS);
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
