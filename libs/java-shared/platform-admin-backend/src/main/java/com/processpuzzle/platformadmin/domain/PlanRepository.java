package com.processpuzzle.platformadmin.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlanRepository extends JpaRepository<Plan, String>, JpaSpecificationExecutor<Plan> {
}
