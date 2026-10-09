package com.processpuzzle.event.domain;

import com.processpuzzle.shared.event.PlatformEventAction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every finder is scoped by {@code orgKey}, so an unscoped read of another tenant's row is not expressible by accident. */
public interface EventDefinitionRepository extends JpaRepository<EventDefinition, EventDefinitionKey> {

    Optional<EventDefinition> findByOrgKeyAndId(String orgKey, String id);

    boolean existsByOrgKeyAndId(String orgKey, String id);

    List<EventDefinition> findByOrgKeyOrderByIdAsc(String orgKey);

    /** The SYSTEM candidates for a fact; the state filter is applied by {@link EventDefinition#matches}. */
    List<EventDefinition> findByOrgKeyAndKindAndSubjectTypeAndAction(
            String orgKey, EventKind kind, String subjectType, PlatformEventAction action);
}
