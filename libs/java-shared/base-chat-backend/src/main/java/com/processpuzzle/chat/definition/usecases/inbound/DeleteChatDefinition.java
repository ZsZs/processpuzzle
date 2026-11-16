package com.processpuzzle.chat.definition.usecases.inbound;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.usecases.exception.ChatDefinitionNotFoundException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes the draft of a chat definition. The released row stays until the next release, which removes it — a
 * conversation of a definition that is no longer released simply cannot be reached, like any chat whose definition
 * was withdrawn. Not refused while conversations exist: base-chat does not own what a subject's people said, and
 * they are kept until retention or an erasure removes them.
 */
@Service
@Transactional
public class DeleteChatDefinition {

    private final ChatDefinitionRepository repository;
    private final OrganizationGuard guard;

    public DeleteChatDefinition(ChatDefinitionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public void delete(String orgKey, String definitionKey) {
        guard.requireDesign(orgKey);
        ChatDefinition draft = repository.findByOrgKeyAndDefinitionKeyAndChannel(orgKey, definitionKey, Channel.DRAFT)
                .orElseThrow(() -> new ChatDefinitionNotFoundException(orgKey, definitionKey));
        repository.delete(draft);
    }
}
