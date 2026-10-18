package com.processpuzzle.calculation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Scoped by {@code orgKey} like the definitions: another organization's run is not found. */
public interface CalculationRunRepository extends JpaRepository<CalculationRun, UUID> {

    @Modifying(flushAutomatically = true)
    @Query("delete from CalculationRun r where r.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);

    Optional<CalculationRun> findByIdAndOrgKey(UUID id, String orgKey);

    List<CalculationRun> findByOrgKeyOrderByStartedAtDesc(String orgKey);

    List<CalculationRun> findByOrgKeyAndCalculationIdOrderByStartedAtDesc(String orgKey, String calculationId);

    List<CalculationRun> findByOrgKeyAndSubjectIdOrderByStartedAtDesc(String orgKey, String subjectId);

    List<CalculationRun> findByOrgKeyAndCalculationIdAndSubjectIdOrderByStartedAtDesc(String orgKey, String calculationId,
                                                                                    String subjectId);

    Optional<CalculationRun> findFirstByOrgKeyAndCalculationIdAndSubjectIdAndStatusOrderByStartedAtDesc(
            String orgKey, String calculationId, String subjectId, RunStatus status);

    boolean existsByOrgKeyAndCalculationIdAndSubjectIdAndFrozenTrue(String orgKey, String calculationId, String subjectId);
}
