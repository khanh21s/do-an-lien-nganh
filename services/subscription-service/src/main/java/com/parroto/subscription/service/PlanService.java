package com.parroto.subscription.service;

import com.parroto.subscription.dto.PlanResponse;
import com.parroto.subscription.dto.PlansListResponse;
import com.parroto.subscription.entity.Plan;
import com.parroto.common.exception.ApiException;
import com.parroto.subscription.repository.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlanService {

    static final String PLAN_NOT_ON_SALE = "Gói này hiện không còn bán";

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public PlansListResponse listPlans() {
        List<PlanResponse> plans = planRepository.findByIsActiveTrueOrderByDurationDaysAscPriceAsc().stream()
                .map(PlanResponse::from)
                .toList();
        return new PlansListResponse(plans);
    }

    @Transactional(readOnly = true)
    public Plan getPlanOrThrow(String planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> ApiException.notFound("Plan not found"));
    }

    /** Like {@link #getPlanOrThrow} but refuses a disabled plan (new payments, code generation/redemption). */
    @Transactional(readOnly = true)
    public Plan getActivePlanOrThrow(String planId) {
        Plan plan = getPlanOrThrow(planId);
        requireOnSale(plan);
        return plan;
    }

    public static void requireOnSale(Plan plan) {
        if (!plan.isActive()) {
            throw ApiException.badRequest(PLAN_NOT_ON_SALE);
        }
    }
}
