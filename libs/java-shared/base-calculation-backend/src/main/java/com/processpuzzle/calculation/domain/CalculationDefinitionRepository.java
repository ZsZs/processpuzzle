package com.processpuzzle.calculation.domain;

import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder is scoped by {@code orgKey}, so an unscoped read of another tenant's row is not expressible by accident. */
public interface CalculationDefinitionRepository extends JpaRepository<CalculationDefinition, CalculationDefinitionKey> {

    @Modifying(flushAutomatically = true)
    @Query("delete from CalculationDefinition c where c.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);

    Optional<CalculationDefinition> findByOrgKeyAndIdAndChannel(String orgKey, String id, Channel channel);

    boolean existsByOrgKeyAndIdAndChannel(String orgKey, String id, Channel channel);

    List<CalculationDefinition> findByOrgKeyAndChannelOrderByIdAsc(String orgKey, Channel channel);

    List<CalculationDefinition> findByOrgKeyAndChannelAndOutputEntity(String orgKey, Channel channel, String outputEntity);
}
