package com.parroto.subscription.repository;

import com.parroto.subscription.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, String> {

    /** Public catalogue: only plans still on sale. */
    List<Plan> findByIsActiveTrueOrderByDurationDaysAscPriceAsc();

    /** Admin list: every plan, including disabled ones. */
    List<Plan> findAllByOrderByDurationDaysAscPriceAsc();
}
