package com.processpuzzle.ai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationAiSubscriptionRepository extends JpaRepository<OrganizationAiSubscription, String> {

    @Modifying
    @Query("delete from OrganizationAiSubscription s where s.orgKey = :orgKey")
    int deleteByOrganization(@Param("orgKey") String orgKey);
}
