package com.processpuzzle.chat.definition.domain;

import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder is scoped by {@code orgKey}, so an unscoped read of another tenant's row is not expressible by accident. */
public interface ChatDefinitionRepository extends JpaRepository<ChatDefinition, ChatDefinitionKey> {

    Optional<ChatDefinition> findByOrgKeyAndDefinitionKeyAndChannel(String orgKey, String definitionKey, Channel channel);

    List<ChatDefinition> findByOrgKeyAndChannelOrderByDefinitionKeyAsc(String orgKey, Channel channel);

    /** Every definition of every tenant on the channel; retention reads the period of each. */
    List<ChatDefinition> findByChannel(Channel channel);

    /** Every definition of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ChatDefinition d where d.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
