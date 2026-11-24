package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.definition.adapters.inbound.EntityDefinitionMapper;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.DateFormat;
import com.processpuzzle.baseentity.definition.domain.DateStyle;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.definition.domain.TimeStyle;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.usecases.inbound.CreateEntityDefinitionUseCase;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultEntityLoaderTest {

    private static final String TESTBED_FILE = "processpuzzle-testbed-entities.yaml";
    private static final String ORG = "processpuzzle-testbed";

    private CreateEntityDefinitionUseCase createDefinitionUseCase;
    private EntityDefinitionRepository definitionRepository;
    private EntityDefinitionMapper definitionMapper;
    private CreateEntityInstanceUseCase createInstanceUseCase;
    private EntityObjectRepository objectRepository;
    private ResourcePatternResolver resourceResolver;
    private SeededMeasureUpgrader measureUpgrader;
    private SeededInstanceUpgrader instanceUpgrader;
    private DefaultEntityLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        createDefinitionUseCase = mock(CreateEntityDefinitionUseCase.class);
        definitionRepository = mock(EntityDefinitionRepository.class);
        definitionMapper = new EntityDefinitionMapper();
        createInstanceUseCase = mock(CreateEntityInstanceUseCase.class);
        objectRepository = mock(EntityObjectRepository.class);
        resourceResolver = mock(ResourcePatternResolver.class);

        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{bundledTestbedFile()});
        when(definitionRepository.existsByOrgKeyAndCodeAndChannel(anyString(), anyString(), eq(Channel.DRAFT))).thenReturn(false);
        when(objectRepository.existsByOrgKeyAndEntityDefinitionCode(anyString(), anyString())).thenReturn(false);

        when(createDefinitionUseCase.create(anyString(), any(BaseEntityDefinition.class)))
                .thenAnswer(call -> {
                    BaseEntityDefinition def = call.getArgument(1);
                    def.setId(UUID.randomUUID());
                    def.setVersion(0L);
                    return def;
                });

        when(createInstanceUseCase.create(anyString(), anyString(), any()))
                .thenAnswer(call -> {
                    String orgKey = call.getArgument(0);
                    String code = call.getArgument(1);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = call.getArgument(2);
                    return EntityObject.builder()
                            .id(UUID.randomUUID())
                            .orgKey(orgKey)
                            .entityDefinitionCode(code)
                            .payload(payload)
                            .build();
                });

        measureUpgrader = mock(SeededMeasureUpgrader.class);
        instanceUpgrader = mock(SeededInstanceUpgrader.class);

        loader = new DefaultEntityLoader(
                createDefinitionUseCase,
                definitionRepository,
                definitionMapper,
                createInstanceUseCase,
                new EntityObjectScope(objectRepository),
                resourceResolver,
                measureUpgrader,
                instanceUpgrader
        );
    }

    @Test
    void seededInstancesOfAnExistingDefinitionAreBroughtUpToTheirMeasures() {
        when(objectRepository.existsByOrgKeyAndEntityDefinitionCode(anyString(), anyString())).thenReturn(true);

        loader.loadDefaults();

        verify(createInstanceUseCase, never()).create(anyString(), anyString(), any());
        verify(instanceUpgrader, atLeastOnce()).upgrade(eq(ORG), eq("order"));
    }

    @Test
    void anExistingDefinitionIsLeftUntouchedButItsMeasuresAreUpgraded() {
        when(definitionRepository.existsByOrgKeyAndCodeAndChannel(anyString(), anyString(), eq(Channel.DRAFT))).thenReturn(true);

        loader.loadDefaults();

        verify(createDefinitionUseCase, org.mockito.Mockito.never()).create(anyString(), any(BaseEntityDefinition.class));
        verify(measureUpgrader, org.mockito.Mockito.atLeastOnce()).upgrade(eq(ORG), any(BaseEntityDefinition.class));
    }

    @Test
    void createsEveryDefinitionAndInstanceInBundledFile() {
        loader.loadDefaults();

        ArgumentCaptor<BaseEntityDefinition> defCaptor = ArgumentCaptor.forClass(BaseEntityDefinition.class);
        verify(createDefinitionUseCase, times(15)).create(eq(ORG), defCaptor.capture());

        List<BaseEntityDefinition> capturedDefs = defCaptor.getAllValues();
        assertThat(capturedDefs).extracting(BaseEntityDefinition::getCode)
                .containsExactly("dynamic-embedded-address", "dynamic-embedded-detail", "dynamic-entity",
                        "order-line", "order", "special-order-line", "special-order", "boat",
                        "race", "race-registration", "race-observation", "demo-component-one", "demo-component-two", "demo-root",
                        "tutorial");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(createInstanceUseCase, times(19)).create(anyString(), codeCaptor.capture(), payloadCaptor.capture());

        // 'order-line' and 'special-order-line' are embedded, so neither contributes an instance of its own:
        // their rows travel inside the lineItems array of the four orders and two special orders below.
        assertThat(codeCaptor.getAllValues()).containsExactly(
                "dynamic-entity", "dynamic-entity", "dynamic-entity", "dynamic-entity", "dynamic-entity",
                "order", "order", "order", "order", "special-order", "special-order",
                "boat", "boat", "boat", "boat", "race", "race", "demo-root", "demo-root");
        assertThat(payloadCaptor.getAllValues()).hasSize(19);
    }

    @Test
    void seedsTheTestbedDateFormats() {
        loader.loadDefaults();

        ArgumentCaptor<BaseEntityDefinition> defCaptor = ArgumentCaptor.forClass(BaseEntityDefinition.class);
        verify(createDefinitionUseCase, times(15)).create(eq(ORG), defCaptor.capture());
        BaseEntityDefinition dynamicEntity = defCaptor.getAllValues().stream()
                .filter(def -> def.getCode().equals("dynamic-entity")).findFirst().orElseThrow();

        assertThat(attribute(dynamicEntity, "dateAttr").getDateFormat())
                .isEqualTo(new DateFormat(DateStyle.SHORT, null));
        assertThat(attribute(dynamicEntity, "dateTimeAttr").getDateFormat())
                .isEqualTo(new DateFormat(DateStyle.MEDIUM, TimeStyle.SHORT));
    }

    @Test
    void seedsTheTestbedMeasures() {
        loader.loadDefaults();

        ArgumentCaptor<BaseEntityDefinition> defCaptor = ArgumentCaptor.forClass(BaseEntityDefinition.class);
        verify(createDefinitionUseCase, times(15)).create(eq(ORG), defCaptor.capture());
        BaseEntityDefinition dynamicEntity = defCaptor.getAllValues().stream()
                .filter(def -> def.getCode().equals("dynamic-entity")).findFirst().orElseThrow();

        BaseEntityAttribute weight = attribute(dynamicEntity, "weight");
        assertThat(weight.getFormControlType()).isEqualTo(FormControlType.QUANTITY);
        assertThat(weight.getQuantityKind()).isEqualTo("MASS");
        assertThat(weight.getAllowedUnits()).containsExactly("kg", "g", "lb");
        assertThat(weight.getDefaultUnit()).isEqualTo("kg");
        assertThat(weight.getScale()).isEqualTo(3);
        BaseEntityAttribute price = attribute(dynamicEntity, "price");
        assertThat(price.getFormControlType()).isEqualTo(FormControlType.MONEY);
        assertThat(price.getAllowedCurrencies()).containsExactly("EUR", "HUF", "USD");
        assertThat(price.getDefaultCurrency()).isEqualTo("EUR");
        BaseEntityAttribute size = attribute(dynamicEntity, "size");
        assertThat(size.getFormControlType()).isEqualTo(FormControlType.DIMENSIONS);
        assertThat(size.getDimensionCount()).isEqualTo(3);
        assertThat(size.getAllowedUnits()).containsExactly("cm", "m", "in");
        assertThat(dynamicEntity.getAttributes()).filteredOn(a -> List.of("weight", "price", "size").contains(a.getCode()))
                .as("optional, so the generated e2e suites that cannot fill them still save").allMatch(a -> !a.isRequired());
    }

    private static BaseEntityAttribute attribute(BaseEntityDefinition definition, String code) {
        return definition.getAttributes().stream().filter(a -> a.getCode().equals(code)).findFirst().orElseThrow();
    }

    /**
     * The regression that cost the stage host its memory. Until 2026-09-21 this loader created its
     * sample instances unconditionally, so every restart appended another eleven rows — 856 of them
     * by the time anyone counted, each re-read at boot by GovernedStateConsistencyCheck.
     */
    @Test
    void createsNoInstanceForADefinitionThatAlreadyHasOne() {
        when(objectRepository.existsByOrgKeyAndEntityDefinitionCode(ORG, "order")).thenReturn(true);

        loader.loadDefaults();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(createInstanceUseCase, times(15)).create(anyString(), codeCaptor.capture(), any());

        // The four 'order' rows are gone; nothing else is affected, 'special-order' least of all —
        // it is a different definition despite the shared prefix.
        assertThat(codeCaptor.getAllValues()).containsExactly(
                "dynamic-entity", "dynamic-entity", "dynamic-entity", "dynamic-entity", "dynamic-entity",
                "special-order", "special-order", "boat", "boat", "boat", "boat", "race", "race", "demo-root", "demo-root");
    }

    /**
     * The snapshot, which is what makes the guard correct on a FRESH database. Asked per instance
     * instead of once up front, the first 'order' would be created and the repository would then
     * answer "yes" for the three that follow it — seeding one row where the file asks for four.
     */
    @Test
    void createsEveryInstanceOfADefinitionWhenTheFirstOneIsNew() {
        loader.loadDefaults();

        verify(objectRepository, times(1)).existsByOrgKeyAndEntityDefinitionCode(ORG, "order");
        verify(createInstanceUseCase, times(4))
                .create(anyString(), argThat("order"::equals), any());
    }

    @Test
    void leavesAnAlreadyPresentDefinitionUntouched() {
        when(definitionRepository.existsByOrgKeyAndCodeAndChannel(ORG, "dynamic-embedded-address", Channel.DRAFT)).thenReturn(true);

        loader.loadDefaults();

        verify(createDefinitionUseCase, times(14)).create(anyString(), any(BaseEntityDefinition.class));
    }

    @Test
    void survivesConflictExceptionWhenCreatingDefinition() {
        doThrow(new ConflictException("already exists"))
                .when(createDefinitionUseCase).create(anyString(), argThat(def -> "dynamic-entity".equals(def.getCode())));

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void survivesValidationExceptionWhenCreatingDefinition() {
        doThrow(new ValidationException(List.of(new ValidationException.Violation("name", "required"))))
                .when(createDefinitionUseCase).create(anyString(), argThat(def -> "dynamic-entity".equals(def.getCode())));

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void survivesValidationExceptionWhenCreatingInstance() {
        doThrow(new ValidationException(List.of(new ValidationException.Violation("name", "required"))))
                .when(createInstanceUseCase).create(anyString(), anyString(), any());

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

    @Test
    void skipsFilesWithBlankOrgKey() throws IOException {
        Resource blankOrgResource = new ByteArrayResource(
                "entityDefinitions: []\nentities: []".getBytes(StandardCharsets.UTF_8)
        ) {
            @Override
            public String getFilename() {
                return "-entities.yaml";
            }
        };
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{blankOrgResource});

        loader.loadDefaults();

        verifyNoInteractions(createDefinitionUseCase);
        verifyNoInteractions(createInstanceUseCase);
    }

    @Test
    void survivesIoExceptionOnReadingResource() throws IOException {
        Resource brokenResource = mock(Resource.class);
        when(brokenResource.getFilename()).thenReturn("test-entities.yaml");
        when(brokenResource.getInputStream()).thenThrow(new IOException("corrupt file"));
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{brokenResource});

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void handlesNullOrBlankDefinitionCodeAndNullEntities() throws IOException {
        String yamlContent = """
                entityDefinitions:
                  - code: ""
                    name: "Empty Code"
                  - code: null
                    name: "Null Code"
                entities:
                  - entityDefinitionCode: ""
                    payload: {}
                  - entityDefinitionCode: "test"
                    payload: null
                  - entityDefinitionCode: null
                    payload: {"a": 1}
                """;
        Resource yamlResource = new ByteArrayResource(yamlContent.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "custom-entities.yaml";
            }
        };
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{yamlResource});

        loader.loadDefaults();

        verifyNoInteractions(createDefinitionUseCase);
        verifyNoInteractions(createInstanceUseCase);
    }

    @Test
    void survivesRuntimeExceptionWhenCreatingDefinition() {
        doThrow(new RuntimeException("unexpected db error"))
                .when(createDefinitionUseCase).create(anyString(), argThat(def -> "dynamic-entity".equals(def.getCode())));

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void survivesConflictExceptionWhenCreatingInstance() {
        doThrow(new ConflictException("already exists"))
                .when(createInstanceUseCase).create(anyString(), anyString(), any());

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void survivesRuntimeExceptionWhenCreatingInstance() {
        doThrow(new RuntimeException("unexpected error"))
                .when(createInstanceUseCase).create(anyString(), anyString(), any());

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    private static Resource bundledTestbedFile() {
        return new ClassPathResource("default-entities/" + TESTBED_FILE);
    }
}
