package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionExtendsValidator;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionValidator;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.WorkProductDefinition;
import com.processpuzzle.workflow.definition.domain.WorkProductType;
import com.processpuzzle.workflow.definition.usecases.outbound.ActiveProcessInstanceExistencePort;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessDefinitionUseCasesTest {

    private static final String ORG = "org-1";

    private ProcessDefinitionRepository processRepo;
    private ProcessDefinitionValidator validator;
    private ProcessDefinitionExtendsValidator extendsValidator;
    private ActiveProcessInstanceExistencePort activePort;
    private com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository toolRepo;

    @BeforeEach
    void setUp() {
        processRepo = mock(ProcessDefinitionRepository.class);
        validator = mock(ProcessDefinitionValidator.class);
        extendsValidator = mock(ProcessDefinitionExtendsValidator.class);
        activePort = mock(ActiveProcessInstanceExistencePort.class);
        toolRepo = mock(com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository.class);
    }

    @Test
    void createProcessDefinition_success() {
        CreateProcessDefinitionUseCase useCase = new CreateProcessDefinitionUseCase(processRepo, validator, extendsValidator);
        ProcessDefinition proc = ProcessDefinition.builder().id("proc1").build();
        when(processRepo.existsByOrgKeyAndId(ORG, "proc1")).thenReturn(false);
        when(processRepo.save(proc)).thenReturn(proc);

        ProcessDefinition result = useCase.create(ORG, proc);
        assertThat(result).isNotNull();
        assertThat(proc.getOrgKey()).isEqualTo(ORG);
        verify(extendsValidator).validate(ORG, "proc1", null);
        verify(validator).validate(proc);
    }

    @Test
    void createProcessDefinition_alreadyExists_throwsConflict() {
        CreateProcessDefinitionUseCase useCase = new CreateProcessDefinitionUseCase(processRepo, validator, extendsValidator);
        ProcessDefinition proc = ProcessDefinition.builder().id("proc1").build();
        when(processRepo.existsByOrgKeyAndId(ORG, "proc1")).thenReturn(true);

        assertThatThrownBy(() -> useCase.create(ORG, proc))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void findProcessDefinition_successAndNotFound() {
        FindProcessDefinitionUseCase useCase = new FindProcessDefinitionUseCase(processRepo);
        ProcessDefinition proc = ProcessDefinition.builder().id("proc1").build();
        when(processRepo.findByOrgKeyAndId(ORG, "proc1")).thenReturn(Optional.of(proc));
        when(processRepo.findByOrgKeyAndId(ORG, "unknown")).thenReturn(Optional.empty());

        assertThat(useCase.findByOrgKeyAndId(ORG, "proc1")).isEqualTo(proc);
        assertThatThrownBy(() -> useCase.findByOrgKeyAndId(ORG, "unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findAllProcessDefinitions_executesQuery() {
        FindAllProcessDefinitionsUseCase useCase = new FindAllProcessDefinitionsUseCase(processRepo);
        ProcessDefinition proc = ProcessDefinition.builder().id("proc1").build();
        when(processRepo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(proc)));

        Page<ProcessDefinition> page = useCase.findAll(ORG, "id == 'proc1'", "name,asc", 0, 10);
        assertThat(page.getContent()).containsExactly(proc);
    }

    @Test
    void replaceProcessDefinition_successAndValidation() {
        ReplaceProcessDefinitionUseCase useCase = new ReplaceProcessDefinitionUseCase(processRepo, validator, extendsValidator);
        ProcessDefinition existing = ProcessDefinition.builder().orgKey(ORG).id("proc1").version(1L).name("Old").build();
        ProcessDefinition desired = ProcessDefinition.builder().orgKey(ORG).id("proc1").version(1L).name("New").build();

        when(processRepo.findByOrgKeyAndId(ORG, "proc1")).thenReturn(Optional.of(existing));
        when(processRepo.save(existing)).thenReturn(existing);

        ProcessDefinition result = useCase.replace(ORG, "proc1", desired);
        assertThat(result.getName()).isEqualTo("New");

        // ID mismatch
        ProcessDefinition wrongId = ProcessDefinition.builder().orgKey(ORG).id("proc2").build();
        assertThatThrownBy(() -> useCase.replace(ORG, "proc1", wrongId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("immutable");

        // Version mismatch
        ProcessDefinition wrongVersion = ProcessDefinition.builder().orgKey(ORG).id("proc1").version(2L).build();
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
        ProcessDefinition existing = ProcessDefinition.builder().orgKey(ORG).id("proc1").build();

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
        when(processRepo.findByOrgKeyAndExtendsProcessId(ORG, "proc1")).thenReturn(List.of(new ProcessDefinition()));
        assertThatThrownBy(() -> useCase.delete(ORG, "proc1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("extended by another");

        // Not found
        when(processRepo.findByOrgKeyAndId(ORG, "unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.delete(ORG, "unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void roleDefinitionUseCases_createReplaceDelete() {
        CreateRoleDefinitionUseCase createUseCase = new CreateRoleDefinitionUseCase(processRepo);
        ReplaceRoleDefinitionUseCase replaceUseCase = new ReplaceRoleDefinitionUseCase(processRepo);
        DeleteRoleDefinitionUseCase deleteUseCase = new DeleteRoleDefinitionUseCase(processRepo);

        RoleDefinition r1 = RoleDefinition.builder().technicalId(java.util.UUID.randomUUID()).id("dev").name("Dev").build();
        ProcessDefinition proc = ProcessDefinition.builder().orgKey(ORG).id("proc1")
                .roles(new ArrayList<>(List.of(r1))).build();

        when(processRepo.findByOrgKeyAndId(ORG, "proc1")).thenReturn(Optional.of(proc));
        when(processRepo.save(proc)).thenReturn(proc);

        // Create new role
        RoleDefinition r2 = RoleDefinition.builder().technicalId(java.util.UUID.randomUUID()).id("qa").name("QA").build();
        RoleDefinition created = createUseCase.create(ORG, "proc1", r2);
        assertThat(created).isEqualTo(r2);

        // Create duplicate role -> conflict
        assertThatThrownBy(() -> createUseCase.create(ORG, "proc1", r1))
                .isInstanceOf(ConflictException.class);

        // Replace role
        RoleDefinition r1Updated = RoleDefinition.builder().technicalId(r1.getTechnicalId()).id("dev").name("Senior Dev").build();
        RoleDefinition replaced = replaceUseCase.replace(ORG, "proc1", "dev", r1Updated);
        assertThat(replaced.getName()).isEqualTo("Senior Dev");

        // Replace non-existent role
        assertThatThrownBy(() -> replaceUseCase.replace(ORG, "proc1", "unknown", r1Updated))
                .isInstanceOf(NotFoundException.class);

        // Delete role conflict (performedBy a task)
        TaskDefinition performingTask = TaskDefinition.builder().id("task1").performedBy("dev").build();
        proc.setTasks(List.of(performingTask));
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "proc1", "dev"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still performedBy tasks");

        // Delete role
        proc.setTasks(List.of());
        deleteUseCase.delete(ORG, "proc1", "qa");
        assertThat(proc.findRole("qa")).isEmpty();

        // Delete non-existent role
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "proc1", "unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void taskDefinitionUseCases_createReplaceDelete() {
        CreateTaskDefinitionUseCase createUseCase = new CreateTaskDefinitionUseCase(processRepo, validator);
        ReplaceTaskDefinitionUseCase replaceUseCase = new ReplaceTaskDefinitionUseCase(processRepo, validator);
        DeleteTaskDefinitionUseCase deleteUseCase = new DeleteTaskDefinitionUseCase(processRepo);

        TaskDefinition t1 = TaskDefinition.builder().technicalId(java.util.UUID.randomUUID()).id("code").name("Write code").build();
        ProcessDefinition proc = ProcessDefinition.builder().orgKey(ORG).id("proc1")
                .tasks(new ArrayList<>(List.of(t1))).build();

        when(processRepo.findByOrgKeyAndId(ORG, "proc1")).thenReturn(Optional.of(proc));
        when(processRepo.save(proc)).thenReturn(proc);

        // Create task
        TaskDefinition t2 = TaskDefinition.builder().technicalId(java.util.UUID.randomUUID()).id("test").name("Test code").build();
        TaskDefinition created = createUseCase.create(ORG, "proc1", t2);
        assertThat(created).isEqualTo(t2);

        // Duplicate task
        assertThatThrownBy(() -> createUseCase.create(ORG, "proc1", t1))
                .isInstanceOf(ConflictException.class);

        // Replace task
        TaskDefinition t1Updated = TaskDefinition.builder().technicalId(t1.getTechnicalId()).id("code").name("Write unit tests and code").build();
        TaskDefinition replaced = replaceUseCase.replace(ORG, "proc1", "code", t1Updated);
        assertThat(replaced.getName()).isEqualTo("Write unit tests and code");

        // Replace non-existent task
        assertThatThrownBy(() -> replaceUseCase.replace(ORG, "proc1", "unknown", t1Updated))
                .isInstanceOf(NotFoundException.class);

        // Delete task conflict (depended on by another task)
        TaskDefinition dependentTask = TaskDefinition.builder().id("task2").dependsOn(List.of("test")).build();
        proc.getTasks().add(dependentTask);
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "proc1", "test"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still a dependency of tasks");

        // Delete task
        proc.getTasks().remove(dependentTask);
        deleteUseCase.delete(ORG, "proc1", "test");
        assertThat(proc.findTask("test")).isEmpty();

        // Delete non-existent task
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "proc1", "unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void toolDefinitionUseCases_crud() {
        CreateToolDefinitionUseCase createUseCase = new CreateToolDefinitionUseCase(toolRepo);
        FindToolDefinitionUseCase findUseCase = new FindToolDefinitionUseCase(toolRepo);
        FindAllToolDefinitionsUseCase findAllUseCase = new FindAllToolDefinitionsUseCase(toolRepo);
        ReplaceToolDefinitionUseCase replaceUseCase = new ReplaceToolDefinitionUseCase(toolRepo);
        DeleteToolDefinitionUseCase deleteUseCase = new DeleteToolDefinitionUseCase(toolRepo, processRepo);

        ToolDefinition tool = ToolDefinition.builder().orgKey(ORG).id("tool1").name("Tool One").baseUrl("https://example.com").build();

        when(toolRepo.existsByOrgKeyAndId(ORG, "tool1")).thenReturn(false);
        when(toolRepo.save(tool)).thenReturn(tool);
        when(toolRepo.findByOrgKeyAndId(ORG, "tool1")).thenReturn(Optional.of(tool));
        when(toolRepo.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(tool));
        when(processRepo.findByOrgKey(ORG)).thenReturn(List.of());

        ToolDefinition created = createUseCase.create(ORG, tool);
        assertThat(created.getId()).isEqualTo("tool1");

        // duplicate tool
        when(toolRepo.existsByOrgKeyAndId(ORG, "tool1")).thenReturn(true);
        assertThatThrownBy(() -> createUseCase.create(ORG, tool))
                .isInstanceOf(ConflictException.class);

        // find
        assertThat(findUseCase.findByOrgKeyAndId(ORG, "tool1")).isEqualTo(tool);
        when(toolRepo.findByOrgKeyAndId(ORG, "unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> findUseCase.findByOrgKeyAndId(ORG, "unknown"))
                .isInstanceOf(NotFoundException.class);

        // findAll
        assertThat(findAllUseCase.findAll(ORG, null, null)).containsExactly(tool);

        // replace
        ToolDefinition desired = ToolDefinition.builder().orgKey(ORG).id("tool1").name("Tool Updated").build();
        ToolDefinition replaced = replaceUseCase.replace(ORG, "tool1", desired);
        assertThat(replaced.getName()).isEqualTo("Tool Updated");

        // replace version mismatch
        ToolDefinition wrongVersion = ToolDefinition.builder().orgKey(ORG).id("tool1").version(99L).build();
        tool.setVersion(1L);
        assertThatThrownBy(() -> replaceUseCase.replace(ORG, "tool1", wrongVersion))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified concurrently");

        // delete - tool still referenced by a process definition
        ProcessDefinition procWithTool = ProcessDefinition.builder().orgKey(ORG).id("procWithTool").tools(List.of("tool1")).build();
        when(processRepo.findByOrgKey(ORG)).thenReturn(List.of(procWithTool));
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "tool1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still referenced by");

        // delete - success
        when(processRepo.findByOrgKey(ORG)).thenReturn(List.of());
        deleteUseCase.delete(ORG, "tool1");
        verify(toolRepo).delete(tool);

        // delete unknown
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void exportAndImportProcessDefinitions_roundTripAndErrors() throws IOException {
        ExportProcessDefinitionUseCase exportUseCase = new ExportProcessDefinitionUseCase(processRepo);
        ImportProcessDefinitionsUseCase importUseCase = new ImportProcessDefinitionsUseCase(processRepo, validator);

        RoleDefinition r = RoleDefinition.builder().id("dev").name("Developer").build();
        WorkProductDefinition wp = WorkProductDefinition.builder().id("code").name("Source Code").type(WorkProductType.ARTIFACT).build();
        TaskDefinition t = TaskDefinition.builder().id("impl").name("Implement").performedBy("dev").build();

        ProcessDefinition proc = ProcessDefinition.builder()
                .orgKey(ORG)
                .id("proc-export")
                .name("Exportable Process")
                .description("Description")
                .roles(List.of(r))
                .workProducts(List.of(wp))
                .tasks(List.of(t))
                .tools(List.of("runner"))
                .build();

        when(processRepo.findByOrgKeyAndId(ORG, "proc-export")).thenReturn(Optional.of(proc));

        byte[] exportedYaml = exportUseCase.execute(ORG, "proc-export");
        assertThat(exportedYaml).isNotEmpty();

        // Import exported yaml
        when(processRepo.findByOrgKey(ORG)).thenReturn(List.of());
        when(processRepo.findByOrgKeyAndId(ORG, "proc-export")).thenReturn(Optional.empty());

        ImportOutcome outcome = importUseCase.execute(ORG, new ByteArrayInputStream(exportedYaml));
        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).isEqualTo(1);

        // Test rejected static factory
        ImportOutcome rejected = ImportOutcome.rejected(List.of("Error 1"));
        assertThat(rejected.errors()).containsExactly("Error 1");
        assertThat(rejected.created()).isEqualTo(0);

        // Import with duplicate id in file
        String duplicateYaml = """
                processes:
                  - id: p1
                    name: P1
                  - id: p1
                    name: P1 Duplicate
                """;
        ImportOutcome dupOutcome = importUseCase.execute(ORG, new ByteArrayInputStream(duplicateYaml.getBytes(StandardCharsets.UTF_8)));
        assertThat(dupOutcome.errors()).anyMatch(e -> e.contains("Duplicate process id"));

        // Import with missing id
        String missingIdYaml = """
                processes:
                  - name: No ID
                """;
        ImportOutcome missingOutcome = importUseCase.execute(ORG, new ByteArrayInputStream(missingIdYaml.getBytes(StandardCharsets.UTF_8)));
        assertThat(missingOutcome.errors()).anyMatch(e -> e.contains("missing 'id'"));

        // Import with cyclic extends
        String cyclicYaml = """
                processes:
                  - id: a
                    name: A
                    extends: b
                  - id: b
                    name: B
                    extends: a
                """;
        // Import with rich YAML and update existing
        String richYaml = """
                processes:
                  - id: proc-export
                    name: Updated Process
                    description: Updated Desc
                    tools:
                      - runner
                    roles:
                      - id: dev
                        name: Lead Dev
                        entityRoleId: lead-dev
                    workProducts:
                      - id: code
                        name: Code Repo
                        type: ARTIFACT
                        entityTypeId: Repo
                        stateMachineId: sm-repo
                    tasks:
                      - id: impl
                        name: Implement Feature
                        performedBy: dev
                        inputs:
                          - refId: code
                            type: DOCUMENT
                            label: Input Code
                        outputs:
                          - refId: code
                            type: DOCUMENT
                            label: Output Code
                        preconditionRuleId: rule-pre
                        postconditionRuleId: rule-post
                        parallel: true
                        override: true
                        dependsOn: []
                        steps:
                          - id: s1
                            name: Step 1
                            toolId: runner
                            toolOperation: exec
                            inputMapping:
                              k: v
                            outputMapping:
                              out: res
                """;

        ProcessDefinition existingProc = ProcessDefinition.builder().orgKey(ORG).id("proc-export").build();
        when(processRepo.findByOrgKeyAndId(ORG, "proc-export")).thenReturn(Optional.of(existingProc));

        ImportOutcome richOutcome = importUseCase.execute(ORG, new ByteArrayInputStream(richYaml.getBytes(StandardCharsets.UTF_8)));
        assertThat(richOutcome.errors()).isEmpty();
        assertThat(richOutcome.updated()).isEqualTo(1);

        // Import invalid role/task structures
        String invalidRoleTaskYaml = """
                processes:
                  - id: p-invalid
                    name: Invalid P
                    roles:
                      - name: Role Without ID
                      - id: r1
                        name: R1
                      - id: r1
                        name: R1 Dup
                    tasks:
                      - name: Task Without ID
                      - id: t1
                        name: T1
                        performedBy: unknown-role
                        dependsOn:
                          - unknown-task
                      - id: t1
                        name: T1 Dup
                """;
        ImportOutcome invOutcome = importUseCase.execute(ORG, new ByteArrayInputStream(invalidRoleTaskYaml.getBytes(StandardCharsets.UTF_8)));
        assertThat(invOutcome.errors()).hasSizeGreaterThan(3);
    }
}
