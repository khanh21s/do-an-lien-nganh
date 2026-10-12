package com.parroto.subscription.service;

import com.parroto.subscription.dto.PlanResponse;
import com.parroto.subscription.dto.PlansListResponse;
import com.parroto.subscription.entity.Plan;
import com.parroto.subscription.exception.ApiException;
import com.parroto.subscription.repository.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlanService {

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public PlansListResponse listPlans() {
        List<PlanResponse> plans = planRepository.findAll().stream()
                .map(PlanResponse::from)
                .toList();
        return new PlansListResponse(plans);
    }

    @Transactional(readOnly = true)
    public Plan getPlanOrThrow(String planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> ApiException.notFound("Plan not found"));
    }
}
