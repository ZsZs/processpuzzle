package com.processpuzzle.chat.definition.adapters.inbound;

import com.processpuzzle.chat.api.ChatDefinitionsApi;
import com.processpuzzle.chat.definition.usecases.exception.InvalidRevisionException;
import com.processpuzzle.chat.definition.usecases.inbound.FindChatDefinition;
import com.processpuzzle.chat.definition.usecases.inbound.SaveChatDefinition;
import com.processpuzzle.chat.model.ChatDefinition;
import com.processpuzzle.chat.model.ChatDefinitionInput;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated {@code ChatDefinitionsApi}. */
@RestController
public class ChatDefinitionEndpoint implements ChatDefinitionsApi {

    private final FindChatDefinition find;
    private final SaveChatDefinition save;
    private final ChatDefinitionMapper mapper;

    public ChatDefinitionEndpoint(FindChatDefinition find, SaveChatDefinition save, ChatDefinitionMapper mapper) {
        this.find = find;
        this.save = save;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<ChatDefinition>> listChatDefinitions(String orgKey, String revision) {
        return ResponseEntity.ok(find.findAll(orgKey, channelOf(revision)).stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<ChatDefinition> getChatDefinition(String orgKey, String definitionKey, String revision) {
        return ResponseEntity.ok(mapper.toModel(find.find(orgKey, definitionKey, channelOf(revision))));
    }

    @Override
    public ResponseEntity<ChatDefinition> saveChatDefinition(String orgKey, String definitionKey, ChatDefinitionInput input) {
        return ResponseEntity.ok(mapper.toModel(save.save(orgKey, definitionKey, mapper.toDomain(input))));
    }

    /** The contract names {@code draft} and {@code released}; the generated parameter is a plain string. */
    private static Channel channelOf(String revision) {
        try {
            return Channel.ofRevision(revision);
        } catch (IllegalArgumentException e) {
            throw new InvalidRevisionException(e.getMessage());
        }
    }
}
