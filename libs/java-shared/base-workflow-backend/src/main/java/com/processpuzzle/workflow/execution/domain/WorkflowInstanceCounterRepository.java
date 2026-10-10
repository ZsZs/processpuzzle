package com.processpuzzle.workflow.execution.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowInstanceCounterRepository extends JpaRepository<WorkflowInstanceCounter, String> {

    /** The organization's counter, locked until the calling transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkflowInstanceCounter> findByOrgKey(String orgKey);

    /**
     * Every instance-number counter row of a tenant in one statement, for erasing the tenant. A bulk delete, so it
     * neither loads the rows nor checks their version.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from WorkflowInstanceCounter c where c.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
