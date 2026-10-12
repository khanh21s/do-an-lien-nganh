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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SubscriptionService {

    private static final long PAYMENT_URL_TTL_SECONDS = 900;

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final ActivationCodeRepository activationCodeRepository;
    private final PlanService planService;
    private final VNPayService vnPayService;
    private final EmailService emailService;
    private final AuthServiceClient authServiceClient;
    private final CacheService cacheService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                PaymentTransactionRepository transactionRepository,
                                ActivationCodeRepository activationCodeRepository,
                                PlanService planService, VNPayService vnPayService,
                                EmailService emailService, AuthServiceClient authServiceClient,
                                CacheService cacheService) {
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
        this.activationCodeRepository = activationCodeRepository;
        this.planService = planService;
        this.vnPayService = vnPayService;
        this.emailService = emailService;
        this.authServiceClient = authServiceClient;
        this.cacheService = cacheService;
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
                .id(sub.getId().toString())
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
        Plan plan = planService.getPlanOrThrow(planId);
        if (isPremium(userId)) {
            throw ApiException.conflict("User already has an active Premium subscription");
        }
        if (!vnPayService.isConfigured()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "VNPay is not configured (VNPAY_MERCHANT_ID / VNPAY_SECRET missing)");
        }

        PaymentTransaction transaction = PaymentTransaction.builder()
                .userId(userId)
                .planId(plan.getId())
                .amount(plan.getPrice())
                .method("vnpay")
                .status("pending")
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
     * @return the frontend path to redirect the browser to ("/subscription/success" or "/subscription/failed")
     */
    @Transactional
    public String handlePaymentCallback(Map<String, String> params) {
        if (!vnPayService.verifyCallback(params)) {
            return "/subscription/failed?reason=invalid_signature";
        }
        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        if (txnRef == null) {
            return "/subscription/failed?reason=missing_reference";
        }

        UUID transactionId;
        try {
            transactionId = UUID.fromString(txnRef);
        } catch (IllegalArgumentException e) {
            return "/subscription/failed?reason=invalid_reference";
        }

        PaymentTransaction transaction = transactionRepository.findById(transactionId).orElse(null);
        if (transaction == null) {
            return "/subscription/failed?reason=not_found";
        }
        if ("success".equals(transaction.getStatus())) {
            return "/subscription/success"; // already processed (idempotent)
        }

        if (!"00".equals(responseCode)) {
            transaction.setStatus("failed");
            transactionRepository.save(transaction);
            return "/subscription/failed?reason=payment_declined";
        }

        transaction.setStatus("success");
        transactionRepository.save(transaction);

        Plan plan = planService.getPlanOrThrow(transaction.getPlanId());
        activateSubscription(transaction.getUserId(), plan, "vnpay", transaction.getTransactionId());

        String email = authServiceClient.findEmail(transaction.getUserId());
        emailService.sendPaymentConfirmation(email, plan.getName(),
                OffsetDateTime.now().plusDays(plan.getDurationDays()).format(DateTimeFormatter.ISO_LOCAL_DATE));

        return "/subscription/success";
    }

    @Transactional
    public UserSubscriptionResponse activateCode(UUID userId, String code) {
        ActivationCode activationCode = activationCodeRepository.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("Activation code not found"));
        if (activationCode.getUsedByUserId() != null) {
            throw ApiException.conflict("This activation code has already been used");
        }
        if (activationCode.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw ApiException.badRequest("This activation code has expired");
        }

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

    private void activateSubscription(UUID userId, Plan plan, String paymentMethod, String transactionRef) {
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
        subscriptionRepository.save(subscription);

        cacheService.invalidateByPattern("premium:" + userId);
    }
}
