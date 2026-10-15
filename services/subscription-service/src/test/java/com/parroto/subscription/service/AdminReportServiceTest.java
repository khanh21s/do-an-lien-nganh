package com.parroto.subscription.service;

import com.parroto.subscription.dto.AdminStatsResponse;
import com.parroto.subscription.dto.AdminTransactionResponse;
import com.parroto.common.dto.PageResponse;
import com.parroto.subscription.dto.UserPremiumStatusResponse;
import com.parroto.subscription.entity.PaymentTransaction;
import com.parroto.subscription.entity.Plan;
import com.parroto.subscription.entity.Subscription;
import com.parroto.common.exception.ApiException;
import com.parroto.subscription.repository.PaymentTransactionRepository;
import com.parroto.subscription.repository.PlanRepository;
import com.parroto.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** T14-SUB: revenue stats, transaction filters and bulk premium status. */
class AdminReportServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 2026-10-13 10:00 in Vietnam. */
    private static final Instant NOW = LocalDateTime.of(2026, 10, 13, 10, 0).atZone(VN).toInstant();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    private PaymentTransactionRepository transactionRepository;
    private SubscriptionRepository subscriptionRepository;
    private PlanRepository planRepository;
    private AdminReportService service;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(PaymentTransactionRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        planRepository = mock(PlanRepository.class);
        service = new AdminReportService(transactionRepository, subscriptionRepository, planRepository,
                Clock.fixed(NOW, VN));
    }

    private static PaymentTransaction tx(String status, String method, long amount, OffsetDateTime at) {
        return PaymentTransaction.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).planId("plan-1month")
                .amount(amount).method(method).status(status).createdAt(at).build();
    }

    private static OffsetDateTime vn(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2026, month, day, hour, minute).atZone(VN).toOffsetDateTime();
    }

    // ---- revenue by day ----

    @Test
    void revenueByDayAlwaysHas30DaysEndingToday() {
        List<AdminStatsResponse.RevenueDay> days = AdminReportService.revenueByDay(List.of(), TODAY);

        assertThat(days).hasSize(30);
        assertThat(days.get(0).date()).isEqualTo("2026-09-14");
        assertThat(days.get(29).date()).isEqualTo("2026-10-13");
        assertThat(days).allSatisfy(d -> assertThat(d.amount()).isZero());
    }

    @Test
    void revenueByDayBucketsInVietnamTimeAndExcludesGiftCodesAndUnpaid() {
        List<PaymentTransaction> txs = new ArrayList<>(List.of(
                tx("success", "vnpay", 79000, vn(10, 13, 9, 0)),
                tx("success", "vnpay", 169000, vn(10, 13, 0, 5)),
                // 2026-10-11T17:30Z = 2026-10-12 00:30 in Vietnam -> counted on the 12th, not the 11th
                tx("success", "vnpay", 499000, OffsetDateTime.of(2026, 10, 11, 17, 30, 0, 0, ZoneOffset.UTC)),
                tx("success", "gift_code", 79000, vn(10, 13, 8, 0)),
                tx("failed", "vnpay", 79000, vn(10, 13, 8, 0)),
                tx("pending", "vnpay", 79000, vn(10, 13, 8, 0)),
                tx("success", "vnpay", 1000, vn(9, 14, 0, 0)),   // first day of the window
                tx("success", "vnpay", 5000, vn(9, 13, 23, 59)), // just outside
                tx("success", null, 2000, vn(10, 1, 12, 0))));   // legacy row without method still counts

        List<AdminStatsResponse.RevenueDay> days = AdminReportService.revenueByDay(txs, TODAY);

        assertThat(days).hasSize(30);
        assertThat(amountOn(days, "2026-10-13")).isEqualTo(248000);
        assertThat(amountOn(days, "2026-10-12")).isEqualTo(499000);
        assertThat(amountOn(days, "2026-10-11")).isZero();
        assertThat(amountOn(days, "2026-09-14")).isEqualTo(1000);
        assertThat(amountOn(days, "2026-10-01")).isEqualTo(2000);
        assertThat(days.stream().mapToLong(AdminStatsResponse.RevenueDay::amount).sum()).isEqualTo(750000);
    }

    private static long amountOn(List<AdminStatsResponse.RevenueDay> days, String date) {
        return days.stream().filter(d -> d.date().equals(date)).findFirst().orElseThrow().amount();
    }

    @Test
    void statsCombinesTotalsAndWindow() {
        OffsetDateTime windowStart = LocalDate.of(2026, 9, 14).atStartOfDay(VN).toOffsetDateTime();
        when(transactionRepository.findPaidSince(windowStart)).thenReturn(List.of(
                tx("success", "vnpay", 79000, vn(10, 13, 9, 0)),
                tx("success", "vnpay", 169000, vn(10, 2, 9, 0))));
        when(transactionRepository.sumPaidRevenue()).thenReturn(1_000_000L);
        when(transactionRepository.countPaid()).thenReturn(12L);
        when(transactionRepository.countByStatus("pending")).thenReturn(3L);
        when(subscriptionRepository.countActiveSubscribers(any())).thenReturn(5L);

        AdminStatsResponse stats = service.stats();

        assertThat(stats.activeSubscribers()).isEqualTo(5);
        assertThat(stats.revenueTotal()).isEqualTo(1_000_000L);
        assertThat(stats.revenueLast30Days()).isEqualTo(248000);
        assertThat(stats.successfulTransactions()).isEqualTo(12);
        assertThat(stats.pendingTransactions()).isEqualTo(3);
        assertThat(stats.revenueByDay()).hasSize(30);
        assertThat(stats.revenueByDay().get(29)).isEqualTo(new AdminStatsResponse.RevenueDay("2026-10-13", 79000));
    }

    @Test
    void paidRuleMatchesRepositoryQuery() {
        assertThat(PaymentTransactionRepository.PAID).contains("'success'").contains("'gift_code'");
        assertThat(AdminReportService.isPaid(tx("success", "vnpay", 1, null))).isTrue();
        assertThat(AdminReportService.isPaid(tx("success", "gift_code", 1, null))).isFalse();
        assertThat(AdminReportService.isPaid(tx("failed", "vnpay", 1, null))).isFalse();
    }

    // ---- transaction filters ----

    @Test
    void invalidFiltersAre400() {
        assertBadRequest(() -> AdminReportService.transactionFilter("refunded", null, null, null, null));
        assertBadRequest(() -> AdminReportService.transactionFilter("all", null, null, "13/10/2026", null));
        assertBadRequest(() -> AdminReportService.transactionFilter("all", null, null, null, "2026-13-01"));
        assertBadRequest(() -> AdminReportService.transactionFilter("all", null, null, "2026-10-13", "2026-10-01"));
    }

    @Test
    void validFiltersBuildASpecification() {
        assertThat(AdminReportService.transactionFilter("all", "all", "", "", "")).isNotNull();
        assertThat(AdminReportService.transactionFilter("SUCCESS", "vnpay", "abc", "2026-10-01", "2026-10-01")).isNotNull();
        assertThat(AdminReportService.transactionFilter(null, null, UUID.randomUUID().toString(), null, null)).isNotNull();
    }

    @Test
    void dateBoundariesUseVietnamMidnight() {
        assertThat(AdminReportService.startOfDay(LocalDate.of(2026, 10, 13)))
                .isEqualTo(OffsetDateTime.of(2026, 10, 12, 17, 0, 0, 0, ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(7)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactionsMapsPlanNameAndClampsPaging() {
        PaymentTransaction t = tx("success", "vnpay", 79000, vn(10, 13, 9, 0));
        t.setTransactionId("TXN-1");
        when(planRepository.findAll()).thenReturn(List.of(
                Plan.builder().id("plan-1month").name("1 tháng Premium").durationDays(30).price(79000L).build()));
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(t), inv.getArgument(1), 1));

        PageResponse<AdminTransactionResponse> page = service.listTransactions("all", null, null, null, null, 0, 500);

        assertThat(page.data()).hasSize(1);
        assertThat(page.data().get(0).planName()).isEqualTo("1 tháng Premium");
        assertThat(page.data().get(0).transactionId()).isEqualTo("TXN-1");
        assertThat(page.pagination().page()).isEqualTo(1);
        assertThat(page.pagination().limit()).isEqualTo(100);
        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 100,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
    }

    // ---- user status ----

    @Test
    void userStatusReturnsOneEntryPerUserInRequestOrder() {
        UUID premium = UUID.randomUUID();
        UUID free = UUID.randomUUID();
        Plan plan = Plan.builder().id("plan-1year").name("1 năm").durationDays(365).price(499000L).build();
        OffsetDateTime end1 = vn(11, 1, 0, 0);
        OffsetDateTime end2 = vn(12, 1, 0, 0);
        when(subscriptionRepository.findByUserIdInAndStatusAndEndAtAfter(anyCollection(), eq("active"), any()))
                .thenReturn(List.of(
                        Subscription.builder().userId(premium).plan(plan).endAt(end1).build(),
                        Subscription.builder().userId(premium).plan(plan).endAt(end2).build()));

        List<UserPremiumStatusResponse> result = service.userStatus(List.of(free, premium, free));

        assertThat(result).containsExactly(
                new UserPremiumStatusResponse(free, false, null, null),
                new UserPremiumStatusResponse(premium, true, "plan-1year", end2));
    }

    @Test
    void userStatusEmptyAndTooMany() {
        assertThat(service.userStatus(List.of())).isEmpty();
        verifyNoInteractions(subscriptionRepository);

        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            many.add(UUID.randomUUID());
        }
        assertBadRequest(() -> service.userStatus(many));
        assertThat(service.userStatus(Collections.nCopies(101, many.get(0)))).hasSize(1); // duplicates collapse
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
