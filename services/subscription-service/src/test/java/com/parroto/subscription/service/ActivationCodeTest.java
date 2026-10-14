package com.parroto.subscription.service;

import com.parroto.subscription.dto.ActivationCodeResponse;
import com.parroto.subscription.dto.CreateActivationCodesRequest;
import com.parroto.subscription.dto.CreateActivationCodesResponse;
import com.parroto.subscription.dto.PageResponse;
import com.parroto.subscription.entity.ActivationCode;
import com.parroto.subscription.entity.Plan;
import com.parroto.subscription.entity.Subscription;
import com.parroto.subscription.exception.ApiException;
import com.parroto.subscription.repository.ActivationCodeRepository;
import com.parroto.subscription.repository.PaymentTransactionRepository;
import com.parroto.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** SUB-04/SUB-05 code redemption and ADM-06 code generation/listing. */
class ActivationCodeTest {

    private final Plan plan30 = Plan.builder().id("plan-1month").name("1 tháng Premium").durationDays(30).price(79000L).build();

    private ActivationCodeRepository codeRepository;
    private SubscriptionRepository subscriptionRepository;
    private AuthServiceClient authServiceClient;
    private PlanService planService;
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    private SubscriptionService subscriptionService;
    private ActivationCodeService activationCodeService;

    @BeforeEach
    void setUp() {
        codeRepository = mock(ActivationCodeRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        authServiceClient = mock(AuthServiceClient.class);
        planService = mock(PlanService.class);
        when(planService.getPlanOrThrow("plan-1month")).thenReturn(plan30);
        when(planService.getPlanOrThrow("nope")).thenThrow(ApiException.notFound("Plan not found"));
        when(planService.getActivePlanOrThrow("plan-1month")).thenReturn(plan30);
        when(planService.getActivePlanOrThrow("nope")).thenThrow(ApiException.notFound("Plan not found"));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(codeRepository.save(any(ActivationCode.class))).thenAnswer(inv -> inv.getArgument(0));
        when(codeRepository.saveAllAndFlush(anyCollection())).thenAnswer(inv -> new ArrayList<>(inv.getArgument(0)));
        when(codeRepository.findExistingCodes(anyCollection())).thenReturn(List.of());

        subscriptionService = new SubscriptionService(subscriptionRepository, mock(PaymentTransactionRepository.class),
                codeRepository, planService, mock(VNPayService.class), mock(SepayService.class), mock(EmailService.class),
                authServiceClient, new PremiumCacheEvictor(redisTemplate), "");
        activationCodeService = new ActivationCodeService(codeRepository, planService, authServiceClient);
    }

    private ActivationCode unusedCode(String code) {
        return ActivationCode.builder().id(UUID.randomUUID()).code(code).plan(plan30)
                .expiresAt(OffsetDateTime.now().plusDays(10)).build();
    }

    // ---- redemption ----

    @Test
    void redeemWithoutActiveSubscriptionStartsNow() {
        UUID userId = UUID.randomUUID();
        when(codeRepository.findByCodeForUpdate("PROMO-AAAAA-BBBBB")).thenReturn(Optional.of(unusedCode("PROMO-AAAAA-BBBBB")));
        when(subscriptionRepository.findFirstByUserIdAndStatusAndEndAtAfterOrderByEndAtDesc(eqUser(userId), anyString(), any()))
                .thenReturn(Optional.empty());

        Subscription saved = redeemAndCaptureSubscription(userId, " promo-aaaaa-bbbbb ");

        assertThat(saved.getEndAt()).isCloseTo(OffsetDateTime.now().plusDays(30), within(5, ChronoUnit.SECONDS));
        verify(redisTemplate).delete("premium:" + userId);
    }

    @Test
    void redeemWhileActiveAccumulatesOntoEndDate() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime d = OffsetDateTime.now().plusDays(12);
        Subscription active = Subscription.builder().id(UUID.randomUUID()).userId(userId).plan(plan30)
                .startAt(OffsetDateTime.now().minusDays(18)).endAt(d).status("active").build();
        when(codeRepository.findByCodeForUpdate("PROMO-AAAAA-CCCCC")).thenReturn(Optional.of(unusedCode("PROMO-AAAAA-CCCCC")));
        when(subscriptionRepository.findFirstByUserIdAndStatusAndEndAtAfterOrderByEndAtDesc(eqUser(userId), anyString(), any()))
                .thenReturn(Optional.of(active));

        Subscription saved = redeemAndCaptureSubscription(userId, "PROMO-AAAAA-CCCCC");

        assertThat(saved.getEndAt()).isEqualTo(d.plusDays(30));
        verify(redisTemplate).delete("premium:" + userId);
    }

    private Subscription redeemAndCaptureSubscription(UUID userId, String code) {
        subscriptionService.activateCode(userId, code);
        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        return captor.getValue();
    }

    private static UUID eqUser(UUID userId) {
        return org.mockito.ArgumentMatchers.eq(userId);
    }

    @Test
    void usedCodeIs400() {
        ActivationCode used = unusedCode("PROMO-USED1-USED1");
        used.setUsedByUserId(UUID.randomUUID());
        when(codeRepository.findByCodeForUpdate("PROMO-USED1-USED1")).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> subscriptionService.activateCode(UUID.randomUUID(), "PROMO-USED1-USED1"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Mã đã được sử dụng");
                });
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void expiredCodeIs400() {
        ActivationCode expired = unusedCode("PROMO-EXPRD-EXPRD");
        expired.setExpiresAt(OffsetDateTime.now().minusDays(1));
        when(codeRepository.findByCodeForUpdate("PROMO-EXPRD-EXPRD")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> subscriptionService.activateCode(UUID.randomUUID(), "PROMO-EXPRD-EXPRD"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Mã đã hết hạn");
                });
    }

    @Test
    void unknownCodeIs404() {
        when(codeRepository.findByCodeForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.activateCode(UUID.randomUUID(), "PROMO-XXXXX-XXXXX"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Mã không hợp lệ");
                });
    }

    // ---- generation ----

    @Test
    void createsRequestedNumberOfUniqueWellFormedCodes() {
        CreateActivationCodesResponse r = activationCodeService.createCodes(
                new CreateActivationCodesRequest("plan-1month", 10, null));

        assertThat(r.codes()).hasSize(10);
        assertThat(r.codes()).extracting(ActivationCodeResponse::code).doesNotHaveDuplicates()
                .allMatch(c -> c.matches("PROMO-[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}"));
        assertThat(r.codes()).allSatisfy(c -> {
            assertThat(c.planId()).isEqualTo("plan-1month");
            assertThat(c.expiresAt()).isCloseTo(OffsetDateTime.now().plusDays(365), within(5, ChronoUnit.SECONDS));
        });
    }

    @Test
    void retriesWhenGeneratedCodesAlreadyExist() {
        // Deterministic generator: first round yields codes that "already exist" in the DB.
        Set<String> firstRound = new HashSet<>();
        when(codeRepository.findExistingCodes(anyCollection())).thenAnswer(inv -> {
            Collection<String> candidates = inv.getArgument(0);
            if (firstRound.isEmpty()) {
                firstRound.addAll(candidates);
                return new ArrayList<>(candidates);
            }
            return List.of();
        });

        Set<String> codes = activationCodeService.generateUniqueCodes(3);

        assertThat(codes).hasSize(3).doesNotContainAnyElementsOf(firstRound);
        verify(codeRepository, times(2)).findExistingCodes(anyCollection());
    }

    @Test
    void rejectsQuantityOutOfRangeAndPastExpiry() {
        assertThatThrownBy(() -> activationCodeService.createCodes(new CreateActivationCodesRequest("plan-1month", 501, null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> activationCodeService.createCodes(new CreateActivationCodesRequest("plan-1month", 0, null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> activationCodeService.createCodes(
                new CreateActivationCodesRequest("plan-1month", 1, "2020-01-01")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> activationCodeService.createCodes(
                new CreateActivationCodesRequest("plan-1month", 1, "not-a-date")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void acceptsDateOrDateTimeExpiry() {
        assertThat(ActivationCodeService.parseExpiresAt("2099-12-31").toLocalDate().toString()).isEqualTo("2099-12-31");
        assertThat(ActivationCodeService.parseExpiresAt("2099-12-31T00:00:00+07:00").getYear()).isEqualTo(2099);
    }

    // ---- listing ----

    @Test
    void listUsedCodesIncludesEmailAndUsedAt() {
        UUID userId = UUID.randomUUID();
        ActivationCode used = unusedCode("PROMO-AAAAA-DDDDD");
        used.setUsedByUserId(userId);
        used.setUsedAt(OffsetDateTime.now().minusHours(1));
        when(codeRepository.findByUsedByUserIdIsNotNullOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of(used), PageRequest.of(0, 20), 1));
        when(authServiceClient.findEmail(userId)).thenReturn("learner@example.com");

        PageResponse<ActivationCodeResponse> page = activationCodeService.listCodes(true, 1, 20);

        assertThat(page.data()).hasSize(1);
        assertThat(page.data().get(0).usedByEmail()).isEqualTo("learner@example.com");
        assertThat(page.data().get(0).usedAt()).isNotNull();
        assertThat(page.pagination().total()).isEqualTo(1);
    }
}
