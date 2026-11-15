package com.processpuzzle.baseentity.instances.adapters.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.processpuzzle.baseentity.common.BaseEntityApiExceptionHandler;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.DeleteEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.EmbeddedCollectionUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.FindEntityInstanceByIdUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.SearchEntityInstancesUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.UpdateEntityInstanceUseCase;
import com.processpuzzle.baseentity.model.EntityObjectInput;
import com.processpuzzle.baseentity.model.EntityObjectUpdate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.data.web.config.SpringDataJacksonConfiguration.PageModule;
import org.springframework.data.web.config.SpringDataWebSettings;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EntityObjectEndpointTest {

    private static final String ORG = "test-org";

    @Mock
    private CreateEntityInstanceUseCase createUseCase;
    @Mock
    private UpdateEntityInstanceUseCase updateUseCase;
    @Mock
    private DeleteEntityInstanceUseCase deleteUseCase;
    @Mock
    private FindEntityInstanceByIdUseCase findByIdUseCase;
    @Mock
    private SearchEntityInstancesUseCase searchUseCase;
    @Mock
    private EmbeddedCollectionUseCase embeddedUseCase;

    private final EntityObjectMapper mapper = new EntityObjectMapper();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new PageModule(new SpringDataWebSettings(PageSerializationMode.VIA_DTO)));
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EntityObjectEndpoint endpoint = new EntityObjectEndpoint(
                createUseCase,
                findByIdUseCase,
                searchUseCase,
                updateUseCase,
                deleteUseCase,
                embeddedUseCase,
                mapper
        );

        mockMvc = MockMvcBuilders.standaloneSetup(endpoint)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new BaseEntityApiExceptionHandler())
                .build();
    }

    @Test
    void findById_returnsEntity() throws Exception {
        UUID id = UUID.randomUUID();
        EntityObject entity = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .version(1L)
                .payload(Map.of("name", "ACME"))
                .build();
        when(findByIdUseCase.findById(ORG, "partner", id)).thenReturn(entity);

        mockMvc.perform(get("/organizations/test-org/entities/{entityDefinitionCode}/{id}", "partner", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.entityDefinitionCode").value("partner"))
                .andExpect(jsonPath("$.payload.name").value("ACME"));
    }

    @Test
    void create_returnsCreatedEntity() throws Exception {
        UUID id = UUID.randomUUID();
        EntityObject entity = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .version(0L)
                .payload(Map.of("name", "ACME"))
                .build();
        when(createUseCase.create(eq(ORG), eq("partner"), any())).thenReturn(entity);
        when(findByIdUseCase.findById(ORG, "partner", id)).thenThrow(new NotFoundException("deleted meanwhile"));

        EntityObjectInput input = new EntityObjectInput();
        input.setEntityDefinitionCode("partner");
        input.setPayload(Map.of("name", "ACME"));

        mockMvc.perform(post("/organizations/test-org/entities/{entityDefinitionCode}", "partner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.entityDefinitionCode").value("partner"));
    }

    @Test
    void update_returnsUpdatedEntity() throws Exception {
        UUID id = UUID.randomUUID();
        EntityObject entity = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .version(2L)
                .payload(Map.of("name", "ACME Updated"))
                .build();
        when(updateUseCase.update(eq(ORG), eq("partner"), eq(id), eq(1L), any())).thenReturn(entity);
        // What an after-commit listener wrote onto it — a calculated total — keeping the version.
        EntityObject committed = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .version(2L)
                .payload(Map.of("name", "ACME Updated", "total", 42))
                .build();
        when(findByIdUseCase.findById(ORG, "partner", id)).thenReturn(committed);

        EntityObjectUpdate updateRequest = new EntityObjectUpdate();
        updateRequest.setVersion(1);
        updateRequest.setPayload(Map.of("name", "ACME Updated"));

        mockMvc.perform(put("/organizations/test-org/entities/{entityDefinitionCode}/{id}", "partner", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.payload.name").value("ACME Updated"))
                .andExpect(jsonPath("$.payload.total").value(42));
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/organizations/test-org/entities/{entityDefinitionCode}/{id}", "partner", id)
                        .param("cascade", "false"))
                .andExpect(status().isNoContent());

        verify(deleteUseCase).delete(ORG, "partner", id, false);
    }

    @Test
    void delete_withCascadeTrue_callsUseCaseWithCascadeTrue() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/organizations/test-org/entities/{entityDefinitionCode}/{id}", "partner", id)
                        .param("cascade", "true"))
                .andExpect(status().isNoContent());

        verify(deleteUseCase).delete(ORG, "partner", id, true);
    }

    @Test
    void delete_withoutCascadeParam_callsUseCaseWithCascadeFalse() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/organizations/test-org/entities/{entityDefinitionCode}/{id}", "partner", id))
                .andExpect(status().isNoContent());

        verify(deleteUseCase).delete(ORG, "partner", id, false);
    }

    @Test
    void search_returnsPage() throws Exception {
        UUID id = UUID.randomUUID();
        EntityObject entity = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .version(1L)
                .payload(Map.of("name", "ACME"))
                .build();
        Page<EntityObject> page = new PageImpl<>(List.of(entity));

        when(searchUseCase.search(eq("test-org"), eq("partner"), eq("name==ACME"), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/organizations/test-org/entities/{entityDefinitionCode}", "partner")
                        .param("rsql", "name==ACME")
                        .param("sort", "name,asc")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].entityDefinitionCode").value("partner"));
    }

    @Test
    void embeddedItems_listCarriesTheVersionAsAnETag() throws Exception {
        UUID id = UUID.randomUUID();
        when(embeddedUseCase.list(ORG, "race", id, "rounds")).thenReturn(new EmbeddedCollectionUseCase.Items(List.of(Map.of("id", "r1")), 5L));

        mockMvc.perform(get("/organizations/test-org/entities/race/{id}/rounds", id))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(jsonPath("$[0].id").value("r1"));
    }

    @Test
    void embeddedItems_appendAnswers201WithTheItemAndTheNewVersion() throws Exception {
        UUID id = UUID.randomUUID();
        when(embeddedUseCase.append(ORG, "race", id, "rounds", Map.of("number", 2)))
                .thenReturn(new EmbeddedCollectionUseCase.ItemResult(Map.of("id", "r2", "number", 2), 6L));

        mockMvc.perform(post("/organizations/test-org/entities/race/{id}/rounds", id).contentType(MediaType.APPLICATION_JSON).content("{\"number\":2}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"6\""))
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/rounds/r2")))
                .andExpect(jsonPath("$.id").value("r2"));
    }

    @Test
    void embeddedItems_patchTakesTheVersionFromIfMatch() throws Exception {
        UUID id = UUID.randomUUID();
        when(embeddedUseCase.patch(ORG, "race", id, "rounds", "r1", 5L, Map.of("number", 3)))
                .thenReturn(new EmbeddedCollectionUseCase.ItemResult(Map.of("id", "r1", "number", 3), 6L));

        mockMvc.perform(patch("/organizations/test-org/entities/race/{id}/rounds/r1", id).header("If-Match", "\"5\"").contentType(MediaType.APPLICATION_JSON).content("{\"number\":3}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"6\""))
                .andExpect(jsonPath("$.number").value(3));
    }

    @Test
    void embeddedItems_patchWithoutIfMatchAppliesToWhatIsStored() throws Exception {
        UUID id = UUID.randomUUID();
        when(embeddedUseCase.patch(ORG, "race", id, "rounds", "r1", null, Map.of("number", 3)))
                .thenReturn(new EmbeddedCollectionUseCase.ItemResult(Map.of("id", "r1"), 7L));

        mockMvc.perform(patch("/organizations/test-org/entities/race/{id}/rounds/r1", id).contentType(MediaType.APPLICATION_JSON).content("{\"number\":3}"))
                .andExpect(status().isOk());
    }

    @Test
    void embeddedItems_aMalformedIfMatchIsABadRequest() throws Exception {
        mockMvc.perform(patch("/organizations/test-org/entities/race/{id}/rounds/r1", UUID.randomUUID()).header("If-Match", "latest").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void embeddedItems_deleteAnswers204() throws Exception {
        UUID id = UUID.randomUUID();
        when(embeddedUseCase.remove(ORG, "race", id, "rounds", "r1", null)).thenReturn(new EmbeddedCollectionUseCase.ItemResult(null, 6L));

        mockMvc.perform(delete("/organizations/test-org/entities/race/{id}/rounds/r1", id))
                .andExpect(status().isNoContent())
                .andExpect(header().string("ETag", "\"6\""));
    }
}
