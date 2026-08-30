package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.TaskStepType;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.definition.domain.WorkflowValidator;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportWorkflowsUseCase;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultWorkflowImporterTest {

    private static final String ORG = "processpuzzle-testbed";

    private static final String TESTBED_FILE = "processpuzzle-testbed-workflows.yaml";

    private ImportWorkflowsUseCase importUseCase;
    private ResourcePatternResolver resourceResolver;
    private DefaultWorkflowImporter importer;

    @BeforeEach
    void setUp() {
        importUseCase = mock(ImportWorkflowsUseCase.class);
        resourceResolver = mock(ResourcePatternResolver.class);
        importer = new DefaultWorkflowImporter(importUseCase, resourceResolver);
    }

    @Test
    void loadsAndImportsBundledWorkflowsFile() throws IOException {
        Resource bundledResource = bundledTestbedFile();
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{bundledResource});
        when(importUseCase.execute(eq("processpuzzle-testbed"), any(InputStream.class)))
                .thenReturn(new ImportOutcome(1, 0, List.of()));

        importer.loadDefaults();

        verify(importUseCase).execute(eq("processpuzzle-testbed"), any(InputStream.class));
    }

    /**
     * The bundled seed file, through the real importer, mapper and validator — the one test that
     * would catch a section name, field name or reference in
     * {@code default-workflows/processpuzzle-testbed-workflows.yaml} drifting away from the code that
     * reads it. That drift is otherwise invisible until startup, where the importer logs and moves on
     * rather than failing.
     *
     * <p>The four catalog repositories are stubbed to resolve whatever the file itself defines, since
     * the definition sections are written before the workflow that references them — so
     * {@link WorkflowValidator} sees the same-file catalog.
     */
    @Test
    void bundledFileParsesAndImportsValidWorkflow() throws IOException {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        RoleDefinitionRepository roleRepository = mock(RoleDefinitionRepository.class);
        ArtifactDefinitionRepository artifactRepository = mock(ArtifactDefinitionRepository.class);
        ToolDefinitionRepository toolRepository = mock(ToolDefinitionRepository.class);
        TaskDefinitionRepository taskRepository = mock(TaskDefinitionRepository.class);
        WorkflowValidator validator = new WorkflowValidator(
                roleRepository, artifactRepository, toolRepository, taskRepository);
        ImportWorkflowsUseCase realImportUseCase = new ImportWorkflowsUseCase(
                repository, roleRepository, artifactRepository, toolRepository, taskRepository,
                validator, new WorkflowYamlMapper());

        when(repository.findByOrgKey(ORG)).thenReturn(List.of());
        when(repository.findByOrgKeyAndId(eq(ORG), anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByOrgKeyAndId(eq(ORG), anyString())).thenReturn(Optional.empty());
        when(artifactRepository.findByOrgKeyAndId(eq(ORG), anyString())).thenReturn(Optional.empty());
        when(toolRepository.findByOrgKeyAndId(eq(ORG), anyString())).thenReturn(Optional.empty());
        when(taskRepository.findByOrgKeyAndId(eq(ORG), anyString())).thenReturn(Optional.empty());

        // The validator resolves references through the repositories; the saves above are what the
        // file itself contributes, so echo them back.
        List<RoleDefinition> savedRoles = new ArrayList<>();
        List<ArtifactDefinition> savedArtifacts = new ArrayList<>();
        List<ToolDefinition> savedTools = new ArrayList<>();
        List<TaskDefinition> savedTasks = new ArrayList<>();
        when(roleRepository.save(any())).thenAnswer(call -> record(savedRoles, call.getArgument(0)));
        when(artifactRepository.save(any())).thenAnswer(call -> record(savedArtifacts, call.getArgument(0)));
        when(toolRepository.save(any())).thenAnswer(call -> record(savedTools, call.getArgument(0)));
        when(taskRepository.save(any())).thenAnswer(call -> record(savedTasks, call.getArgument(0)));
        when(roleRepository.existsByOrgKeyAndId(eq(ORG), anyString()))
                .thenAnswer(call -> hasId(savedRoles, call.getArgument(1), RoleDefinition::getId));
        when(artifactRepository.existsByOrgKeyAndId(eq(ORG), anyString()))
                .thenAnswer(call -> hasId(savedArtifacts, call.getArgument(1), ArtifactDefinition::getId));
        when(toolRepository.existsByOrgKeyAndId(eq(ORG), anyString()))
                .thenAnswer(call -> hasId(savedTools, call.getArgument(1), ToolDefinition::getId));
        when(taskRepository.findByOrgKeyAndIdIn(eq(ORG), anyList())).thenAnswer(call -> {
            List<String> requested = call.getArgument(1);
            return savedTasks.stream().filter(task -> requested.contains(task.getId())).toList();
        });

        try (InputStream input = bundledTestbedFile().getInputStream()) {
            ImportOutcome outcome = realImportUseCase.execute(ORG, input);

            // 2 roles + 2 artifacts + 1 tool + 3 tasks + 1 workflow
            assertThat(outcome.errors()).isEmpty();
            assertThat(outcome.created()).isEqualTo(9);
            assertThat(outcome.updated()).isZero();

            assertThat(savedRoles).extracting(RoleDefinition::getId).containsExactly("clerk", "manager");
            assertThat(savedRoles).extracting(RoleDefinition::getResponsibleFor)
                    .containsExactly(List.of("order-entity"), List.of("fulfillment-invoice"));
            assertThat(savedArtifacts).extracting(ArtifactDefinition::getId)
                    .containsExactly("order-entity", "fulfillment-invoice");
            assertThat(savedArtifacts).extracting(ArtifactDefinition::getArtifactType)
                    .containsExactly(ArtifactType.ENTITY, ArtifactType.DOCUMENT);
            assertThat(savedTools).extracting(ToolDefinition::getId).containsExactly("automated-check-tool");
            assertThat(savedTasks).extracting(TaskDefinition::getId)
                    .containsExactly("review-order", "approve-shipment", "confirm-delivery");

            // Every task's artifacts resolve, which is what the validator would otherwise refuse.
            assertThat(savedTasks).flatExtracting(TaskDefinition::getInputs).containsOnly("order-entity");
            assertThat(savedTasks).flatExtracting(TaskDefinition::getOutputs)
                    .containsExactly("order-entity", "order-entity", "fulfillment-invoice");
            assertThat(savedTasks).flatExtracting(TaskDefinition::getSteps)
                    .extracting(StepDefinition::getStepType)
                    .containsExactly(TaskStepType.SERVICE_STEP, TaskStepType.USER_STEP, TaskStepType.SERVICE_STEP);

            ArgumentCaptor<Workflow> defCaptor = ArgumentCaptor.forClass(Workflow.class);
            verify(repository).save(defCaptor.capture());

            Workflow def = defCaptor.getValue();
            assertThat(def.getOrgKey()).isEqualTo(ORG);
            assertThat(def.getId()).isEqualTo("order-fulfillment-workflow");
            assertThat(def.getName()).isEqualTo("Order Fulfillment Workflow");
            assertThat(def.roleDefinitionIds()).containsExactly("clerk", "manager");
            assertThat(def.artifactDefinitionIds()).containsExactly("order-entity", "fulfillment-invoice");
            assertThat(def.toolDefinitionIds()).containsExactly("automated-check-tool");
            assertThat(def.taskDefinitionIds())
                    .containsExactly("review-order", "approve-shipment", "confirm-delivery");
            assertThat(def.getStartCondition().getStartType())
                    .isEqualTo(WorkflowStartConditionType.INPUT_ARTIFACT);
            assertThat(def.getStartCondition().getRequiredArtifacts()).singleElement()
                    .satisfies(required -> {
                        assertThat(required.getArtifactDefinitionId()).isEqualTo("order-entity");
                        assertThat(required.getState()).isEqualTo("DRAFT");
                    });
        }
    }

    private static <D> D record(List<D> saved, D definition) {
        saved.add(definition);
        return definition;
    }

    private static <D> boolean hasId(List<D> saved, String id, java.util.function.Function<D, String> idOf) {
        return saved.stream().map(idOf).anyMatch(id::equals);
    }

    @Test
    void skipsFileWhenNameDoesNotFollowConvention() throws IOException {
        Resource invalidNameResource = new ByteArrayResource("workflows: []".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "invalid-name.yaml";
            }
        };
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{invalidNameResource});

        importer.loadDefaults();

        verifyNoInteractions(importUseCase);
    }

    @Test
    void skipsFileWhenOrgKeyIsEmpty() throws IOException {
        Resource emptyOrgResource = new ByteArrayResource("workflows: []".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "-workflows.yaml";
            }
        };
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{emptyOrgResource});

        importer.loadDefaults();

        verifyNoInteractions(importUseCase);
    }

    @Test
    void handlesResourceScanFailureGracefully() throws IOException {
        when(resourceResolver.getResources(anyString())).thenThrow(new IOException("Disk I/O error"));

        assertThatCode(() -> importer.loadDefaults()).doesNotThrowAnyException();
        verifyNoInteractions(importUseCase);
    }

    @Test
    void handlesEmptyResourceListGracefully() throws IOException {
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[0]);

        assertThatCode(() -> importer.loadDefaults()).doesNotThrowAnyException();
        verifyNoInteractions(importUseCase);
    }

    @Test
    void handlesImportExceptionGracefully() throws IOException {
        Resource brokenResource = mock(Resource.class);
        when(brokenResource.getFilename()).thenReturn("test-org-workflows.yaml");
        when(brokenResource.getInputStream()).thenThrow(new IOException("Stream failed"));
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{brokenResource});

        assertThatCode(() -> importer.loadDefaults()).doesNotThrowAnyException();
    }

    @Test
    void logsWarningsWhenImportOutcomeContainsErrors() throws IOException {
        Resource testResource = mock(Resource.class);
        when(testResource.getFilename()).thenReturn("test-org-workflows.yaml");
        when(testResource.getInputStream()).thenReturn(new ByteArrayResource(new byte[0]).getInputStream());
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{testResource});
        when(importUseCase.execute(eq("test-org"), any(InputStream.class)))
                .thenReturn(new ImportOutcome(0, 0, List.of("Invalid workflow topology")));

        assertThatCode(() -> importer.loadDefaults()).doesNotThrowAnyException();
        verify(importUseCase).execute(eq("test-org"), any(InputStream.class));
    }

    private static Resource bundledTestbedFile() {
        return new ClassPathResource("default-workflows/" + TESTBED_FILE);
    }
}
