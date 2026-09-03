package com.processpuzzle.platformadmin.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface UsageRecordRepository
        extends JpaRepository<UsageRecord, String>, JpaSpecificationExecutor<UsageRecord> {

    /** Everything measured for a tenant in the period covering {@code at}. */
    List<UsageRecord> findByOrgKeyAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
            String orgKey, Instant at, Instant sameAt);
}
