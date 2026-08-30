package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

interface PlanRepository extends JpaRepository<Plan, String> {
}
