package com.parroto.subscription.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.parroto.subscription.dto.AdminPlanRequest;
import com.parroto.subscription.dto.AdminPlanResponse;
import com.parroto.subscription.entity.Plan;
import com.parroto.subscription.exception.ApiException;
import com.parroto.subscription.repository.PlanRepository;
import com.parroto.subscription.repository.SubscriptionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T14-SUB: admin CRUD for plans. Plans are never hard-deleted (subscriptions, transactions and
 * activation codes reference them); they are taken off sale with {@code isActive=false}.
 *
 * <p>No cache to evict: the public plan list is read straight from the DB, and plan edits do not
 * change anyone's premium status ({@code premium:{userId}} depends on subscriptions only).
 */
@Service
public class AdminPlanService {

    static final String DUPLICATE_ID = "Mã gói đã tồn tại";
    static final String NOT_FOUND = "Không tìm thấy gói";

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;

    public AdminPlanService(PlanRepository planRepository, SubscriptionRepository subscriptionRepository) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminPlanResponse> listPlans() {
        Map<String, Long> subscribers = new HashMap<>();
        for (Object[] row : subscriptionRepository.countActiveSubscribersByPlan(OffsetDateTime.now())) {
            subscribers.put((String) row[0], ((Number) row[1]).longValue());
        }
        return planRepository.findAllByOrderByDurationDaysAscPriceAsc().stream()
                .map(p -> AdminPlanResponse.from(p, subscribers.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Transactional
    public AdminPlanResponse createPlan(AdminPlanRequest request) {
        String id = request.id() == null ? null : request.id().trim();
        if (id == null || id.isEmpty()) {
            throw ApiException.badRequest("Mã gói là bắt buộc");
        }
        if (!id.matches(AdminPlanRequest.ID_PATTERN)) {
            throw ApiException.badRequest("Mã gói chỉ gồm a-z, 0-9, dấu '-' và dài 3–50 ký tự");
        }
        if (planRepository.existsById(id)) {
            throw ApiException.conflict(DUPLICATE_ID);
        }
        Plan plan = Plan.builder().id(id).build();
        apply(plan, request, true);
        try {
            plan = planRepository.saveAndFlush(plan);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent create of the same id.
            throw ApiException.conflict(DUPLICATE_ID);
        }
        return AdminPlanResponse.from(plan, 0);
    }

    @Transactional
    public AdminPlanResponse updatePlan(String id, AdminPlanRequest request) {
        Plan plan = planRepository.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        apply(plan, request, false);
        plan = planRepository.saveAndFlush(plan);
        return AdminPlanResponse.from(plan,
                subscriptionRepository.countActiveSubscribersOfPlan(plan.getId(), OffsetDateTime.now()));
    }

    /** Copies validated request fields onto the entity (bean validation already ran in the controller). */
    static void apply(Plan plan, AdminPlanRequest request, boolean creating) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > 255) {
            throw ApiException.badRequest("Tên gói phải từ 1 đến 255 ký tự");
        }
        if (request.durationDays() == null || request.durationDays() < 1 || request.durationDays() > 3650) {
            throw ApiException.badRequest("Thời hạn phải từ 1 đến 3650 ngày");
        }
        if (request.price() == null || request.price() < 1000) {
            throw ApiException.badRequest("Giá tối thiểu là 1.000 ₫");
        }
        plan.setName(name);
        plan.setDurationDays(request.durationDays());
        plan.setPrice(request.price());
        plan.setFeatures(featuresNode(request.features()));
        if (request.isActive() != null) {
            plan.setActive(request.isActive());
        } else if (creating) {
            plan.setActive(true);
        }
    }

    /** Trims entries and drops blank ones; null = empty array. */
    static ArrayNode featuresNode(List<String> features) {
        ArrayNode node = JsonNodeFactory.instance.arrayNode();
        if (features != null) {
            features.stream()
                    .filter(f -> f != null && !f.isBlank())
                    .map(String::trim)
                    .forEach(node::add);
        }
        return node;
    }
}
