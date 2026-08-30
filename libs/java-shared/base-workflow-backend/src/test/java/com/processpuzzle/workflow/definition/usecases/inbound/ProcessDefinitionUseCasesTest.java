package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowExtendsValidator;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowValidator;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.adapters.inbound.WorkflowYamlMapper;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ArtifactUse;
import com.processpuzzle.workflow.definition.domain.HttpMethod;
import com.processpuzzle.workflow.definition.domain.JoinType;
import com.processpuzzle.workflow.definition.domain.RequiredStartArtifact;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.RoleUse;
import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.TaskStepType;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ToolOperation;
import com.processpuzzle.workflow.definition.domain.ToolUse;
import com.processpuzzle.workflow.definition.domain.WorkflowStartCondition;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.definition.usecases.outbound.ActiveProcessInstanceExistencePort;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessDefinitionUseCasesTest {

    private static final String ORG = "org-1";

    private WorkflowRepository processRepo;
    private WorkflowValidator validator;
    private WorkflowExtendsValidator extendsValidator;
    private ActiveProcessInstanceExistencePort activePort;
    private RoleDefinitionRepository roleRepo;
    private ArtifactDefinitionRepository artifactRepo;
    private TaskDefinitionRepository taskRepo;
    private ToolDefinitionRepository toolRepo;
    private CatalogReferenceScanner scanner;

    @BeforeEach
    void setUp() {
        processRepo = mock(WorkflowRepository.class);
        validator = mock(WorkflowValidator.class);
        extendsValidator = mock(WorkflowExtendsValidator.class);
        activePort = mock(ActiveProcessInstanceExistencePort.class);
        roleRepo = mock(RoleDefinitionRepository.class);
        artifactRepo = mock(ArtifactDefinitionRepository.class);
        taskRepo = mock(TaskDefinitionRepository.class);
        toolRepo = mock(ToolDefinitionRepository.class);
        scanner = mock(CatalogReferenceScanner.class);
    }

    @Test
    void createProcessDefinition_success() {
        CreateProcessDefinitionUseCase useCase = new CreateProcessDefinitionUseCase(processRepo, validator, extendsValidator);
        Workflow proc = Workflow.builder().id("proc1").build();
        when(processRepo.existsByOrgKeyAndId(ORG, "proc1")).thenReturn(false);
        when(processRepo.save(proc)).thenReturn(proc);

        Workflow result = useCase.create(ORG, proc);
        assertThat(result).isNotNull();
        assertThat(proc.getOrgKey()).isEqualTo(ORG);
        verify(extendsValidator).validate(ORG, "proc1", null);
        verify(validator).validate(proc);
    }

    @Test
    void createProcessDefinition_alreadyExists_throwsConflict() {
        CreateProcessDefinitionUseCase useCase = new CreateProcessDefinitionUseCase(processRepo, validator, extendsValidator);
        Workflow proc = Workflow.builder().id("proc1").build();
        when(processRepo.existsByOrgKeyAndId(ORG, "proc1")).thenReturn(true);

        assertThatThrownBy(() -> useCase.create(ORG, proc))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void findProcessDefinition_successAndNotFound() {
        FindProcessDefinitionUseCase useCase = new FindProcessDefinitionUseCase(processRepo);
        Workflow proc = Workflow.builder().id("proc1").build();
        when(processRepo.findByOrgKeyAndId(ORG, "proc1")).thenReturn(Optional.of(proc));
        when(processRepo.findByOrgKeyAndId(ORG, "unknown")).thenReturn(Optional.empty());

        assertThat(useCase.findByOrgKeyAndId(ORG, "proc1")).isEqualTo(proc);
        assertThatThrownBy(() -> useCase.findByOrgKeyAndId(ORG, "unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findAllProcessDefinitions_executesQuery() {
        FindAllProcessDefinitionsUseCase useCase = new FindAllProcessDefinitionsUseCase(processRepo);
        Workflow proc = Workflow.builder().id("proc1").build();
        when(processRepo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(proc)));

        Page<Workflow> page = useCase.findAll(ORG, "id == 'proc1'", "name,asc", 0, 10);
        assertThat(page.getContent()).containsExactly(proc);
    }

    @Test
    void replaceProcessDefinition_successAndValidation() {
        ReplaceProcessDefinitionUseCase useCase = new ReplaceProcessDefinitionUseCase(processRepo, validator, extendsValidator);
        Workflow existing = Workflow.builder().orgKey(ORG).id("proc1").version(1L).name("Old").build();
        Workflow desired = Workflow.builder().orgKey(ORG).id("proc1").version(1L).name("New").build();

        when(processRepo.findByOrgKeyAndId(ORG, "proc1")).thenReturn(Optional.of(existing));
        when(processRepo.save(existing)).thenReturn(existing);

        Workflow result = useCase.replace(ORG, "proc1", desired);
        assertThat(result.getName()).isEqualTo("New");

        // ID mismatch
        Workflow wrongId = Workflow.builder().orgKey(ORG).id("proc2").build();
        assertThatThrownBy(() -> useCase.replace(ORG, "proc1", wrongId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("immutable");

        // Version mismatch
        Workflow wrongVersion = Workflow.builder().orgKey(ORG).id("proc1").version(2L).build();
        assertThatThrownBy(() -> useCase.replace(ORG, "proc1", wrongVersion))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("concurrently");

        // Not found
        when(processRepo.findByOrgKeyAndId(ORG, "unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.replace(ORG, "unknown", desired))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteProcessDefinition_successAndConflicts() {
        DeleteProcessDefinitionUseCase useCase = new DeleteProcessDefinitionUseCase(processRepo, activePort);
        Workflow existing = Workflow.builder().orgKey(ORG).id("proc1").build();

        when(processRepo.findByOrgKeyAndId(ORG, "proc1")).thenReturn(Optional.of(existing));
        when(activePort.existsActiveInstanceOf(ORG, "proc1")).thenReturn(false);
        when(processRepo.findByOrgKeyAndExtendsProcessId(ORG, "proc1")).thenReturn(List.of());

        useCase.delete(ORG, "proc1");
        verify(processRepo).delete(existing);

        // Active instances conflict
        when(activePort.existsActiveInstanceOf(ORG, "proc1")).thenReturn(true);
        assertThatThrownBy(() -> useCase.delete(ORG, "proc1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("active process instances");

        // Extended by other conflict
        when(activePort.existsActiveInstanceOf(ORG, "proc1")).thenReturn(false);
        when(processRepo.findByOrgKeyAndExtendsProcessId(ORG, "proc1")).thenReturn(List.of(new Workflow()));
        assertThatThrownBy(() -> useCase.delete(ORG, "proc1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("extended by another");

        // Not found
        when(processRepo.findByOrgKeyAndId(ORG, "unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.delete(ORG, "unknown"))
                .isInstanceOf(NotFoundException.class);
    }


    // ---------------------------------------------------------------- catalog CRUD
    // Each of the four definitions is an aggregate of its own, so its use cases take its own
    // repository and its own (orgKey, id) — never a workflow id. That is the whole point of the
    // Definition layer, and these four tests are what would notice if it regressed.

    @Test
    void roleDefinitionUseCases_crud() {
        CreateRoleDefinitionUseCase createUseCase = new CreateRoleDefinitionUseCase(roleRepo);
        FindRoleDefinitionUseCase findUseCase = new FindRoleDefinitionUseCase(roleRepo);
        FindAllRoleDefinitionsUseCase findAllUseCase = new FindAllRoleDefinitionsUseCase(roleRepo);
        ReplaceRoleDefinitionUseCase replaceUseCase = new ReplaceRoleDefinitionUseCase(roleRepo);
        DeleteRoleDefinitionUseCase deleteUseCase = new DeleteRoleDefinitionUseCase(roleRepo, scanner);

        RoleDefinition role = RoleDefinition.builder().orgKey(ORG).id("dev").name("Dev")
                .responsibleFor(new ArrayList<>(List.of("spec"))).build();

        when(roleRepo.existsByOrgKeyAndId(ORG, "dev")).thenReturn(false);
        when(roleRepo.save(role)).thenReturn(role);
        when(roleRepo.findByOrgKeyAndId(ORG, "dev")).thenReturn(Optional.of(role));
        when(roleRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(role));

        assertThat(createUseCase.create(ORG, role).getId()).isEqualTo("dev");

        when(roleRepo.existsByOrgKeyAndId(ORG, "dev")).thenReturn(true);
        assertThatThrownBy(() -> createUseCase.create(ORG, role)).isInstanceOf(ConflictException.class);

        assertThat(findUseCase.findByOrgKeyAndId(ORG, "dev")).isEqualTo(role);
        when(roleRepo.findByOrgKeyAndId(ORG, "unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> findUseCase.findByOrgKeyAndId(ORG, "unknown")).isInstanceOf(NotFoundException.class);

        assertThat(findAllUseCase.findAll(ORG, null, null)).containsExactly(role);

        RoleDefinition desired = RoleDefinition.builder().orgKey(ORG).id("dev").name("Senior Dev")
                .responsibleFor(List.of("spec", "binary")).build();
        RoleDefinition replaced = replaceUseCase.replace(ORG, "dev", desired);
        assertThat(replaced.getName()).isEqualTo("Senior Dev");
        assertThat(replaced.getResponsibleFor()).containsExactly("spec", "binary");

        role.setVersion(1L);
        RoleDefinition wrongVersion = RoleDefinition.builder().orgKey(ORG).id("dev").version(99L).build();
        assertThatThrownBy(() -> replaceUseCase.replace(ORG, "dev", wrongVersion))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified concurrently");

        // A workflow naming the role in roles or in tasks[].performedBy blocks the delete...
        when(scanner.processesUsingRole(ORG, "dev")).thenReturn(List.of("wf-1"));
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "dev"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still used by workflows");

        // ...and so does a task definition merely offering it, which no workflow has to mention.
        when(scanner.processesUsingRole(ORG, "dev")).thenReturn(List.of());
        when(scanner.tasksOfferingRole(ORG, "dev")).thenReturn(List.of("code"));
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "dev"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still offered by tasks");

        when(scanner.tasksOfferingRole(ORG, "dev")).thenReturn(List.of());
        deleteUseCase.delete(ORG, "dev");
        verify(roleRepo).delete(role);

        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "unknown")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void artifactDefinitionUseCases_crud() {
        CreateArtifactDefinitionUseCase createUseCase = new CreateArtifactDefinitionUseCase(artifactRepo);
        FindArtifactDefinitionUseCase findUseCase = new FindArtifactDefinitionUseCase(artifactRepo);
        FindAllArtifactDefinitionsUseCase findAllUseCase = new FindAllArtifactDefinitionsUseCase(artifactRepo);
        ReplaceArtifactDefinitionUseCase replaceUseCase = new ReplaceArtifactDefinitionUseCase(artifactRepo);
        DeleteArtifactDefinitionUseCase deleteUseCase = new DeleteArtifactDefinitionUseCase(artifactRepo, scanner);

        ArtifactDefinition artifact = ArtifactDefinition.builder().orgKey(ORG).id("spec").name("Spec")
                .artifactType(ArtifactType.DOCUMENT).artifactTypeId("spec-doc").build();

        when(artifactRepo.existsByOrgKeyAndId(ORG, "spec")).thenReturn(false);
        when(artifactRepo.save(artifact)).thenReturn(artifact);
        when(artifactRepo.findByOrgKeyAndId(ORG, "spec")).thenReturn(Optional.of(artifact));
        when(artifactRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(artifact));

        assertThat(createUseCase.create(ORG, artifact).getId()).isEqualTo("spec");

        when(artifactRepo.existsByOrgKeyAndId(ORG, "spec")).thenReturn(true);
        assertThatThrownBy(() -> createUseCase.create(ORG, artifact)).isInstanceOf(ConflictException.class);

        assertThat(findUseCase.findByOrgKeyAndId(ORG, "spec")).isEqualTo(artifact);
        when(artifactRepo.findByOrgKeyAndId(ORG, "unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> findUseCase.findByOrgKeyAndId(ORG, "unknown")).isInstanceOf(NotFoundException.class);

        assertThat(findAllUseCase.findAll(ORG, null, null)).containsExactly(artifact);

        ArtifactDefinition desired = ArtifactDefinition.builder().orgKey(ORG).id("spec").name("Specification")
                .artifactType(ArtifactType.ENTITY).artifactTypeId("spec-entity").stateMachineId("sm-spec").build();
        ArtifactDefinition replaced = replaceUseCase.replace(ORG, "spec", desired);
        assertThat(replaced.getName()).isEqualTo("Specification");
        assertThat(replaced.getArtifactType()).isEqualTo(ArtifactType.ENTITY);
        assertThat(replaced.getArtifactTypeId()).isEqualTo("spec-entity");
        assertThat(replaced.getStateMachineId()).isEqualTo("sm-spec");

        // Three independent holders of an artifact reference, so three separate refusals.
        when(scanner.processesUsingArtifact(ORG, "spec")).thenReturn(List.of("wf-1"));
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "spec"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still used by workflows");

        when(scanner.processesUsingArtifact(ORG, "spec")).thenReturn(List.of());
        when(scanner.tasksReferencingArtifact(ORG, "spec")).thenReturn(List.of("code"));
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "spec"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("as an input or output");

        when(scanner.tasksReferencingArtifact(ORG, "spec")).thenReturn(List.of());
        when(scanner.rolesResponsibleForArtifact(ORG, "spec")).thenReturn(List.of("dev"));
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "spec"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still owned by roles [dev] via responsibleFor");

        when(scanner.rolesResponsibleForArtifact(ORG, "spec")).thenReturn(List.of());
        deleteUseCase.delete(ORG, "spec");
        verify(artifactRepo).delete(artifact);

        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "unknown")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void taskDefinitionUseCases_crud() {
        CreateTaskDefinitionUseCase createUseCase = new CreateTaskDefinitionUseCase(taskRepo);
        FindTaskDefinitionUseCase findUseCase = new FindTaskDefinitionUseCase(taskRepo);
        FindAllTaskDefinitionsUseCase findAllUseCase = new FindAllTaskDefinitionsUseCase(taskRepo);
        ReplaceTaskDefinitionUseCase replaceUseCase = new ReplaceTaskDefinitionUseCase(taskRepo);
        DeleteTaskDefinitionUseCase deleteUseCase = new DeleteTaskDefinitionUseCase(taskRepo, scanner);

        TaskDefinition task = TaskDefinition.builder().orgKey(ORG).id("code").name("Write code")
                .performedByRoles(new ArrayList<>(List.of("dev")))
                .inputs(new ArrayList<>(List.of("spec")))
                .steps(new ArrayList<>(List.of(StepDefinition.builder().id("s1").name("Step One")
                        .stepType(TaskStepType.SERVICE_STEP).toolDefinitionId("ci").build())))
                .build();

        when(taskRepo.existsByOrgKeyAndId(ORG, "code")).thenReturn(false);
        when(taskRepo.save(task)).thenReturn(task);
        when(taskRepo.findByOrgKeyAndId(ORG, "code")).thenReturn(Optional.of(task));
        when(taskRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(task));

        assertThat(createUseCase.create(ORG, task).getId()).isEqualTo("code");

        when(taskRepo.existsByOrgKeyAndId(ORG, "code")).thenReturn(true);
        assertThatThrownBy(() -> createUseCase.create(ORG, task)).isInstanceOf(ConflictException.class);

        assertThat(findUseCase.findByOrgKeyAndId(ORG, "code")).isEqualTo(task);
        when(taskRepo.findByOrgKeyAndId(ORG, "unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> findUseCase.findByOrgKeyAndId(ORG, "unknown")).isInstanceOf(NotFoundException.class);

        assertThat(findAllUseCase.findAll(ORG, null, null)).containsExactly(task);

        TaskDefinition desired = TaskDefinition.builder().orgKey(ORG).id("code").name("Write unit tests and code")
                .performedByRoles(List.of("dev", "lead"))
                .inputs(List.of("spec")).outputs(List.of("binary"))
                .preconditionRuleId("rule-pre").postconditionRuleId("rule-post")
                .build();
        TaskDefinition replaced = replaceUseCase.replace(ORG, "code", desired);
        assertThat(replaced.getName()).isEqualTo("Write unit tests and code");
        assertThat(replaced.getPerformedByRoles()).containsExactly("dev", "lead");
        assertThat(replaced.getOutputs()).containsExactly("binary");

        when(scanner.processesAssigningTask(ORG, "code")).thenReturn(List.of("wf-1"));
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "code"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still used by workflows");

        when(scanner.processesAssigningTask(ORG, "code")).thenReturn(List.of());
        deleteUseCase.delete(ORG, "code");
        verify(taskRepo).delete(task);

        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "unknown")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void toolDefinitionUseCases_crud() {
        CreateToolDefinitionUseCase createUseCase = new CreateToolDefinitionUseCase(toolRepo);
        FindToolDefinitionUseCase findUseCase = new FindToolDefinitionUseCase(toolRepo);
        FindAllToolDefinitionsUseCase findAllUseCase = new FindAllToolDefinitionsUseCase(toolRepo);
        ReplaceToolDefinitionUseCase replaceUseCase = new ReplaceToolDefinitionUseCase(toolRepo);
        DeleteToolDefinitionUseCase deleteUseCase = new DeleteToolDefinitionUseCase(toolRepo, scanner);

        ToolDefinition tool = ToolDefinition.builder().orgKey(ORG).id("tool1").name("Tool One")
                .baseUrl("https://example.com").build();

        when(toolRepo.existsByOrgKeyAndId(ORG, "tool1")).thenReturn(false);
        when(toolRepo.save(tool)).thenReturn(tool);
        when(toolRepo.findByOrgKeyAndId(ORG, "tool1")).thenReturn(Optional.of(tool));
        when(toolRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(tool));

        assertThat(createUseCase.create(ORG, tool).getId()).isEqualTo("tool1");

        when(toolRepo.existsByOrgKeyAndId(ORG, "tool1")).thenReturn(true);
        assertThatThrownBy(() -> createUseCase.create(ORG, tool)).isInstanceOf(ConflictException.class);

        assertThat(findUseCase.findByOrgKeyAndId(ORG, "tool1")).isEqualTo(tool);
        when(toolRepo.findByOrgKeyAndId(ORG, "unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> findUseCase.findByOrgKeyAndId(ORG, "unknown")).isInstanceOf(NotFoundException.class);

        assertThat(findAllUseCase.findAll(ORG, null, null)).containsExactly(tool);

        ToolDefinition desired = ToolDefinition.builder().orgKey(ORG).id("tool1").name("Tool Updated").build();
        assertThat(replaceUseCase.replace(ORG, "tool1", desired).getName()).isEqualTo("Tool Updated");

        tool.setVersion(1L);
        ToolDefinition wrongVersion = ToolDefinition.builder().orgKey(ORG).id("tool1").version(99L).build();
        assertThatThrownBy(() -> replaceUseCase.replace(ORG, "tool1", wrongVersion))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified concurrently");

        when(scanner.processesUsingTool(ORG, "tool1")).thenReturn(List.of("wf-1"));
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "tool1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still referenced by workflows");

        // A SERVICE_STEP invoking the tool is a reference too, and one no workflow lists.
        when(scanner.processesUsingTool(ORG, "tool1")).thenReturn(List.of());
        when(scanner.tasksUsingTool(ORG, "tool1")).thenReturn(List.of("code"));
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "tool1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still invoked by steps of tasks");

        when(scanner.tasksUsingTool(ORG, "tool1")).thenReturn(List.of());
        deleteUseCase.delete(ORG, "tool1");
        verify(toolRepo).delete(tool);

        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "unknown")).isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------- import / export

    /**
     * Export then import, with real mappers on both sides: the assertion that matters is that the
     * document the exporter writes is one the importer accepts, section names and all. A mismatch
     * here is exactly the failure mode a seed file hits on startup.
     */
    @Test
    void exportAndImportWorkflows_roundTrip() throws IOException {
        WorkflowYamlMapper yamlMapper = new WorkflowYamlMapper();
        ExportProcessDefinitionUseCase exportUseCase = new ExportProcessDefinitionUseCase(
                processRepo, roleRepo, artifactRepo, toolRepo, taskRepo, yamlMapper);
        ImportProcessDefinitionsUseCase importUseCase = new ImportProcessDefinitionsUseCase(
                processRepo, roleRepo, artifactRepo, toolRepo, taskRepo, validator, yamlMapper);

        RoleDefinition role = RoleDefinition.builder().orgKey(ORG).id("dev").name("Developer")
                .responsibleFor(List.of("code")).build();
        ArtifactDefinition artifact = ArtifactDefinition.builder().orgKey(ORG).id("code").name("Source Code")
                .artifactType(ArtifactType.ENTITY).artifactTypeId("repo").stateMachineId("sm-repo").build();
        ToolDefinition tool = ToolDefinition.builder().orgKey(ORG).id("runner").name("Runner")
                .baseUrl("https://runner.example.com")
                .operations(List.of(ToolOperation.builder().id("run").method(HttpMethod.POST).path("/run").build()))
                .build();
        TaskDefinition task = TaskDefinition.builder().orgKey(ORG).id("impl").name("Implement")
                .performedByRoles(List.of("dev")).inputs(List.of("code")).outputs(List.of("code"))
                .steps(List.of(StepDefinition.builder().id("s1").name("Run CI")
                        .stepType(TaskStepType.SERVICE_STEP).toolDefinitionId("runner").toolOperation("run").build()))
                .build();

        Workflow workflow = Workflow.builder()
                .orgKey(ORG)
                .id("wf-export")
                .name("Exportable Workflow")
                .description("Description")
                .startCondition(WorkflowStartCondition.builder()
                        .startType(WorkflowStartConditionType.INPUT_ARTIFACT)
                        .requiredArtifacts(List.of(RequiredStartArtifact.builder()
                                .artifactDefinitionId("code").state("DRAFT").build()))
                        .build())
                .roles(List.of(RoleUse.builder().roleDefinitionId("dev").build()))
                .artifacts(List.of(ArtifactUse.builder().artifactDefinitionId("code").build()))
                .tools(List.of(ToolUse.builder().toolDefinitionId("runner").build()))
                .tasks(List.of(TaskUse.builder().taskDefinitionId("impl").performedBy("dev")
                        .joinType(JoinType.ANY).build()))
                .build();

        when(processRepo.findByOrgKeyAndId(ORG, "wf-export")).thenReturn(Optional.of(workflow));
        when(roleRepo.findByOrgKeyAndIdIn(eq(ORG), anyList())).thenReturn(List.of(role));
        when(artifactRepo.findByOrgKeyAndIdIn(eq(ORG), anyList())).thenReturn(List.of(artifact));
        when(taskRepo.findByOrgKeyAndIdIn(eq(ORG), anyList())).thenReturn(List.of(task));
        when(toolRepo.findByOrgKeyAndId(ORG, "runner")).thenReturn(Optional.of(tool));

        byte[] exportedYaml = exportUseCase.execute(ORG, "wf-export");
        String yaml = new String(exportedYaml, StandardCharsets.UTF_8);

        // The five section names are part of the contract, not an implementation detail.
        assertThat(yaml)
                .contains("role-definitions:")
                .contains("artifact-definitions:")
                .contains("tool-definitions:")
                .contains("task-definitions:")
                .contains("workflows:")
                .contains("roleDefinitionId: \"dev\"")
                .contains("artifactType: \"ENTITY\"")
                .contains("stepType: \"SERVICE_STEP\"")
                .contains("startType: \"INPUT_ARTIFACT\"")
                .contains("joinType: \"ANY\"");

        // Feed it straight back: nothing exists yet, so everything is a create and nothing errors.
        when(processRepo.findByOrgKey(ORG)).thenReturn(List.of());
        when(processRepo.findByOrgKeyAndId(ORG, "wf-export")).thenReturn(Optional.empty());
        when(roleRepo.findByOrgKeyAndId(ORG, "dev")).thenReturn(Optional.empty());
        when(artifactRepo.findByOrgKeyAndId(ORG, "code")).thenReturn(Optional.empty());
        when(taskRepo.findByOrgKeyAndId(ORG, "impl")).thenReturn(Optional.empty());
        when(toolRepo.findByOrgKeyAndId(ORG, "runner")).thenReturn(Optional.empty());

        ImportOutcome outcome = importUseCase.execute(ORG, new ByteArrayInputStream(exportedYaml));
        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).isEqualTo(5);
        assertThat(outcome.updated()).isZero();

        ArgumentCaptor<Workflow> saved = ArgumentCaptor.forClass(Workflow.class);
        verify(processRepo).save(saved.capture());
        Workflow reimported = saved.getValue();
        assertThat(reimported.roleDefinitionIds()).containsExactly("dev");
        assertThat(reimported.artifactDefinitionIds()).containsExactly("code");
        assertThat(reimported.toolDefinitionIds()).containsExactly("runner");
        assertThat(reimported.getTasks()).singleElement().satisfies(use -> {
            assertThat(use.getTaskDefinitionId()).isEqualTo("impl");
            assertThat(use.getJoinType()).isEqualTo(JoinType.ANY);
        });
        assertThat(reimported.getStartCondition().getStartType())
                .isEqualTo(WorkflowStartConditionType.INPUT_ARTIFACT);
        assertThat(reimported.getStartCondition().getRequiredArtifacts()).singleElement()
                .satisfies(required -> assertThat(required.getState()).isEqualTo("DRAFT"));
    }

    @Test
    void importWorkflows_rejectsStructurallyBrokenFiles() throws IOException {
        ImportProcessDefinitionsUseCase importUseCase = new ImportProcessDefinitionsUseCase(
                processRepo, roleRepo, artifactRepo, toolRepo, taskRepo, validator, new WorkflowYamlMapper());
        when(processRepo.findByOrgKey(ORG)).thenReturn(List.of());

        assertThat(importOf(importUseCase, """
                workflows:
                  - id: wf1
                    name: WF1
                  - id: wf1
                    name: WF1 Duplicate
                """)).anyMatch(error -> error.contains("Duplicate workflow id"));

        assertThat(importOf(importUseCase, """
                workflows:
                  - name: No ID
                """)).anyMatch(error -> error.contains("missing 'id'"));

        assertThat(importOf(importUseCase, """
                workflows:
                  - id: a
                    name: A
                    extends: b
                  - id: b
                    name: B
                    extends: a
                """)).anyMatch(error -> error.contains("is part of an extends cycle"));

        assertThat(importOf(importUseCase, """
                artifact-definitions:
                  - id: code
                    name: Code
                    artifactType: NOT_A_TYPE
                """)).anyMatch(error -> error.contains("unknown artifactType 'NOT_A_TYPE'"));

        assertThat(importOf(importUseCase, """
                task-definitions:
                  - id: impl
                    name: Implement
                    performedByRoles: [ dev ]
                    steps:
                      - id: s1
                        name: Step
                        stepType: NOT_A_STEP_TYPE
                """)).anyMatch(error -> error.contains("unknown stepType 'NOT_A_STEP_TYPE'"));

        assertThat(importOf(importUseCase, """
                workflows:
                  - id: wf1
                    name: WF1
                    tasks:
                      - taskDefinitionId: impl
                        performedBy: dev
                        joinType: SOMETIMES
                """)).anyMatch(error -> error.contains("unknown joinType 'SOMETIMES'"));

        assertThat(importOf(importUseCase, """
                workflows:
                  - id: wf1
                    name: WF1
                    startCondition:
                      startType: WHENEVER
                """)).anyMatch(error -> error.contains("unknown startCondition startType 'WHENEVER'"));

        assertThat(importOf(importUseCase, """
                workflows:
                  - id: wf1
                    name: WF1
                    tasks:
                      - taskDefinitionId: impl
                        performedBy: dev
                        dependsOn: [ impl ]
                """)).anyMatch(error -> error.contains("dependsOn itself"));

        assertThat(importOf(importUseCase, """
                workflows:
                  - id: wf1
                    name: WF1
                    tasks:
                      - taskDefinitionId: impl
                        performedBy: dev
                        dependsOn: [ ghost ]
                """)).anyMatch(error -> error.contains("which the workflow does not use"));

        // A rejected import is all-or-nothing: no section is written.
        verify(processRepo, never()).save(any());
        verify(roleRepo, never()).save(any());
        verify(taskRepo, never()).save(any());
    }

    /**
     * A YAML section name that does not match is silently an empty section, not an error — which is
     * why the round-trip test above asserts the names literally.
     */
    @Test
    void importWorkflows_toleratesAnEmptyDocument() throws IOException {
        ImportProcessDefinitionsUseCase importUseCase = new ImportProcessDefinitionsUseCase(
                processRepo, roleRepo, artifactRepo, toolRepo, taskRepo, validator, new WorkflowYamlMapper());

        ImportOutcome outcome = importUseCase.execute(ORG,
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).isZero();
        assertThat(outcome.updated()).isZero();
    }

    @Test
    void importOutcome_rejectedFactoryReportsNoWrites() {
        ImportOutcome rejected = ImportOutcome.rejected(List.of("Error 1"));

        assertThat(rejected.errors()).containsExactly("Error 1");
        assertThat(rejected.created()).isZero();
        assertThat(rejected.updated()).isZero();
    }

    private List<String> importOf(ImportProcessDefinitionsUseCase useCase, String yaml) throws IOException {
        return useCase.execute(ORG, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).errors();
    }
}
