package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.definition.adapters.inbound.EntityDefinitionMapper;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.usecases.inbound.CreateEntityDefinitionUseCase;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DefaultEntityLoaderTest {

    private static final String TESTBED_FILE = "processpuzzle-testbed-entities.yaml";

    private CreateEntityDefinitionUseCase createDefinitionUseCase;
    private EntityDefinitionRepository definitionRepository;
    private EntityDefinitionMapper definitionMapper;
    private CreateEntityInstanceUseCase createInstanceUseCase;
    private ResourcePatternResolver resourceResolver;
    private DefaultEntityLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        createDefinitionUseCase = mock(CreateEntityDefinitionUseCase.class);
        definitionRepository = mock(EntityDefinitionRepository.class);
        definitionMapper = new EntityDefinitionMapper();
        createInstanceUseCase = mock(CreateEntityInstanceUseCase.class);
        resourceResolver = mock(ResourcePatternResolver.class);

        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{bundledTestbedFile()});
        when(definitionRepository.existsByCode(anyString())).thenReturn(false);

        when(createDefinitionUseCase.create(any(BaseEntityDefinition.class)))
                .thenAnswer(call -> {
                    BaseEntityDefinition def = call.getArgument(0);
                    def.setId(UUID.randomUUID());
                    def.setVersion(0L);
                    return def;
                });

        when(createInstanceUseCase.create(anyString(), any()))
                .thenAnswer(call -> {
                    String code = call.getArgument(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = call.getArgument(1);
                    return EntityObject.builder()
                            .id(UUID.randomUUID())
                            .entityDefinitionCode(code)
                            .payload(payload)
                            .build();
                });

        loader = new DefaultEntityLoader(
                createDefinitionUseCase,
                definitionRepository,
                definitionMapper,
                createInstanceUseCase,
                resourceResolver
        );
    }

    @Test
    void createsEveryDefinitionAndInstanceInBundledFile() {
        loader.loadDefaults();

        ArgumentCaptor<BaseEntityDefinition> defCaptor = ArgumentCaptor.forClass(BaseEntityDefinition.class);
        verify(createDefinitionUseCase, times(3)).create(defCaptor.capture());

        List<BaseEntityDefinition> capturedDefs = defCaptor.getAllValues();
        assertThat(capturedDefs).extracting(BaseEntityDefinition::getCode)
                .containsExactly("dynamic-embedded-address", "dynamic-embedded-detail", "dynamic-entity");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(createInstanceUseCase, times(5)).create(codeCaptor.capture(), payloadCaptor.capture());

        assertThat(codeCaptor.getAllValues()).allMatch(code -> code.equals("dynamic-entity"));
        assertThat(payloadCaptor.getAllValues()).hasSize(5);
    }

    @Test
    void leavesAnAlreadyPresentDefinitionUntouched() {
        when(definitionRepository.existsByCode("dynamic-embedded-address")).thenReturn(true);

        loader.loadDefaults();

        verify(createDefinitionUseCase, times(2)).create(any(BaseEntityDefinition.class));
    }

    @Test
    void survivesConflictExceptionWhenCreatingDefinition() {
        doThrow(new ConflictException("already exists"))
                .when(createDefinitionUseCase).create(argThat(def -> "dynamic-entity".equals(def.getCode())));

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void survivesValidationExceptionWhenCreatingDefinition() {
        doThrow(new ValidationException(List.of(new ValidationException.Violation("name", "required"))))
                .when(createDefinitionUseCase).create(argThat(def -> "dynamic-entity".equals(def.getCode())));

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void survivesValidationExceptionWhenCreatingInstance() {
        doThrow(new ValidationException(List.of(new ValidationException.Violation("name", "required"))))
                .when(createInstanceUseCase).create(anyString(), any());

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void survivesIoExceptionOnScanning() throws IOException {
        when(resourceResolver.getResources(anyString())).thenThrow(new IOException("disk error"));

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void handlesNoFilesGracefully() throws IOException {
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[0]);

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
        verifyNoInteractions(createDefinitionUseCase);
        verifyNoInteractions(createInstanceUseCase);
    }

    @Test
    void skipsFilesNotMatchingConvention() throws IOException {
        Resource invalidNameResource = new ByteArrayResource(
                "entityDefinitions: []\nentities: []".getBytes(StandardCharsets.UTF_8)
        ) {
            @Override
            public String getFilename() {
                return "invalid-name.txt";
            }
        };
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{invalidNameResource});

        loader.loadDefaults();

        verifyNoInteractions(createDefinitionUseCase);
        verifyNoInteractions(createInstanceUseCase);
    }

    private static Resource bundledTestbedFile() {
        return new ClassPathResource("default-entities/" + TESTBED_FILE);
    }
}
