package com.parroto.subscription.service;

import com.parroto.subscription.entity.PaymentTransaction;
import com.parroto.subscription.entity.Plan;
import com.parroto.subscription.entity.Subscription;
import com.parroto.subscription.repository.ActivationCodeRepository;
import com.parroto.subscription.repository.PaymentTransactionRepository;
import com.parroto.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SePay checkout signing and the API-confirmed settlement of SePay transactions.
 */
class SepayServiceTest {

    private static final String SECRET = "spsk_test_secret";

    private final SepayService sepay = new SepayService("sandbox", "SP-TEST-X", SECRET,
            "http://localhost:8000/", mock(RestTemplate.class));

    // ---- signing ----

    @Test
    void checkoutFieldsAreSignedLikeTheOfficialSdk() {
        Map<String, String> fields = sepay.checkoutFields("inv-1", 79000, "Nâng cấp gói 1 tháng Premium");

        assertThat(List.copyOf(fields.keySet())).containsExactly("merchant", "operation", "payment_method",
                "order_invoice_number", "order_amount", "currency", "order_description",
                "success_url", "error_url", "cancel_url", "signature");
        assertThat(fields.get("order_description")).isEqualTo("Nang cap goi 1 thang Premium");
        assertThat(fields.get("success_url"))
                .isEqualTo("http://localhost:8000/subscriptions/sepay/return?txn=inv-1&result=success");
        // Base64(HMAC-SHA256) of "merchant=SP-TEST-X,operation=PURCHASE,...,cancel_url=...", computed
        // independently with the algorithm of sepay-pg-node's Checkout.signFields.
        assertThat(fields.get("signature")).isEqualTo("4t7fr+XrWQYUApJ97JqrZqY32W96q/cF9upL57x5vCM=");
    }

    @Test
    void sandboxAndProductionUseTheirOwnCheckoutHost() {
        assertThat(sepay.checkoutInitUrl()).isEqualTo("https://pay-sandbox.sepay.vn/v1/checkout/init");
        SepayService prod = new SepayService("production", "M", "S", "https://api.example.com", mock(RestTemplate.class));
        assertThat(prod.checkoutInitUrl()).isEqualTo("https://pay.sepay.vn/v1/checkout/init");
        assertThat(prod.checkoutPageUrl("abc")).isEqualTo("https://api.example.com/subscriptions/sepay/checkout/abc");
    }

    @Test
    void ipnKeyMustMatchTheSecret() {
        assertThat(sepay.isValidIpnKey(SECRET)).isTrue();
        assertThat(sepay.isValidIpnKey("wrong")).isFalse();
        assertThat(sepay.isValidIpnKey(null)).isFalse();
        SepayService unconfigured = new SepayService("sandbox", "", "", "x", mock(RestTemplate.class));
        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(unconfigured.isValidIpnKey("")).isFalse();
    }

    // ---- settlement (SubscriptionService) ----

    private SepayService sepayMock;
    private PaymentTransactionRepository transactionRepository;
    private SubscriptionRepository subscriptionRepository;
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    private SubscriptionService service;
    private final Plan plan = Plan.builder().id("plan-1month").name("1 tháng Premium").durationDays(30).price(79000L).build();
    private PaymentTransaction tx;

    @BeforeEach
    void setUp() {
        sepayMock = mock(SepayService.class);
        when(sepayMock.isConfigured()).thenReturn(true);
        transactionRepository = mock(PaymentTransactionRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        PlanService planService = mock(PlanService.class);
        service = new SubscriptionService(subscriptionRepository, transactionRepository,
                mock(ActivationCodeRepository.class), planService, mock(VNPayService.class), sepayMock,
                mock(EmailService.class), mock(AuthServiceClient.class), new PremiumCacheEvictor(redisTemplate), "");

        UUID id = UUID.randomUUID();
        tx = PaymentTransaction.builder().id(id).transactionId(id.toString()).userId(UUID.randomUUID())
                .planId(plan.getId()).amount(plan.getPrice()).method("sepay").status("pending").build();
        when(transactionRepository.findByIdForUpdate(id)).thenReturn(Optional.of(tx));
        when(transactionRepository.findById(id)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(planService.getPlanOrThrow(plan.getId())).thenReturn(plan);
        when(subscriptionRepository.findFirstByUserIdAndStatusAndEndAtAfterOrderByEndAtDesc(any(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void sepayReports(String status, Long amount) {
        when(sepayMock.fetchOrder(tx.getTransactionId())).thenReturn(new SepayService.SepayOrder(status, amount));
    }

    @Test
    void sepayIsPreferredWhenConfigured() {
        assertThat(service.paymentProvider()).isEqualTo("sepay");
    }

    @Test
    void capturedOrderActivatesSubscriptionOnce() {
        sepayReports("CAPTURED", 79000L);

        assertThat(service.handleSepayReturn(tx.getTransactionId(), "success")).isEqualTo("/subscription/success");
        assertThat(tx.getStatus()).isEqualTo("success");
        // IPN arriving afterwards: already settled, nothing granted twice
        assertThat(service.processSepayOrder(tx.getTransactionId())).isEqualTo("success");
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        verify(sepayMock, times(1)).fetchOrder(any());
        verify(redisTemplate).delete("premium:" + tx.getUserId());
    }

    @Test
    void forgedSuccessRedirectDoesNotActivate() {
        sepayReports("AUTHENTICATION_NOT_NEEDED", 79000L);

        service.handleSepayReturn(tx.getTransactionId(), "success");

        assertThat(tx.getStatus()).isEqualTo("pending");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void amountMismatchFails() {
        sepayReports("CAPTURED", 1000L);

        assertThat(service.handleSepayReturn(tx.getTransactionId(), "success"))
                .isEqualTo("/subscription/failed?reason=payment_declined");
        assertThat(tx.getStatus()).isEqualTo("failed");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void cancelledOrderFails() {
        sepayReports("CANCELLED", 79000L);

        assertThat(service.handleSepayReturn(tx.getTransactionId(), "cancel"))
                .isEqualTo("/subscription/failed?reason=payment_declined");
        assertThat(tx.getStatus()).isEqualTo("failed");
    }

    @Test
    void cancelBeforeSepayUpdatesKeepsPending() {
        sepayReports("AUTHENTICATION_NOT_NEEDED", 79000L);

        assertThat(service.handleSepayReturn(tx.getTransactionId(), "cancel"))
                .isEqualTo("/subscription/failed?reason=payment_cancelled");
        assertThat(tx.getStatus()).isEqualTo("pending");
    }

    @Test
    void unknownOrNonSepayTransactionIsNotFound() {
        assertThat(service.handleSepayReturn("not-a-uuid", "success")).isEqualTo("/subscription/failed?reason=not_found");
        tx.setMethod("vnpay");
        assertThat(service.processSepayOrder(tx.getTransactionId())).isNull();
        verify(sepayMock, never()).fetchOrder(any());
    }

    @Test
    void checkoutFormOnlyForPendingSepayTransactions() {
        when(sepayMock.checkoutFields(any(), anyLong(), any())).thenReturn(Map.of("merchant", "M"));
        assertThat(service.sepayCheckoutFields(tx.getTransactionId())).isNotNull();

        tx.setStatus("success");
        assertThat(service.sepayCheckoutFields(tx.getTransactionId())).isNull();
    }
}
