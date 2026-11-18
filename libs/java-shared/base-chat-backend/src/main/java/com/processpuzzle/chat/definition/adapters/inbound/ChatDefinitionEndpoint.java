package com.processpuzzle.chat.definition.adapters.inbound;

import com.processpuzzle.chat.api.ChatDefinitionsApi;
import com.processpuzzle.chat.definition.usecases.exception.InvalidRevisionException;
import com.processpuzzle.chat.definition.usecases.inbound.DeleteChatDefinition;
import com.processpuzzle.chat.definition.usecases.inbound.FindChatDefinition;
import com.processpuzzle.chat.definition.usecases.inbound.SaveChatDefinition;
import com.processpuzzle.chat.model.ChatDefinition;
import com.processpuzzle.chat.model.ChatDefinitionInput;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.ReleaseState;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated {@code ChatDefinitionsApi}. */
@RestController
public class ChatDefinitionEndpoint implements ChatDefinitionsApi {

    private final FindChatDefinition find;
    private final SaveChatDefinition save;
    private final DeleteChatDefinition delete;
    private final ChatDefinitionMapper mapper;
    private final ChatDefinitionReleaseParticipant releaseParticipant;

    public ChatDefinitionEndpoint(FindChatDefinition find, SaveChatDefinition save, DeleteChatDefinition delete,
                                  ChatDefinitionMapper mapper, ChatDefinitionReleaseParticipant releaseParticipant) {
        this.find = find;
        this.save = save;
        this.delete = delete;
        this.mapper = mapper;
        this.releaseParticipant = releaseParticipant;
    }

    @Override
    public ResponseEntity<List<ChatDefinition>> listChatDefinitions(String orgKey, String revision) {
        Channel channel = channelOf(revision);
        Map<String, ReleaseState> states = releaseStates(orgKey, channel);
        return ResponseEntity.ok(find.findAll(orgKey, channel).stream()
                .map(definition -> mapper.toModel(definition, states.get(definition.getDefinitionKey()))).toList());
    }

    @Override
    public ResponseEntity<ChatDefinition> getChatDefinition(String orgKey, String definitionKey, String revision) {
        Channel channel = channelOf(revision);
        com.processpuzzle.chat.definition.domain.ChatDefinition definition = find.find(orgKey, definitionKey, channel);
        return ResponseEntity.ok(mapper.toModel(definition, releaseStates(orgKey, channel).get(definitionKey)));
    }

    @Override
    public ResponseEntity<ChatDefinition> saveChatDefinition(String orgKey, String definitionKey, ChatDefinitionInput input) {
        com.processpuzzle.chat.definition.domain.ChatDefinition saved = save.save(orgKey, definitionKey, mapper.toDomain(input));
        return ResponseEntity.ok(mapper.toModel(saved, releaseStates(orgKey, Channel.DRAFT).get(definitionKey)));
    }

    @Override
    public ResponseEntity<Void> deleteChatDefinition(String orgKey, String definitionKey) {
        delete.delete(orgKey, definitionKey);
        return ResponseEntity.noContent().build();
    }

    /** Only a draft has a release state; the released row is what the release produced. */
    private Map<String, ReleaseState> releaseStates(String orgKey, Channel channel) {
        return channel == Channel.DRAFT ? ReleaseState.ofDrafts(releaseParticipant, orgKey) : Map.of();
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
