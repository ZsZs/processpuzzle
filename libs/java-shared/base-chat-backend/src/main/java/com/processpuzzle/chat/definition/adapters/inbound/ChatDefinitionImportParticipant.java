package com.processpuzzle.chat.definition.adapters.inbound;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.usecases.inbound.ImportChatDefinitions;
import com.processpuzzle.chat.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Imports a Business Starter's chat files — the {@code chat-definitions:} format of the seed files — through
 * {@link ImportChatDefinitions}. The key is the definition key.
 *
 * <p>The import writes drafts only: a starter is released as a whole afterwards, by the same release that releases
 * everything else it brought, so that its chats go live together with the entities and states they name.
 */
@Component
public class ChatDefinitionImportParticipant implements DefinitionImportParticipant {

    private final ImportChatDefinitions importer;
    private final ChatDefinitionRepository repository;

    public ChatDefinitionImportParticipant(ImportChatDefinitions importer, ChatDefinitionRepository repository) {
        this.importer = importer;
        this.repository = repository;
    }

    @Override
    public String kind() {
        return DefinitionKinds.CHAT;
    }

    @Override
    public int order() {
        return DefinitionKinds.CHAT_ORDER;
    }

    @Override
    public ParticipantResult apply(String orgKey, String fileName, byte[] yaml) {
        ImportOutcome outcome;
        try {
            outcome = importer.execute(orgKey, new ByteArrayInputStream(yaml), false);
        } catch (IOException | RuntimeException e) {
            return ParticipantResult.rejected(List.of(fileName + ": not a readable chat definition file: " + e.getMessage()));
        }
        if (!outcome.errors().isEmpty()) {
            return ParticipantResult.rejected(outcome.errors().stream().map(error -> fileName + ": " + error).toList());
        }
        List<ImportedItem> items = outcome.written().stream()
                .map(key -> new ImportedItem(key, outcome.updatedKeys().contains(key) ? ImportedItem.Action.UPDATE : ImportedItem.Action.CREATE))
                .toList();
        return new ParticipantResult(items, List.of());
    }

    @Override
    public List<String> removeAll(String orgKey) {
        return importer.removeAll(orgKey);
    }

    @Override
    public Map<String, String> fingerprints(String orgKey, Set<String> keys) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        for (ChatDefinition draft : repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(orgKey, Channel.DRAFT)) {
            if (keys.contains(draft.getDefinitionKey())) {
                fingerprints.put(draft.getDefinitionKey(), draft.fingerprint());
            }
        }
        return fingerprints;
    }
}
