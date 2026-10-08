package com.processpuzzle.workflow.execution.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface WorkflowInstanceCounterRepository extends JpaRepository<WorkflowInstanceCounter, String> {

    /** The organization's counter, locked until the calling transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkflowInstanceCounter> findByOrgKey(String orgKey);
}
