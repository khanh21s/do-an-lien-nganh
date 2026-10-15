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
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/** T14-SUB: admin transaction list, revenue/subscriber stats and bulk premium status lookup. */
@Service
public class AdminReportService {

    static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int REVENUE_DAYS = 30;
    static final int MAX_USER_IDS = 100;
    private static final Set<String> STATUSES = Set.of("pending", "success", "failed");

    private final PaymentTransactionRepository transactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminReportService(PaymentTransactionRepository transactionRepository,
                              SubscriptionRepository subscriptionRepository, PlanRepository planRepository) {
        this(transactionRepository, subscriptionRepository, planRepository, Clock.system(APP_ZONE));
    }

    AdminReportService(PaymentTransactionRepository transactionRepository,
                       SubscriptionRepository subscriptionRepository, PlanRepository planRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.clock = clock;
    }

    // ---- transactions ----

    /**
     * @param status all|pending|success|failed (null/blank = all)
     * @param method exact payment method (vnpay, momo, gift_code…); null/blank/"all" = any
     * @param q      substring of {@code transactionId} (case-insensitive) or an exact {@code userId}
     * @param from   {@code YYYY-MM-DD}, inclusive, Vietnam time
     * @param to     {@code YYYY-MM-DD}, inclusive, Vietnam time
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionResponse> listTransactions(String status, String method, String q,
                                                                   String from, String to, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Specification<PaymentTransaction> spec = transactionFilter(status, method, q, from, to);
        Page<PaymentTransaction> result = transactionRepository.findAll(spec,
                PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt")));

        Map<String, String> planNames = planRepository.findAll().stream()
                .collect(Collectors.toMap(Plan::getId, Plan::getName, (a, b) -> a));
        Page<AdminTransactionResponse> mapped = result.map(tx -> AdminTransactionResponse.from(tx,
                tx.getPlanId() == null ? null : planNames.get(tx.getPlanId())));
        return PageResponse.of(mapped, safePage, safeLimit);
    }

    /** Validates the filter parameters (400 on bad input) and builds the JPA specification. */
    static Specification<PaymentTransaction> transactionFilter(String status, String method, String q,
                                                               String from, String to) {
        String normalizedStatus = blankToNull(status);
        if (normalizedStatus != null) {
            normalizedStatus = normalizedStatus.toLowerCase(Locale.ROOT);
            if ("all".equals(normalizedStatus)) {
                normalizedStatus = null;
            } else if (!STATUSES.contains(normalizedStatus)) {
                throw ApiException.badRequest("Trạng thái không hợp lệ (all|pending|success|failed)");
            }
        }
        String normalizedMethod = blankToNull(method);
        if (normalizedMethod != null) {
            normalizedMethod = normalizedMethod.toLowerCase(Locale.ROOT);
            if ("all".equals(normalizedMethod)) {
                normalizedMethod = null;
            }
        }
        String query = blankToNull(q);
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw ApiException.badRequest("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
        }
        OffsetDateTime fromTime = fromDate == null ? null : startOfDay(fromDate);
        OffsetDateTime toTimeExclusive = toDate == null ? null : startOfDay(toDate.plusDays(1));
        UUID queryUuid = query == null ? null : parseUuid(query);

        String st = normalizedStatus;
        String me = normalizedMethod;
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (st != null) {
                predicates.add(cb.equal(root.get("status"), st));
            }
            if (me != null) {
                predicates.add(cb.equal(cb.lower(root.get("method")), me));
            }
            if (query != null) {
                Predicate byTxn = cb.like(cb.lower(root.get("transactionId")),
                        "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%", '\\');
                predicates.add(queryUuid == null ? byTxn : cb.or(byTxn, cb.equal(root.get("userId"), queryUuid)));
            }
            if (fromTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromTime));
            }
            if (toTimeExclusive != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), toTimeExclusive));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // ---- stats ----

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate today = now.atZoneSameInstant(APP_ZONE).toLocalDate();
        LocalDate firstDay = today.minusDays(REVENUE_DAYS - 1);
        List<PaymentTransaction> recent = transactionRepository.findPaidSince(startOfDay(firstDay));
        List<AdminStatsResponse.RevenueDay> byDay = revenueByDay(recent, today);
        long last30 = byDay.stream().mapToLong(AdminStatsResponse.RevenueDay::amount).sum();
        return new AdminStatsResponse(
                subscriptionRepository.countActiveSubscribers(now),
                transactionRepository.sumPaidRevenue(),
                last30,
                transactionRepository.countPaid(),
                transactionRepository.countByStatus(SubscriptionService.STATUS_PENDING),
                byDay);
    }

    /**
     * Buckets paid transactions into the {@value #REVENUE_DAYS} Vietnam-time days ending {@code today}
     * (oldest first). Always returns exactly {@value #REVENUE_DAYS} entries; days without revenue are 0.
     * Defensive: re-applies the revenue rule (success, not gift_code) and ignores out-of-window rows.
     */
    static List<AdminStatsResponse.RevenueDay> revenueByDay(Collection<PaymentTransaction> transactions, LocalDate today) {
        LocalDate firstDay = today.minusDays(REVENUE_DAYS - 1);
        Map<LocalDate, Long> sums = new TreeMap<>();
        for (int i = 0; i < REVENUE_DAYS; i++) {
            sums.put(firstDay.plusDays(i), 0L);
        }
        for (PaymentTransaction tx : transactions) {
            if (!isPaid(tx) || tx.getCreatedAt() == null) {
                continue;
            }
            LocalDate day = tx.getCreatedAt().atZoneSameInstant(APP_ZONE).toLocalDate();
            sums.computeIfPresent(day, (d, sum) -> sum + tx.getAmount());
        }
        return sums.entrySet().stream()
                .map(e -> new AdminStatsResponse.RevenueDay(e.getKey().toString(), e.getValue()))
                .toList();
    }

    static boolean isPaid(PaymentTransaction tx) {
        return SubscriptionService.STATUS_SUCCESS.equals(tx.getStatus())
                && !"gift_code".equals(tx.getMethod())
                && tx.getAmount() != null;
    }

    // ---- premium status of several users ----

    /** One entry per distinct requested user, in request order. */
    @Transactional(readOnly = true)
    public List<UserPremiumStatusResponse> userStatus(List<UUID> userIds) {
        if (userIds == null) {
            throw ApiException.badRequest("userIds là bắt buộc");
        }
        List<UUID> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() > MAX_USER_IDS) {
            throw ApiException.badRequest("Tối đa 100 userIds mỗi lần");
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<UUID, Subscription> latest = new HashMap<>();
        for (Subscription s : subscriptionRepository.findByUserIdInAndStatusAndEndAtAfter(ids, "active", now)) {
            latest.merge(s.getUserId(), s, (a, b) -> a.getEndAt().isAfter(b.getEndAt()) ? a : b);
        }
        return ids.stream().map(id -> {
            Subscription s = latest.get(id);
            return s == null
                    ? new UserPremiumStatusResponse(id, false, null, null)
                    : new UserPremiumStatusResponse(id, true, s.getPlan().getId(), s.getEndAt());
        }).toList();
    }

    // ---- helpers ----

    static OffsetDateTime startOfDay(LocalDate day) {
        return day.atStartOfDay(APP_ZONE).toOffsetDateTime();
    }

    static LocalDate parseDate(String value) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Ngày không hợp lệ (định dạng YYYY-MM-DD)");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
