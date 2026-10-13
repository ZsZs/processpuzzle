package com.processpuzzle.baseentity.definition.adapters.inbound;

import com.processpuzzle.baseentity.adapter.inbound.EntityDefinitionReleaseParticipant;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.PendingChange;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processpuzzle.baseentity.common.BaseEntityApiExceptionHandler;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionStatus;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.definition.domain.ValueKind;
import com.processpuzzle.baseentity.definition.usecases.inbound.AddAttributeUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.CreateEntityDefinitionUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.DeleteAttributeUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.DeleteEntityDefinitionUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.FindAllEntityDefinitionsUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.FindEntityDefinitionByCodeUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.ReplaceAttributeUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.ReplaceEntityDefinitionUseCase;
import com.processpuzzle.baseentity.model.BaseEntityAttributeInput;
import com.processpuzzle.baseentity.model.BaseEntityDefinitionInput;
import java.util.List;
import java.util.Set;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EntityDefinitionEndpointTest {

    @Mock
    private CreateEntityDefinitionUseCase createUseCase;
    @Mock
    private ReplaceEntityDefinitionUseCase replaceUseCase;
    @Mock
    private DeleteEntityDefinitionUseCase deleteUseCase;
    @Mock
    private FindEntityDefinitionByCodeUseCase findByCodeUseCase;
    @Mock
    private FindAllEntityDefinitionsUseCase findAllUseCase;
    @Mock
    private AddAttributeUseCase addAttributeUseCase;
    @Mock
    private ReplaceAttributeUseCase replaceAttributeUseCase;
    @Mock
    private DeleteAttributeUseCase deleteAttributeUseCase;
    @Mock
    private EntityDefinitionReleaseParticipant releaseParticipant;

    private final EntityDefinitionMapper mapper = new EntityDefinitionMapper();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new PageModule(new SpringDataWebSettings(PageSerializationMode.VIA_DTO)));
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EntityDefinitionEndpoint endpoint = new EntityDefinitionEndpoint(
                createUseCase,
                findByCodeUseCase,
                findAllUseCase,
                replaceUseCase,
                deleteUseCase,
                addAttributeUseCase,
                replaceAttributeUseCase,
                deleteAttributeUseCase,
                mapper,
                releaseParticipant
        );

        mockMvc = MockMvcBuilders.standaloneSetup(endpoint)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new BaseEntityApiExceptionHandler())
                .build();
    }

    @Test
    void findAll_returnsList() throws Exception {
        BaseEntityDefinition def = BaseEntityDefinition.builder()
                .code("partner")
                .name("Partner")
                .status(EntityDefinitionStatus.ACTIVE)
                .build();
        Page<BaseEntityDefinition> page = new PageImpl<>(List.of(def));
        when(findAllUseCase.findAll(eq("test-org"), eq(Channel.DRAFT), any(), any(), any(Pageable.class))).thenReturn(page);
        when(releaseParticipant.drafts("test-org")).thenReturn(Set.of("partner"));
        when(releaseParticipant.pending("test-org"))
                .thenReturn(List.of(new PendingChange("entity", "partner", PendingChange.Change.ADDED, "{}")));

        mockMvc.perform(get("/organizations/test-org/entity-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("partner"))
                .andExpect(jsonPath("$.content[0].name").value("Partner"))
                .andExpect(jsonPath("$.content[0].releaseState").value("NEW"));
    }

    /** Only a draft has a release state: the released row is what it is compared with. */
    @Test
    void findAll_released_carriesNoReleaseState() throws Exception {
        BaseEntityDefinition def = BaseEntityDefinition.builder().code("partner").name("Partner").build();
        when(findAllUseCase.findAll(eq("test-org"), eq(Channel.RELEASED), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(def)));

        mockMvc.perform(get("/organizations/test-org/entity-definitions").param("revision", "released"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].releaseState").doesNotExist());
        org.mockito.Mockito.verifyNoInteractions(releaseParticipant);
    }

    /** The retired DRAFT status is accepted and read as ACTIVE. */
    @Test
    void findAll_filteringByTheRetiredDraftStatus_readsActive() throws Exception {
        when(findAllUseCase.findAll(eq("test-org"), eq(Channel.DRAFT), eq(EntityDefinitionStatus.ACTIVE), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/organizations/test-org/entity-definitions").param("status", "DRAFT"))
                .andExpect(status().isOk());
    }

    @Test
    void findAll_withQueryParams_passesFiltersToUseCase() throws Exception {
        BaseEntityDefinition def = BaseEntityDefinition.builder()
                .code("partner")
                .name("Partner")
                .status(EntityDefinitionStatus.ACTIVE)
                .build();
        Page<BaseEntityDefinition> page = new PageImpl<>(List.of(def));
        when(findAllUseCase.findAll(eq("test-org"), eq(Channel.DRAFT), eq(EntityDefinitionStatus.ACTIVE), eq(false), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/organizations/test-org/entity-definitions")
                        .param("status", "ACTIVE")
                        .param("isEmbedded", "false")
                        .param("page", "1")
                        .param("size", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("partner"));
    }

    @Test
    void findByCode_returnsDefinition() throws Exception {
        BaseEntityDefinition def = BaseEntityDefinition.builder()
                .code("partner")
                .name("Partner")
                .status(EntityDefinitionStatus.ACTIVE)
                .build();
        when(findByCodeUseCase.findByCode("test-org", "partner", Channel.DRAFT)).thenReturn(def);
        when(releaseParticipant.drafts("test-org")).thenReturn(Set.of("partner"));

        mockMvc.perform(get("/organizations/test-org/entity-definitions/partner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("partner"))
                .andExpect(jsonPath("$.name").value("Partner"))
                .andExpect(jsonPath("$.releaseState").value("RELEASED"));
    }

    @Test
    void create_returnsCreatedDefinition() throws Exception {
        BaseEntityDefinition def = BaseEntityDefinition.builder()
                .code("partner")
                .name("Partner")
                .status(EntityDefinitionStatus.ACTIVE)
                .build();
        when(createUseCase.create(eq("test-org"), any())).thenReturn(def);

        BaseEntityDefinitionInput input = new BaseEntityDefinitionInput();
        input.setCode("partner");
        input.setName("Partner");

        mockMvc.perform(post("/organizations/test-org/entity-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("partner"));
    }

    @Test
    void replace_returnsUpdatedDefinition() throws Exception {
        BaseEntityDefinition def = BaseEntityDefinition.builder()
                .code("partner")
                .name("Updated Partner")
                .status(EntityDefinitionStatus.ACTIVE)
                .build();
        when(replaceUseCase.replace(eq("test-org"), eq("partner"), any())).thenReturn(def);

        BaseEntityDefinitionInput input = new BaseEntityDefinitionInput();
        input.setCode("partner");
        input.setName("Updated Partner");

        mockMvc.perform(put("/organizations/test-org/entity-definitions/partner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Partner"));
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/organizations/test-org/entity-definitions/partner"))
                .andExpect(status().isNoContent());

        verify(deleteUseCase).delete("test-org", "partner");
    }

    @Test
    void addAttribute_returnsCreatedAttribute() throws Exception {
        BaseEntityAttribute attr = BaseEntityAttribute.builder()
                .code("email")
                .name("Email")
                .valueKind(ValueKind.TEXT)
                .formControlType(FormControlType.TEXT)
                .build();
        when(addAttributeUseCase.addAttribute(eq("test-org"), eq("partner"), any())).thenReturn(attr);

        BaseEntityAttributeInput input = new BaseEntityAttributeInput();
        input.setCode("email");
        input.setName("Email");
        input.setValueKind(com.processpuzzle.baseentity.model.ValueKind.TEXT);
        input.setFormControlType(com.processpuzzle.baseentity.model.FormControlType.TEXT);

        mockMvc.perform(post("/organizations/test-org/entity-definitions/partner/attributes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("email"));
    }

    @Test
    void replaceAttribute_returnsUpdatedAttribute() throws Exception {
        BaseEntityAttribute attr = BaseEntityAttribute.builder()
                .code("email")
                .name("Updated Email")
                .valueKind(ValueKind.TEXT)
                .formControlType(FormControlType.TEXT)
                .build();
        when(replaceAttributeUseCase.replaceAttribute(eq("test-org"), eq("partner"), eq("email"), any())).thenReturn(attr);

        BaseEntityAttributeInput input = new BaseEntityAttributeInput();
        input.setCode("email");
        input.setName("Updated Email");
        input.setValueKind(com.processpuzzle.baseentity.model.ValueKind.TEXT);
        input.setFormControlType(com.processpuzzle.baseentity.model.FormControlType.TEXT);

        mockMvc.perform(put("/organizations/test-org/entity-definitions/partner/attributes/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Email"));
    }

    @Test
    void deleteAttribute_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/organizations/test-org/entity-definitions/partner/attributes/email"))
                .andExpect(status().isNoContent());

        verify(deleteAttributeUseCase).deleteAttribute("test-org", "partner", "email");
    }
}
