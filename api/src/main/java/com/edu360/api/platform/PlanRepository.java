package com.edu360.api.platform;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
    List<Plan> findByStatusOrderByPlanNameAsc(String status);
}
