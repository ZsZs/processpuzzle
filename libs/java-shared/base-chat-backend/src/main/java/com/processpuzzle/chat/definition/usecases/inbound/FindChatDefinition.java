package com.processpuzzle.chat.definition.usecases.inbound;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.usecases.exception.ChatDefinitionNotFoundException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindChatDefinition {

    private final ChatDefinitionRepository repository;
    private final OrganizationGuard guard;

    public FindChatDefinition(ChatDefinitionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    /**
     * A designer's question, about the draft or the released row. Reading the draft is designing, so it
     * needs the design right; the released row only membership.
     */
    public ChatDefinition find(String orgKey, String definitionKey, Channel channel) {
        requireRead(orgKey, channel);
        return repository.findByOrgKeyAndDefinitionKeyAndChannel(orgKey, definitionKey, channel)
                .orElseThrow(() -> new ChatDefinitionNotFoundException(orgKey, definitionKey));
    }

    public List<ChatDefinition> findAll(String orgKey, Channel channel) {
        requireRead(orgKey, channel);
        return repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(orgKey, channel);
    }

    /**
     * The released definition, or empty when the organization released none of that key: what the running
     * application reads. For callers that already checked the caller's membership.
     */
    public Optional<ChatDefinition> released(String orgKey, String definitionKey) {
        return definitionKey == null
                ? Optional.empty()
                : repository.findByOrgKeyAndDefinitionKeyAndChannel(orgKey, definitionKey, Channel.RELEASED);
    }

    private void requireRead(String orgKey, Channel channel) {
        if (channel == Channel.DRAFT) {
            guard.requireDesign(orgKey);
        } else {
            guard.requireAccess(orgKey);
        }
    }
}
