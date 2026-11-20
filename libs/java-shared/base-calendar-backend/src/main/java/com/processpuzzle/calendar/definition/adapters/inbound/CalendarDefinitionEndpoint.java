package com.processpuzzle.calendar.definition.adapters.inbound;

import com.processpuzzle.calendar.api.CalendarDefinitionsApi;
import com.processpuzzle.calendar.definition.usecases.exception.InvalidRevisionException;
import com.processpuzzle.calendar.definition.usecases.inbound.DeleteCalendarDefinition;
import com.processpuzzle.calendar.definition.usecases.inbound.FindCalendarDefinition;
import com.processpuzzle.calendar.definition.usecases.inbound.SaveCalendarDefinition;
import com.processpuzzle.calendar.model.CalendarDefinition;
import com.processpuzzle.calendar.model.CalendarDefinitionInput;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@code CalendarDefinitionsApi}. The release state of a draft is not reported yet: it
 * arrives with the release participant.
 */
@RestController
public class CalendarDefinitionEndpoint implements CalendarDefinitionsApi {

    private final FindCalendarDefinition find;
    private final SaveCalendarDefinition save;
    private final DeleteCalendarDefinition delete;
    private final CalendarDefinitionMapper mapper;

    public CalendarDefinitionEndpoint(FindCalendarDefinition find, SaveCalendarDefinition save,
                                      DeleteCalendarDefinition delete, CalendarDefinitionMapper mapper) {
        this.find = find;
        this.save = save;
        this.delete = delete;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<CalendarDefinition>> listCalendarDefinitions(String orgKey, String revision) {
        return ResponseEntity.ok(find.findAll(orgKey, channelOf(revision)).stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<CalendarDefinition> getCalendarDefinition(String orgKey, String definitionKey, String revision) {
        return ResponseEntity.ok(mapper.toModel(find.find(orgKey, definitionKey, channelOf(revision))));
    }

    @Override
    public ResponseEntity<CalendarDefinition> saveCalendarDefinition(String orgKey, String definitionKey,
                                                                     CalendarDefinitionInput input) {
        return ResponseEntity.ok(mapper.toModel(save.save(orgKey, definitionKey, mapper.toDomain(input))));
    }

    @Override
    public ResponseEntity<Void> deleteCalendarDefinition(String orgKey, String definitionKey) {
        delete.delete(orgKey, definitionKey);
        return ResponseEntity.noContent().build();
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
