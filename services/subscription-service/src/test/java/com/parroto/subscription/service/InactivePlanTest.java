package com.parroto.subscription.service;

import com.parroto.subscription.dto.CreateActivationCodesRequest;
import com.parroto.subscription.dto.PlanResponse;
import com.parroto.subscription.entity.ActivationCode;
import com.parroto.subscription.entity.Plan;
import com.parroto.subscription.exception.ApiException;
import com.parroto.subscription.repository.ActivationCodeRepository;
import com.parroto.subscription.repository.PaymentTransactionRepository;
import com.parroto.subscription.repository.PlanRepository;
import com.parroto.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** T14-SUB: disabled plans are hidden from the public list and cannot be bought or redeemed. */
class InactivePlanTest {

    private final Plan active = Plan.builder().id("plan-1month").name("1 tháng").durationDays(30).price(79000L).build();
    private final Plan retired = Plan.builder().id("plan-old").name("Gói cũ").durationDays(30).price(50000L)
            .isActive(false).build();

    private PlanRepository planRepository;
    private PaymentTransactionRepository transactionRepository;
    private ActivationCodeRepository codeRepository;
    private SubscriptionRepository subscriptionRepository;
    private VNPayService vnPay;
    private PlanService planService;
    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        transactionRepository = mock(PaymentTransactionRepository.class);
        codeRepository = mock(ActivationCodeRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        vnPay = mock(VNPayService.class);
        when(vnPay.isConfigured()).thenReturn(true);
        when(planRepository.findById("plan-1month")).thenReturn(Optional.of(active));
        when(planRepository.findById("plan-old")).thenReturn(Optional.of(retired));
        planService = new PlanService(planRepository);
        subscriptionService = new SubscriptionService(subscriptionRepository, transactionRepository, codeRepository,
                planService, vnPay, mock(SepayService.class), mock(EmailService.class), mock(AuthServiceClient.class),
                mock(PremiumCacheEvictor.class), "");
    }

    @Test
    void newPlansAreActiveByDefault() {
        assertThat(Plan.builder().id("x").build().isActive()).isTrue();
    }

    @Test
    void publicListUsesOnlyActivePlansAndKeepsShape() {
        when(planRepository.findByIsActiveTrueOrderByDurationDaysAscPriceAsc()).thenReturn(List.of(active));

        List<PlanResponse> plans = planService.listPlans().plans();

        assertThat(plans).extracting(PlanResponse::id).containsExactly("plan-1month");
        assertThat(plans.get(0).priceDisplay()).isNotBlank();
        verify(planRepository, never()).findAll();
    }

    @Test
    void createPaymentForDisabledPlanIs400() {
        assertNotOnSale(() -> subscriptionService.createPayment(UUID.randomUUID(), "plan-old", "127.0.0.1"));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void redeemingCodeOfDisabledPlanIs400AndLeavesCodeUnused() {
        ActivationCode code = ActivationCode.builder().id(UUID.randomUUID()).code("PROMO-AAAAA-AAAAA").plan(retired)
                .expiresAt(OffsetDateTime.now().plusDays(10)).build();
        when(codeRepository.findByCodeForUpdate("PROMO-AAAAA-AAAAA")).thenReturn(Optional.of(code));

        assertNotOnSale(() -> subscriptionService.activateCode(UUID.randomUUID(), "PROMO-AAAAA-AAAAA"));
        assertThat(code.getUsedByUserId()).isNull();
        verify(codeRepository, never()).save(any());
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void generatingCodesForDisabledPlanIs400() {
        ActivationCodeService codes = new ActivationCodeService(codeRepository, planService, mock(AuthServiceClient.class));
        assertNotOnSale(() -> codes.createCodes(new CreateActivationCodesRequest("plan-old", 5, null)));
    }

    @Test
    void activePlanStillPassesTheCheck() {
        assertThat(planService.getActivePlanOrThrow("plan-1month")).isSameAs(active);
    }

    private static void assertNotOnSale(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getMessage()).isEqualTo("Gói này hiện không còn bán");
        });
    }
}
