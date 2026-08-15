package com.processpuzzle.baseentity.instances.adapters.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.processpuzzle.baseentity.common.BaseEntityApiExceptionHandler;
import com.processpuzzle.baseentity.instances.adapters.inbound.EntityObjectEndpoint.EntityObjectUpdateRequest;
import com.processpuzzle.baseentity.instances.adapters.inbound.dto.EntityObjectDto;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.usecases.inbound.*;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EntityObjectEndpointTest {

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
        when(findByIdUseCase.findById(id)).thenReturn(entity);

        mockMvc.perform(get("/api/base-entity/entities/{id}", id))
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
        when(createUseCase.create(eq("partner"), any())).thenReturn(entity);

        EntityObjectDto inputDto = EntityObjectDto.builder()
                .entityDefinitionCode("partner")
                .payload(Map.of("name", "ACME"))
                .build();

        mockMvc.perform(post("/api/base-entity/entities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
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
        when(updateUseCase.update(eq(id), eq(1L), any())).thenReturn(entity);

        EntityObjectUpdateRequest updateRequest = new EntityObjectUpdateRequest(1L, Map.of("name", "ACME Updated"));

        mockMvc.perform(put("/api/base-entity/entities/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.payload.name").value("ACME Updated"));
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/base-entity/entities/{id}", id)
                        .param("cascade", "false"))
                .andExpect(status().isNoContent());

        verify(deleteUseCase).delete(id, false);
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

        when(searchUseCase.search(eq("partner"), eq("name==ACME"), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/base-entity/entities")
                        .param("entityDefinitionCode", "partner")
                        .param("rsql", "name==ACME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].entityDefinitionCode").value("partner"));
    }
}
