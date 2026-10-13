package com.processpuzzle.event.adapter.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.event.api.EventDefinitionsApi;
import com.processpuzzle.event.model.EventDefinition;
import com.processpuzzle.event.model.EventDefinitionInput;
import com.processpuzzle.event.model.ReleaseState;
import com.processpuzzle.event.usecase.CreateEventDefinition;
import com.processpuzzle.event.usecase.DeleteEventDefinition;
import com.processpuzzle.event.usecase.FindEventDefinition;
import com.processpuzzle.event.usecase.ImportEventDefinitions;
import com.processpuzzle.event.usecase.ReplaceEventDefinition;
import com.processpuzzle.shared.model.ImportResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Implements the generated {@code EventDefinitionsApi}. */
@RestController
public class EventDefinitionsEndpoint implements EventDefinitionsApi {

    private final CreateEventDefinition createEventDefinition;
    private final ReplaceEventDefinition replaceEventDefinition;
    private final DeleteEventDefinition deleteEventDefinition;
    private final FindEventDefinition findEventDefinition;
    private final ImportEventDefinitions importEventDefinitions;
    private final EventDefinitionMapper mapper;
    private final EventDefinitionReleaseParticipant releaseParticipant;

    public EventDefinitionsEndpoint(CreateEventDefinition createEventDefinition,
                                    ReplaceEventDefinition replaceEventDefinition,
                                    DeleteEventDefinition deleteEventDefinition,
                                    FindEventDefinition findEventDefinition,
                                    ImportEventDefinitions importEventDefinitions,
                                    EventDefinitionMapper mapper,
                                    EventDefinitionReleaseParticipant releaseParticipant) {
        this.createEventDefinition = createEventDefinition;
        this.replaceEventDefinition = replaceEventDefinition;
        this.deleteEventDefinition = deleteEventDefinition;
        this.findEventDefinition = findEventDefinition;
        this.importEventDefinitions = importEventDefinitions;
        this.mapper = mapper;
        this.releaseParticipant = releaseParticipant;
    }

    @Override
    public ResponseEntity<List<EventDefinition>> listEventDefinitions(String orgKey, String revision) {
        Channel channel = Channel.ofRevision(revision);
        List<EventDefinition> definitions = findEventDefinition.findAll(orgKey, channel).stream().map(mapper::toModel).toList();
        if (channel == Channel.DRAFT) {
            Map<String, com.processpuzzle.core.definition.ReleaseState> states =
                    com.processpuzzle.core.definition.ReleaseState.ofDrafts(releaseParticipant, orgKey);
            definitions.forEach(definition -> definition.setReleaseState(releaseStateOf(states, definition.getId())));
        }
        return ResponseEntity.ok(definitions);
    }

    @Override
    public ResponseEntity<EventDefinition> createEventDefinition(String orgKey, EventDefinitionInput input) {
        var created = createEventDefinition.create(orgKey, mapper.toDomain(input));
        return new ResponseEntity<>(mapper.toModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<EventDefinition> getEventDefinition(String orgKey, String eventDefinitionId, String revision) {
        Channel channel = Channel.ofRevision(revision);
        EventDefinition definition = mapper.toModel(findEventDefinition.find(orgKey, eventDefinitionId, channel));
        if (channel == Channel.DRAFT) {
            definition.setReleaseState(releaseStateOf(
                    com.processpuzzle.core.definition.ReleaseState.ofDrafts(releaseParticipant, orgKey), eventDefinitionId));
        }
        return ResponseEntity.ok(definition);
    }

    /** A draft's release state, as the designer's list and form show it; only drafts have one. */
    private static ReleaseState releaseStateOf(Map<String, com.processpuzzle.core.definition.ReleaseState> states, String id) {
        com.processpuzzle.core.definition.ReleaseState state = states.get(id);
        return state == null ? null : ReleaseState.fromValue(state.name());
    }

    @Override
    public ResponseEntity<EventDefinition> updateEventDefinition(String orgKey, String eventDefinitionId,
                                                                 EventDefinitionInput input) {
        var replaced = replaceEventDefinition.replace(orgKey, eventDefinitionId, mapper.toDomain(input));
        return ResponseEntity.ok(mapper.toModel(replaced));
    }

    @Override
    public ResponseEntity<Void> deleteEventDefinition(String orgKey, String eventDefinitionId) {
        deleteEventDefinition.delete(orgKey, eventDefinitionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ImportResult> importEventDefinitions(String orgKey, MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return ResponseEntity.ok(mapper.toImportResult(importEventDefinitions.execute(orgKey, input)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
