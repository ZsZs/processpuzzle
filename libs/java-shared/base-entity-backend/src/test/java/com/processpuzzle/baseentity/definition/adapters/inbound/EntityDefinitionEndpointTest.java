package com.processpuzzle.baseentity.definition.adapters.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.processpuzzle.baseentity.common.BaseEntityApiExceptionHandler;
import com.processpuzzle.baseentity.definition.domain.*;
import com.processpuzzle.baseentity.definition.usecases.inbound.*;
import com.processpuzzle.baseentity.model.BaseEntityAttributeInput;
import com.processpuzzle.baseentity.model.BaseEntityDefinitionInput;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
                mapper
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
        when(findAllUseCase.findAll(any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/organizations/test-org/entity-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("partner"))
                .andExpect(jsonPath("$.content[0].name").value("Partner"));
    }

    @Test
    void findByCode_returnsDefinition() throws Exception {
        BaseEntityDefinition def = BaseEntityDefinition.builder()
                .code("partner")
                .name("Partner")
                .status(EntityDefinitionStatus.ACTIVE)
                .build();
        when(findByCodeUseCase.findByCode("partner")).thenReturn(def);

        mockMvc.perform(get("/organizations/test-org/entity-definitions/partner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("partner"))
                .andExpect(jsonPath("$.name").value("Partner"));
    }

    @Test
    void create_returnsCreatedDefinition() throws Exception {
        BaseEntityDefinition def = BaseEntityDefinition.builder()
                .code("partner")
                .name("Partner")
                .status(EntityDefinitionStatus.DRAFT)
                .build();
        when(createUseCase.create(any())).thenReturn(def);

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
        when(replaceUseCase.replace(eq("partner"), any())).thenReturn(def);

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

        verify(deleteUseCase).delete("partner");
    }

    @Test
    void addAttribute_returnsCreatedAttribute() throws Exception {
        BaseEntityAttribute attr = BaseEntityAttribute.builder()
                .code("email")
                .name("Email")
                .valueKind(ValueKind.TEXT)
                .formControlType(FormControlType.TEXT)
                .build();
        when(addAttributeUseCase.addAttribute(eq("partner"), any())).thenReturn(attr);

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
        when(replaceAttributeUseCase.replaceAttribute(eq("partner"), eq("email"), any())).thenReturn(attr);

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

        verify(deleteAttributeUseCase).deleteAttribute("partner", "email");
    }
}
