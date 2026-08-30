package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.workflow.common.ValidationException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The validator is cross-aggregate, so every test has to say what the organization's catalog
 * contains. {@link #catalog} is the shorthand: it stubs the four repositories to know exactly the
 * roles, artifacts, tools and tasks named, and nothing else — which is what makes "unknown id"
 * cases a matter of simply not listing the id.
 *
 * <p>Note the asymmetry the fixtures make visible: a workflow references its catalog through
 * {@code ...Use} objects, while a task definition references artifacts and roles by bare id. The
 * two levels are what the Definition/Use split is.
 */
class WorkflowValidatorTest {

    private RoleDefinitionRepository roleRepository;
    private ArtifactDefinitionRepository artifactRepository;
    private ToolDefinitionRepository toolRepository;
    private TaskDefinitionRepository taskRepository;
    private WorkflowValidator validator;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleDefinitionRepository.class);
        artifactRepository = mock(ArtifactDefinitionRepository.class);
        toolRepository = mock(ToolDefinitionRepository.class);
        taskRepository = mock(TaskDefinitionRepository.class);
        validator = new WorkflowValidator(roleRepository, artifactRepository, toolRepository, taskRepository);
    }

    @Test
    void acceptsAProcessWhoseReferencesAllResolve() {
        catalog(List.of("developer"), List.of("spec"), List.of("ci"), List.of(task("code", "developer"), task("review", "developer")));
        Workflow process = process(List.of("developer"), List.of("spec"), List.of("ci"),
                assignment("code", "developer"), assignment("review", "developer", "code"));

        assertThatCode(() -> validator.validate(process)).doesNotThrowAnyException();
    }

    @Test
    void rejectsARoleReferenceTheOrganizationDoesNotHave() {
        catalog(List.of(), List.of(), List.of(), List.of());
        Workflow process = process(List.of("ghost"), List.of(), List.of());

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No role definition with id 'ghost'");
    }

    @Test
    void rejectsAnArtifactReferenceTheOrganizationDoesNotHave() {
        catalog(List.of(), List.of(), List.of(), List.of());
        Workflow process = process(List.of(), List.of("ghost"), List.of());

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No artifact definition with id 'ghost'");
    }

    @Test
    void rejectsAToolReferenceTheOrganizationDoesNotHave() {
        catalog(List.of(), List.of(), List.of(), List.of());
        Workflow process = process(List.of(), List.of(), List.of("ghost"));

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No tool definition with id 'ghost'");
    }

    @Test
    void rejectsAnAssignmentOfATaskTheOrganizationDoesNotHave() {
        catalog(List.of("developer"), List.of(), List.of(), List.of());
        Workflow process = process(List.of("developer"), List.of(), List.of(), assignment("ghost", "developer"));

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No task definition with id 'ghost'");
    }

    @Test
    void rejectsAnAssignmentWithNoPerformedBy() {
        catalog(List.of("developer"), List.of(), List.of(), List.of(task("code", "developer")));
        Workflow process = process(List.of("developer"), List.of(), List.of(), assignment("code", null));

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("has no performedBy role");
    }

    @Test
    void rejectsAnAssignmentWithABlankPerformedBy() {
        catalog(List.of("developer"), List.of(), List.of(), List.of(task("code", "developer")));
        Workflow process = process(List.of("developer"), List.of(), List.of(), assignment("code", "  "));

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("has no performedBy role");
    }

    @Test
    void rejectsAPerformedByRoleTheProcessDoesNotDeclare() {
        catalog(List.of("developer", "reviewer"), List.of(), List.of(), List.of(task("code", "developer", "reviewer")));
        Workflow process = process(List.of("developer"), List.of(), List.of(), assignment("code", "reviewer"));

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("which the workflow does not declare in roles");
    }

    @Test
    void rejectsAPerformedByRoleTheTaskDoesNotOffer() {
        catalog(List.of("developer", "reviewer"), List.of(), List.of(), List.of(task("code", "developer")));
        Workflow process = process(List.of("developer", "reviewer"), List.of(), List.of(), assignment("code", "reviewer"));

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be performed by 'reviewer'");
    }

    /** A task with a null {@code performedByRoles} offers nobody, rather than everybody. */
    @Test
    void rejectsAPerformedByRoleWhenTheTaskOffersNone() {
        TaskDefinition task = TaskDefinition.builder().id("code").name("Write code").build();
        task.setPerformedByRoles(null);
        catalog(List.of("developer"), List.of(), List.of(), List.of(task));
        Workflow process = process(List.of("developer"), List.of(), List.of(), assignment("code", "developer"));

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be performed by 'developer'");
    }

    @Test
    void rejectsAnAssignmentThatDependsOnItself() {
        catalog(List.of("developer"), List.of(), List.of(), List.of(task("code", "developer")));
        Workflow process = process(List.of("developer"), List.of(), List.of(), assignment("code", "developer", "code"));

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot depend on itself");
    }

    @Test
    void rejectsADependsOnThatNamesATaskThisProcessDoesNotAssign() {
        catalog(List.of("developer"), List.of(), List.of(), List.of(task("review", "developer")));
        Workflow process = process(List.of("developer"), List.of(), List.of(), assignment("review", "developer", "code"));

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("which this workflow does not use");
    }

    /** A null {@code dependsOn} is the same as an empty one — the process simply waits for nothing. */
    @Test
    void acceptsAnAssignmentWithANullDependsOn() {
        catalog(List.of("developer"), List.of(), List.of(), List.of(task("code", "developer")));
        TaskUse assignment = assignment("code", "developer");
        assignment.setDependsOn(null);
        Workflow process = process(List.of("developer"), List.of(), List.of(), assignment);

        assertThatCode(() -> validator.validate(process)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTheSameTaskAssignedTwice() {
        catalog(List.of("developer"), List.of(), List.of(), List.of(task("code", "developer")));
        Workflow process = process(List.of("developer"), List.of(), List.of(),
                assignment("code", "developer"), assignment("code", "developer"));

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Duplicate task use 'code'");
    }

    @Test
    void rejectsADuplicateRoleReference() {
        catalog(List.of("developer"), List.of(), List.of(), List.of());
        Workflow process = process(List.of("developer", "developer"), List.of(), List.of());

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Duplicate role use 'developer'");
    }

    @Test
    void rejectsADuplicateArtifactReference() {
        catalog(List.of(), List.of("spec"), List.of(), List.of());
        Workflow process = process(List.of(), List.of("spec", "spec"), List.of());

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Duplicate artifact use 'spec'");
    }

    @Test
    void rejectsADuplicateToolReference() {
        catalog(List.of(), List.of(), List.of("ci"), List.of());
        Workflow process = process(List.of(), List.of(), List.of("ci", "ci"));

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Duplicate tool use 'ci'");
    }

    /**
     * A task's artifacts have to appear in the workflow's own list. Otherwise {@code artifacts} would
     * not be the whole picture of what flows through the workflow, and an instance would create
     * artifact instances the definition never mentioned.
     */
    @Test
    void rejectsATaskWhoseArtifactTheWorkflowDoesNotDeclare() {
        catalog(List.of("developer"), List.of("spec"), List.of(),
                List.of(taskWithArtifacts("code", "developer", List.of("spec"), List.of("binary"))));
        Workflow workflow = process(List.of("developer"), List.of("spec"), List.of(), assignment("code", "developer"));

        assertThatThrownBy(() -> validator.validate(workflow))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Task 'code' uses artifact 'binary', which the workflow does not declare");
    }

    @Test
    void acceptsATaskWhoseArtifactsTheWorkflowDeclares() {
        catalog(List.of("developer"), List.of("spec", "binary"), List.of(),
                List.of(taskWithArtifacts("code", "developer", List.of("spec"), List.of("binary"))));
        Workflow workflow = process(List.of("developer"), List.of("spec", "binary"), List.of(),
                assignment("code", "developer"));

        assertThatCode(() -> validator.validate(workflow)).doesNotThrowAnyException();
    }

    /** Null inputs/outputs mean the task touches no artifact, rather than tripping the check. */
    @Test
    void acceptsATaskWithNullArtifactLists() {
        catalog(List.of("developer"), List.of(), List.of(),
                List.of(taskWithArtifacts("code", "developer", null, null)));
        Workflow workflow = process(List.of("developer"), List.of(), List.of(), assignment("code", "developer"));

        assertThatCode(() -> validator.validate(workflow)).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- start condition

    @Test
    void acceptsAStartConditionWhoseReferencesAllResolve() {
        catalog(List.of("developer"), List.of("spec"), List.of(), List.of(task("code", "developer")));
        Workflow workflow = withStartCondition(
                process(List.of("developer"), List.of("spec"), List.of(), assignment("code", "developer")),
                WorkflowStartCondition.builder()
                        .startType(WorkflowStartConditionType.INPUT_ARTIFACT)
                        .requiredArtifacts(List.of(RequiredStartArtifact.builder()
                                .artifactDefinitionId("spec").state("DRAFT").build()))
                        .authorizedRoles(List.of("developer"))
                        .build());

        assertThatCode(() -> validator.validate(workflow)).doesNotThrowAnyException();
    }

    /** A start condition is optional; a workflow without one is started explicitly. */
    @Test
    void acceptsAWorkflowWithNoStartCondition() {
        catalog(List.of("developer"), List.of(), List.of(), List.of(task("code", "developer")));
        Workflow workflow = process(List.of("developer"), List.of(), List.of(), assignment("code", "developer"));

        assertThatCode(() -> validator.validate(workflow)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAStartConditionWithNoStartType() {
        catalog(List.of("developer"), List.of(), List.of(), List.of(task("code", "developer")));
        Workflow workflow = withStartCondition(
                process(List.of("developer"), List.of(), List.of(), assignment("code", "developer")),
                WorkflowStartCondition.builder().startType(null).build());

        assertThatThrownBy(() -> validator.validate(workflow))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Start condition has no startType");
    }

    @Test
    void rejectsARequiredStartArtifactTheOrganizationDoesNotHave() {
        catalog(List.of("developer"), List.of(), List.of(), List.of(task("code", "developer")));
        Workflow workflow = withStartCondition(
                process(List.of("developer"), List.of(), List.of(), assignment("code", "developer")),
                WorkflowStartCondition.builder()
                        .startType(WorkflowStartConditionType.INPUT_ARTIFACT)
                        .requiredArtifacts(List.of(RequiredStartArtifact.builder()
                                .artifactDefinitionId("ghost").build()))
                        .build());

        assertThatThrownBy(() -> validator.validate(workflow))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No artifact definition with id 'ghost'");
    }

    /**
     * Existing in the catalog is not enough: what a workflow starts on is part of what flows through
     * it, so it has to be declared like any other artifact.
     */
    @Test
    void rejectsARequiredStartArtifactTheWorkflowDoesNotDeclare() {
        catalog(List.of("developer"), List.of("spec"), List.of(), List.of(task("code", "developer")));
        Workflow workflow = withStartCondition(
                process(List.of("developer"), List.of(), List.of(), assignment("code", "developer")),
                WorkflowStartCondition.builder()
                        .startType(WorkflowStartConditionType.INPUT_ARTIFACT)
                        .requiredArtifacts(List.of(RequiredStartArtifact.builder()
                                .artifactDefinitionId("spec").build()))
                        .build());

        assertThatThrownBy(() -> validator.validate(workflow))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Start condition requires artifact 'spec', which the workflow does not declare");
    }

    @Test
    void rejectsAnAuthorizedStartRoleTheOrganizationDoesNotHave() {
        catalog(List.of("developer"), List.of(), List.of(), List.of(task("code", "developer")));
        Workflow workflow = withStartCondition(
                process(List.of("developer"), List.of(), List.of(), assignment("code", "developer")),
                WorkflowStartCondition.builder()
                        .startType(WorkflowStartConditionType.ROLE_DEFINITION)
                        .authorizedRoles(List.of("ghost"))
                        .build());

        assertThatThrownBy(() -> validator.validate(workflow))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No role definition with id 'ghost'");
    }

    /** The mechanisms that name nothing leave both collections null, which is not an error. */
    @Test
    void acceptsAStartConditionWithNullCollections() {
        catalog(List.of("developer"), List.of(), List.of(), List.of(task("code", "developer")));
        WorkflowStartCondition condition = WorkflowStartCondition.builder()
                .startType(WorkflowStartConditionType.TIME_BASED_PRECONDITION)
                .milestoneRef("MILESTONE_REACHED")
                .build();
        condition.setRequiredArtifacts(null);
        condition.setAuthorizedRoles(null);
        Workflow workflow = withStartCondition(
                process(List.of("developer"), List.of(), List.of(), assignment("code", "developer")), condition);

        assertThatCode(() -> validator.validate(workflow)).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- fixtures

    private Workflow withStartCondition(Workflow workflow, WorkflowStartCondition condition) {
        workflow.setStartCondition(condition);
        return workflow;
    }

    private void catalog(List<String> roleIds, List<String> artifactIds, List<String> toolIds, List<TaskDefinition> tasks) {
        lenient().when(roleRepository.existsByOrgKeyAndId(eq("acme"), anyString()))
                .thenAnswer(call -> roleIds.contains(call.getArgument(1)));
        lenient().when(artifactRepository.existsByOrgKeyAndId(eq("acme"), anyString()))
                .thenAnswer(call -> artifactIds.contains(call.getArgument(1)));
        lenient().when(toolRepository.existsByOrgKeyAndId(eq("acme"), anyString()))
                .thenAnswer(call -> toolIds.contains(call.getArgument(1)));
        when(taskRepository.findByOrgKeyAndIdIn(eq("acme"), anyList()))
                .thenAnswer(call -> {
                    List<String> requested = call.getArgument(1);
                    return tasks.stream().filter(task -> requested.contains(task.getId())).toList();
                });
    }

    private TaskDefinition task(String id, String... performedByRoles) {
        return TaskDefinition.builder().id(id).name(id).performedByRoles(List.of(performedByRoles)).build();
    }

    private TaskUse assignment(String taskId, String performedBy, String... dependsOn) {
        return TaskUse.builder()
                .taskDefinitionId(taskId).performedBy(performedBy).dependsOn(List.of(dependsOn)).build();
    }

    private Workflow process(List<String> roles, List<String> artifacts, List<String> tools,
                                      TaskUse... tasks) {
        return Workflow.builder()
                .orgKey("acme").id("delivery")
                .roles(roles.stream().map(id -> RoleUse.builder().roleDefinitionId(id).build()).toList())
                .artifacts(artifacts.stream().map(id -> ArtifactUse.builder().artifactDefinitionId(id).build()).toList())
                .tools(tools.stream().map(id -> ToolUse.builder().toolDefinitionId(id).build()).toList())
                .tasks(List.of(tasks))
                .build();
    }

    private TaskDefinition taskWithArtifacts(String id, String role, List<String> inputs, List<String> outputs) {
        return TaskDefinition.builder()
                .id(id).name(id).performedByRoles(List.of(role)).inputs(inputs).outputs(outputs).build();
    }
}
