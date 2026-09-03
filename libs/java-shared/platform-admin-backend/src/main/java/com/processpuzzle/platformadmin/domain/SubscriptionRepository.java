package com.processpuzzle.platformadmin.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SubscriptionRepository
        extends JpaRepository<Subscription, String>, JpaSpecificationExecutor<Subscription> {

    /**
     * The tenant's current subscription.
     *
     * <p>Ordered by period start descending and limited to one, rather than a unique constraint on
     * {@code orgKey}: a tenant that changes plan mid-term has two rows for a while, and the newest is
     * the one in force. A uniqueness constraint would have forced the history to be destroyed on every
     * plan change.
     */
    Optional<Subscription> findFirstByOrgKeyOrderByCurrentPeriodStartDesc(String orgKey);
}
