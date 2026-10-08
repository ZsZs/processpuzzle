package com.processpuzzle.event.adapter.inbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.event.usecase.CreateEventDefinition;
import com.processpuzzle.event.usecase.DeleteEventDefinition;
import com.processpuzzle.event.usecase.FindEventDefinition;
import com.processpuzzle.event.usecase.ImportEventDefinitions;
import com.processpuzzle.event.usecase.ImportOutcome;
import com.processpuzzle.event.usecase.ReplaceEventDefinition;
import com.processpuzzle.event.usecase.exception.EventDefinitionAlreadyExistsException;
import com.processpuzzle.event.usecase.exception.EventDefinitionNotFoundException;
import com.processpuzzle.event.usecase.exception.InvalidEventDefinitionException;
import com.processpuzzle.event.usecase.exception.StaleEventDefinitionException;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EventDefinitionsEndpointTest {

    private static final String BASE = "/organizations/org-1/event-definitions";
    private static final String BODY = """
            {"id":"OrderCreatedEvent","name":"Order created","kind":"SYSTEM","subjectType":"order","action":"CREATED"}
            """;

    private CreateEventDefinition create;
    private ReplaceEventDefinition replace;
    private DeleteEventDefinition deleteUseCase;
    private FindEventDefinition find;
    private ImportEventDefinitions importer;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        create = mock(CreateEventDefinition.class);
        replace = mock(ReplaceEventDefinition.class);
        deleteUseCase = mock(DeleteEventDefinition.class);
        find = mock(FindEventDefinition.class);
        importer = mock(ImportEventDefinitions.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new EventDefinitionsEndpoint(create, replace, deleteUseCase, find, importer,
                        new EventDefinitionMapper()))
                .setControllerAdvice(new EventApiExceptionHandler())
                .build();
    }

    private static EventDefinition stored() {
        EventDefinition definition = EventDefinition.builder()
                .orgKey("org-1").id("OrderCreatedEvent").name("Order created").kind(EventKind.SYSTEM)
                .subjectType("order").action(PlatformEventAction.CREATED).version(1L)
                .build();
        definition.setCreatedAt(Instant.parse("2026-10-08T10:00:00Z"));
        return definition;
    }

    @Test
    void listsFullDefinitions() throws Exception {
        when(find.findAll("org-1")).thenReturn(List.of(stored()));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("OrderCreatedEvent"))
                .andExpect(jsonPath("$[0].kind").value("SYSTEM"))
                .andExpect(jsonPath("$[0].action").value("CREATED"))
                .andExpect(jsonPath("$[0].subjectType").value("order"))
                .andExpect(jsonPath("$[0].version").value(1));
    }

    @Test
    void createsGetsReplacesAndDeletes() throws Exception {
        when(create.create(eq("org-1"), any())).thenReturn(stored());
        when(find.find("org-1", "OrderCreatedEvent")).thenReturn(stored());
        when(replace.replace(eq("org-1"), eq("OrderCreatedEvent"), any())).thenReturn(stored());

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("OrderCreatedEvent"));
        mvc.perform(get(BASE + "/OrderCreatedEvent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Order created"));
        mvc.perform(put(BASE + "/OrderCreatedEvent").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        mvc.perform(delete(BASE + "/OrderCreatedEvent"))
                .andExpect(status().isNoContent());

        verify(deleteUseCase).delete("org-1", "OrderCreatedEvent");
    }

    @Test
    void importsAYamlFile() throws Exception {
        when(importer.execute(eq("org-1"), any())).thenReturn(new ImportOutcome(2, 1, List.of()));

        mvc.perform(multipart(BASE + "/import")
                        .file(new MockMultipartFile("file", "events.yaml", "application/x-yaml", "event-definitions: []".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(1));
    }

    @Test
    void mapsTheModulesExceptionsToErrorIds() throws Exception {
        when(find.find("org-1", "nope")).thenThrow(new EventDefinitionNotFoundException("org-1", "nope"));
        when(create.create(eq("org-1"), any()))
                .thenThrow(new EventDefinitionAlreadyExistsException("org-1", "OrderCreatedEvent"))
                .thenThrow(new InvalidEventDefinitionException("OrderCreatedEvent", List.of("bad")));
        doThrow(new StaleEventDefinitionException("OrderCreatedEvent"))
                .when(replace).replace(eq("org-1"), eq("OrderCreatedEvent"), any());

        mvc.perform(get(BASE + "/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorId").value("event-definition.not-found"));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorId").value("event-definition.already-exists"));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorId").value("event-definition.invalid"));
        mvc.perform(put(BASE + "/OrderCreatedEvent").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorId").value("event-definition.stale-write"));
    }
}
