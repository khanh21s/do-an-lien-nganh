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

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * VNPay HMAC-SHA512 signing/verification and the shared IPN/return-URL processing logic (SUB-01/02).
 */
class VNPayServiceTest {

    private static final String SECRET = "TESTSECRETKEY0123456789ABCDEFGHIJ";

    private VNPayService vnPay;
    private PaymentTransactionRepository transactionRepository;
    private SubscriptionRepository subscriptionRepository;
    private PlanService planService;
    private EmailService emailService;
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    private SubscriptionService service;

    private final Plan plan = Plan.builder().id("plan-1month").name("1 tháng Premium").durationDays(30).price(79000L).build();
    private PaymentTransaction tx;

    @BeforeEach
    void setUp() {
        vnPay = new VNPayService("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "TMNCODE01", SECRET,
                "http://localhost:8000/subscriptions/payment-callback");
        transactionRepository = mock(PaymentTransactionRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        planService = mock(PlanService.class);
        emailService = mock(EmailService.class);
        service = new SubscriptionService(subscriptionRepository, transactionRepository,
                mock(ActivationCodeRepository.class), planService, vnPay, mock(SepayService.class), emailService,
                mock(AuthServiceClient.class), new PremiumCacheEvictor(redisTemplate), "");

        UUID id = UUID.randomUUID();
        tx = PaymentTransaction.builder().id(id).transactionId(id.toString()).userId(UUID.randomUUID())
                .planId(plan.getId()).amount(plan.getPrice()).method("vnpay").status("pending").build();
        when(transactionRepository.findByIdForUpdate(id)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(planService.getPlanOrThrow(plan.getId())).thenReturn(plan);
        when(subscriptionRepository.findFirstByUserIdAndStatusAndEndAtAfterOrderByEndAtDesc(any(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Params as VNPay would send them back, signed with the shared secret. */
    private Map<String, String> vnpayParams(long amountTimes100, String responseCode, String txnStatus) {
        Map<String, String> p = new HashMap<>();
        p.put("vnp_TmnCode", "TMNCODE01");
        p.put("vnp_Amount", String.valueOf(amountTimes100));
        p.put("vnp_BankCode", "NCB");
        p.put("vnp_OrderInfo", "Nâng cấp gói 1 tháng Premium");
        p.put("vnp_PayDate", "20261013101500");
        p.put("vnp_ResponseCode", responseCode);
        p.put("vnp_TransactionNo", "14000000");
        p.put("vnp_TransactionStatus", txnStatus);
        p.put("vnp_TxnRef", tx.getId().toString());
        p.put("vnp_SecureHashType", "HmacSHA512");
        p.put("vnp_SecureHash", vnPay.sign(p));
        return p;
    }

    // ---- signature ----

    @Test
    void signedParamsVerify() {
        assertThat(vnPay.verifySignature(vnpayParams(7_900_000L, "00", "00"))).isTrue();
    }

    @Test
    void signatureIsCaseInsensitiveHex() {
        Map<String, String> p = vnpayParams(7_900_000L, "00", "00");
        p.put("vnp_SecureHash", p.get("vnp_SecureHash").toUpperCase());
        assertThat(vnPay.verifySignature(p)).isTrue();
    }

    @Test
    void tamperedParamFailsVerification() {
        Map<String, String> p = vnpayParams(7_900_000L, "00", "00");
        p.put("vnp_Amount", "100");
        assertThat(vnPay.verifySignature(p)).isFalse();

        Map<String, String> p2 = vnpayParams(7_900_000L, "24", "02");
        p2.put("vnp_ResponseCode", "00");
        assertThat(vnPay.verifySignature(p2)).isFalse();
    }

    @Test
    void missingHashOrWrongSecretFails() {
        Map<String, String> p = vnpayParams(7_900_000L, "00", "00");
        p.remove("vnp_SecureHash");
        assertThat(vnPay.verifySignature(p)).isFalse();

        VNPayService other = new VNPayService("u", "TMNCODE01", "ANOTHER_SECRET_ANOTHER_SECRET_XX", "r");
        assertThat(other.verifySignature(vnpayParams(7_900_000L, "00", "00"))).isFalse();
    }

    @Test
    void unconfiguredServiceNeverVerifies() {
        VNPayService unconfigured = new VNPayService("u", "", "", "r");
        assertThat(unconfigured.verifySignature(vnpayParams(7_900_000L, "00", "00"))).isFalse();
    }

    @Test
    void paymentUrlCarriesVerifiableSignatureAndAmountTimes100() {
        String url = vnPay.buildPaymentUrl(79000, tx.getId().toString(), "Nâng cấp gói 1 tháng", "10.0.0.1");
        Map<String, String> query = new HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            query.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        assertThat(query.get("vnp_Amount")).isEqualTo("7900000");
        assertThat(vnPay.verifySignature(query)).isTrue();
    }

    private static Map<String, String> queryOf(String url) {
        Map<String, String> query = new HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            query.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return query;
    }

    @Test
    void paymentUrlDatesAreGmt7WithExpiry() {
        // 2026-10-14 03:00 UTC = 10:00 in Vietnam, while the container clock is UTC
        Clock utc = Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneOffset.UTC);
        VNPayService fixed = new VNPayService("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "TMNCODE01",
                SECRET, "http://localhost:8000/subscriptions/payment-callback", utc);
        Map<String, String> query = queryOf(fixed.buildPaymentUrl(79000, tx.getId().toString(), "x", "10.0.0.1"));

        assertThat(query.get("vnp_CreateDate")).isEqualTo("20261014100000");
        assertThat(query.get("vnp_ExpireDate")).isEqualTo("20261014101500");
        assertThat(fixed.verifySignature(query)).isTrue();
    }

    @Test
    void orderInfoIsSentWithoutDiacritics() {
        Map<String, String> query = queryOf(vnPay.buildPaymentUrl(79000, tx.getId().toString(),
                "Nâng cấp gói 1 tháng Premium (Đặc biệt)!", "10.0.0.1"));
        assertThat(query.get("vnp_OrderInfo")).isEqualTo("Nang cap goi 1 thang Premium Dac biet");
    }

    // ---- IPN / return processing ----

    @Test
    void validPaymentActivatesSubscription() {
        VnpayResult r = service.processVnpayResult(vnpayParams(7_900_000L, "00", "00"));

        assertThat(r.rspCode()).isEqualTo("00");
        assertThat(tx.getStatus()).isEqualTo("success");
        verify(subscriptionRepository).save(any(Subscription.class));
        verify(redisTemplate).delete("premium:" + tx.getUserId());
    }

    @Test
    void invalidSignatureReturns97AndChangesNothing() {
        Map<String, String> p = vnpayParams(7_900_000L, "00", "00");
        p.put("vnp_SecureHash", "deadbeef");

        assertThat(service.processVnpayResult(p).rspCode()).isEqualTo("97");
        assertThat(tx.getStatus()).isEqualTo("pending");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void amountMismatchMarksFailedAndReturns04() {
        VnpayResult r = service.processVnpayResult(vnpayParams(100L, "00", "00"));

        assertThat(r.rspCode()).isEqualTo("04");
        assertThat(tx.getStatus()).isEqualTo("failed");
        verify(subscriptionRepository, never()).save(any());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void secondIpnForSameOrderReturns02() {
        Map<String, String> p = vnpayParams(7_900_000L, "00", "00");
        assertThat(service.processVnpayResult(p).rspCode()).isEqualTo("00");
        assertThat(service.processVnpayResult(p).rspCode()).isEqualTo("02");

        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    void unknownOrderReturns01() {
        Map<String, String> p = vnpayParams(7_900_000L, "00", "00");
        p.put("vnp_TxnRef", UUID.randomUUID().toString());
        p.put("vnp_SecureHash", vnPay.sign(p));
        when(transactionRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());

        assertThat(service.processVnpayResult(p).rspCode()).isEqualTo("01");
    }

    @Test
    void declinedPaymentMarksFailedButAcknowledges() {
        VnpayResult r = service.processVnpayResult(vnpayParams(7_900_000L, "24", "02"));

        assertThat(r.rspCode()).isEqualTo("00");
        assertThat(tx.getStatus()).isEqualTo("failed");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void returnUrlRedirectsByOutcome() {
        assertThat(service.handlePaymentCallback(vnpayParams(7_900_000L, "00", "00"))).isEqualTo("/subscription/success");
        // replay after IPN already processed it
        assertThat(service.handlePaymentCallback(vnpayParams(7_900_000L, "00", "00"))).isEqualTo("/subscription/success");
    }

    @Test
    void returnUrlWithWrongAmountRedirectsToFailed() {
        assertThat(service.handlePaymentCallback(vnpayParams(1L, "00", "00")))
                .isEqualTo("/subscription/failed?reason=invalid_amount");
        verify(emailService, never()).sendPaymentConfirmation(any(), any(), any());
    }
}
