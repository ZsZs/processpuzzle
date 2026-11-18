package com.processpuzzle.chat.definition.usecases.inbound;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.usecases.exception.InvalidChatDefinitionException;
import com.processpuzzle.chat.definition.usecases.exception.StaleChatDefinitionException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the draft of a chat definition when its key is new, replaces it otherwise. A supplied
 * {@code version} that no longer matches is refused rather than overwritten; none overwrites
 * unconditionally. Authoring a definition is designing metadata, so it needs the design right, and the
 * roles the definition names are announced so the organization's role catalog contains them.
 */
@Service
@Transactional
public class SaveChatDefinition {

    private final ChatDefinitionRepository repository;
    private final OrganizationGuard guard;

    public SaveChatDefinition(ChatDefinitionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public ChatDefinition save(String orgKey, String definitionKey, ChatDefinition desiredState) {
        guard.requireDesign(orgKey);
        ChatDefinition definition = repository.findByOrgKeyAndDefinitionKeyAndChannel(orgKey, definitionKey, Channel.DRAFT)
                .map(existing -> replace(existing, desiredState))
                .orElseGet(() -> create(orgKey, definitionKey, desiredState));
        List<String> errors = definition.validate();
        if (!errors.isEmpty()) {
            throw new InvalidChatDefinitionException(definitionKey, errors);
        }
        ChatDefinition saved = repository.save(definition);
        guard.declareRoles(orgKey, "chat definition '%s'".formatted(definitionKey), saved.allRoles());
        return saved;
    }

    private static ChatDefinition replace(ChatDefinition existing, ChatDefinition desiredState) {
        if (desiredState.getVersion() != null && !desiredState.getVersion().equals(existing.getVersion())) {
            throw new StaleChatDefinitionException(existing.getDefinitionKey());
        }
        existing.replaceWith(desiredState);
        return existing;
    }

    private static ChatDefinition create(String orgKey, String definitionKey, ChatDefinition desiredState) {
        desiredState.setOrgKey(orgKey);
        desiredState.setDefinitionKey(definitionKey);
        desiredState.setChannel(Channel.DRAFT);
        desiredState.setVersion(null);
        return desiredState;
    }
}
