package com.processpuzzle.event.adapter.inbound;

import com.processpuzzle.event.api.EventDefinitionsApi;
import com.processpuzzle.event.model.EventDefinition;
import com.processpuzzle.event.model.EventDefinitionInput;
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

    public EventDefinitionsEndpoint(CreateEventDefinition createEventDefinition,
                                    ReplaceEventDefinition replaceEventDefinition,
                                    DeleteEventDefinition deleteEventDefinition,
                                    FindEventDefinition findEventDefinition,
                                    ImportEventDefinitions importEventDefinitions,
                                    EventDefinitionMapper mapper) {
        this.createEventDefinition = createEventDefinition;
        this.replaceEventDefinition = replaceEventDefinition;
        this.deleteEventDefinition = deleteEventDefinition;
        this.findEventDefinition = findEventDefinition;
        this.importEventDefinitions = importEventDefinitions;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<EventDefinition>> listEventDefinitions(String orgKey) {
        return ResponseEntity.ok(findEventDefinition.findAll(orgKey).stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<EventDefinition> createEventDefinition(String orgKey, EventDefinitionInput input) {
        var created = createEventDefinition.create(orgKey, mapper.toDomain(input));
        return new ResponseEntity<>(mapper.toModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<EventDefinition> getEventDefinition(String orgKey, String eventDefinitionId) {
        return ResponseEntity.ok(mapper.toModel(findEventDefinition.find(orgKey, eventDefinitionId)));
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
