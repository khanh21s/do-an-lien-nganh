package com.parroto.subscription.repository;

import com.parroto.subscription.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, String> {
}
