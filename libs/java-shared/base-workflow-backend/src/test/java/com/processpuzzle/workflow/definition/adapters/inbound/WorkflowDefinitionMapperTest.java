package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.shared.model.ImportResult;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.ArtifactUse;
import com.processpuzzle.workflow.definition.domain.HttpMethod;
import com.processpuzzle.workflow.definition.domain.JoinType;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleUse;
import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskStepType;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.workflow.definition.usecases.outbound.ActiveWorkflowInstanceExistencePort;
import com.processpuzzle.workflow.model.ArtifactDefinitionInput;
import com.processpuzzle.workflow.model.PageOfWorkflow;
import com.processpuzzle.workflow.model.RoleDefinitionInput;
import com.processpuzzle.workflow.model.TaskDefinitionInput;
import com.processpuzzle.workflow.model.TaskStepDefinition;
import com.processpuzzle.workflow.model.ToolDefinitionInput;
import com.processpuzzle.workflow.model.ToolOperationDefinition;
import com.processpuzzle.workflow.model.WorkflowInput;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowDefinitionMapperTest {

    private ActiveWorkflowInstanceExistencePort existencePort;
    private WorkflowDefinitionMapper mapper;

    @BeforeEach
    void setUp() {
        existencePort = mock(ActiveWorkflowInstanceExistencePort.class);
        mapper = new WorkflowDefinitionMapper(existencePort);
    }

    /**
     * A workflow carries uses of the catalog and its own start condition, not the definitions
     * themselves — so the round trip is worth asserting field by field: this is the shape the whole
     * Definition/Use split rests on.
     */
    @Test
    void toDomain_and_toModel_workflow() {
        WorkflowInput input = new WorkflowInput()
                .id("wf-1")
                .name("Workflow 1")
                .description("Desc")
                ._extends("parent-wf")
                .startCondition(new com.processpuzzle.workflow.model.WorkflowStartCondition()
                        .startType(com.processpuzzle.workflow.model.WorkflowStartConditionType.INPUT_ARTIFACT)
                        .requiredArtifacts(List.of(new com.processpuzzle.workflow.model.RequiredStartArtifact()
                                .artifactDefinitionId("wp1").state("DRAFT"))))
                .roles(List.of(new com.processpuzzle.workflow.model.RoleUse().roleDefinitionId("r1")))
                .artifacts(List.of(new com.processpuzzle.workflow.model.ArtifactUse().artifactDefinitionId("wp1")))
                .tools(List.of(new com.processpuzzle.workflow.model.ToolUse().toolDefinitionId("tool-1")))
                .tasks(List.of(new com.processpuzzle.workflow.model.TaskUse()
                        .taskDefinitionId("t1")
                        .performedBy("r1")
                        .dependsOn(List.of("t0"))
                        .joinType(com.processpuzzle.workflow.model.JoinType.ANY)
                        .parallel(true)
                        .override(true)));

        Workflow domain = mapper.toDomain("org-1", input);

        assertThat(domain.getOrgKey()).isEqualTo("org-1");
        assertThat(domain.getId()).isEqualTo("wf-1");
        assertThat(domain.getName()).isEqualTo("Workflow 1");
        assertThat(domain.getDescription()).isEqualTo("Desc");
        assertThat(domain.getExtendsWorkflowId()).isEqualTo("parent-wf");
        assertThat(domain.getVersion()).isNull(); // the input above carries none
        assertThat(domain.roleDefinitionIds()).containsExactly("r1");
        assertThat(domain.artifactDefinitionIds()).containsExactly("wp1");
        assertThat(domain.toolDefinitionIds()).containsExactly("tool-1");
        assertThat(domain.getStartCondition().getStartType()).isEqualTo(WorkflowStartConditionType.INPUT_ARTIFACT);
        assertThat(domain.getStartCondition().getRequiredArtifacts()).singleElement()
                .satisfies(required -> {
                    assertThat(required.getArtifactDefinitionId()).isEqualTo("wp1");
                    assertThat(required.getState()).isEqualTo("DRAFT");
                });
        assertThat(domain.getTasks()).hasSize(1);
        TaskUse use = domain.getTasks().get(0);
        assertThat(use.getTaskDefinitionId()).isEqualTo("t1");
        assertThat(use.getPerformedBy()).isEqualTo("r1");
        assertThat(use.getDependsOn()).containsExactly("t0");
        assertThat(use.getJoinType()).isEqualTo(JoinType.ANY);
        assertThat(use.isParallel()).isTrue();
        assertThat(use.isOverride()).isTrue();

        domain.setCreatedAt(Instant.now());
        domain.setUpdatedAt(Instant.now());
        domain.setVersion(1L);

        var model = mapper.toModel(domain);
        assertThat(model.getId()).isEqualTo("wf-1");
        assertThat(model.getName()).isEqualTo("Workflow 1");
        assertThat(model.getExtends()).isEqualTo("parent-wf");
        assertThat(model.getRoles()).singleElement()
                .extracting(com.processpuzzle.workflow.model.RoleUse::getRoleDefinitionId).isEqualTo("r1");
        assertThat(model.getArtifacts()).singleElement()
                .extracting(com.processpuzzle.workflow.model.ArtifactUse::getArtifactDefinitionId).isEqualTo("wp1");
        assertThat(model.getTools()).singleElement()
                .extracting(com.processpuzzle.workflow.model.ToolUse::getToolDefinitionId).isEqualTo("tool-1");
        assertThat(model.getStartCondition().getRequiredArtifacts()).hasSize(1);
        assertThat(model.getTasks()).hasSize(1);
        assertThat(model.getTasks().get(0).getTaskDefinitionId()).isEqualTo("t1");
        assertThat(model.getTasks().get(0).getDependsOn()).containsExactly("t0");
        assertThat(model.getTasks().get(0).getJoinType())
                .isEqualTo(com.processpuzzle.workflow.model.JoinType.ANY);
        assertThat(model.getVersion()).isEqualTo(1L);
        assertThat(model.getCreatedAt()).isNotNull();
        assertThat(model.getUpdatedAt()).isNotNull();
    }

    /**
     * Generated inputs pre-fill list defaults, so an explicitly nulled collection is the only way to
     * reach the mapper's null branches. A start condition is genuinely optional — a workflow without
     * one can only be started explicitly through /instances.
     */
    @Test
    void toDomain_workflowToleratesAbsentCollectionsAndStartCondition() {
        WorkflowInput emptyInput = new WorkflowInput().id("empty").name("Empty")
                .startCondition(null).roles(null).artifacts(null).tools(null).tasks(null);

        Workflow emptyDomain = mapper.toDomain("org-1", emptyInput);

        assertThat(emptyDomain.getRoles()).isEmpty();
        assertThat(emptyDomain.getArtifacts()).isEmpty();
        assertThat(emptyDomain.getTools()).isEmpty();
        assertThat(emptyDomain.getTasks()).isEmpty();
        assertThat(emptyDomain.getStartCondition()).isNull();
        assertThat(mapper.toModel(emptyDomain).getStartCondition()).isNull();
    }

    /** joinType is nullable on both sides and defaults to ALL rather than to null. */
    @Test
    void taskUseMappingDefaultsAnAbsentJoinTypeToAll() {
        Workflow domain = mapper.toDomain("org-1", new WorkflowInput().id("wf-1").name("Workflow 1")
                .tasks(List.of(new com.processpuzzle.workflow.model.TaskUse()
                        .taskDefinitionId("t1").performedBy("r1").joinType(null))));

        assertThat(domain.getTasks().get(0).getJoinType()).isEqualTo(JoinType.ALL);

        Workflow nulled = Workflow.builder().orgKey("org-1").id("wf-1")
                .tasks(List.of(TaskUse.builder().taskDefinitionId("t1").joinType(null).build()))
                .build();
        assertThat(mapper.toModel(nulled).getTasks().get(0).getJoinType())
                .isEqualTo(com.processpuzzle.workflow.model.JoinType.ALL);
    }

    /** A start condition's own collections are nullable too — ROLE_DEFINITION uses none of them. */
    @Test
    void startConditionMappingToleratesAbsentCollections() {
        Workflow domain = mapper.toDomain("org-1", new WorkflowInput().id("wf-1").name("Workflow 1")
                .startCondition(new com.processpuzzle.workflow.model.WorkflowStartCondition()
                        .startType(null)
                        .requiredArtifacts(null)
                        .authorizedRoles(null)));

        assertThat(domain.getStartCondition().getStartType()).isNull();
        assertThat(domain.getStartCondition().getRequiredArtifacts()).isEmpty();
        assertThat(domain.getStartCondition().getAuthorizedRoles()).isNull();

        var model = mapper.toModel(domain).getStartCondition();
        assertThat(model.getStartType()).isNull();
        assertThat(model.getAuthorizedRoles()).isNull();
    }

    /** The TRIGGERING_EVENT and TIME_BASED_PRECONDITION fields ride the same flat schema. */
    @Test
    void startConditionMappingCarriesEveryMechanismsFields() {
        Workflow domain = mapper.toDomain("org-1", new WorkflowInput().id("wf-1").name("Workflow 1")
                .startCondition(new com.processpuzzle.workflow.model.WorkflowStartCondition()
                        .startType(com.processpuzzle.workflow.model.WorkflowStartConditionType.TRIGGERING_EVENT)
                        .eventType("order.submitted")
                        .payloadMapping(Map.of("orderId", "$.id"))
                        .authorizedRoles(List.of("clerk"))
                        .milestoneRef("MILESTONE_REACHED")
                        .preconditionExpression("milestone.status == 'PASSED'")));

        var condition = domain.getStartCondition();
        assertThat(condition.getEventType()).isEqualTo("order.submitted");
        assertThat(condition.getPayloadMapping()).containsEntry("orderId", "$.id");
        assertThat(condition.getAuthorizedRoles()).containsExactly("clerk");
        assertThat(condition.getMilestoneRef()).isEqualTo("MILESTONE_REACHED");
        assertThat(condition.getPreconditionExpression()).isEqualTo("milestone.status == 'PASSED'");

        var model = mapper.toModel(domain).getStartCondition();
        assertThat(model.getStartType())
                .isEqualTo(com.processpuzzle.workflow.model.WorkflowStartConditionType.TRIGGERING_EVENT);
        assertThat(model.getPayloadMapping()).containsEntry("orderId", "$.id");
        assertThat(model.getPreconditionExpression()).isEqualTo("milestone.status == 'PASSED'");
    }

    /**
     * The page carries the <em>full</em> workflow, not a summary: base-entity's generated form reads
     * the record out of the loaded list, so the reference lists have to be on every row. Only
     * {@code activeInstances} is computed per row.
     */
    @Test
    void toModel_pageCarriesFullDefinitions() {
        Workflow domain = Workflow.builder()
                .orgKey("org-1")
                .id("wf-1")
                .name("Workflow 1")
                .description("Desc")
                .version(2L)
                .roles(List.of(RoleUse.builder().roleDefinitionId("dev").build()))
                .build();
        domain.setCreatedAt(Instant.now());
        domain.setUpdatedAt(Instant.now());

        when(existencePort.countActiveInstancesOf("org-1", "wf-1")).thenReturn(3L);

        PageOfWorkflow pageModel = mapper.toModel(new PageImpl<>(List.of(domain), PageRequest.of(0, 10), 1));

        assertThat(pageModel.getContent()).hasSize(1);
        assertThat(pageModel.getTotalElements()).isEqualTo(1);
        com.processpuzzle.workflow.model.Workflow row = pageModel.getContent().get(0);
        assertThat(row.getId()).isEqualTo("wf-1");
        assertThat(row.getActiveInstances()).isEqualTo(3);
        assertThat(row.getRoles()).singleElement()
                .extracting(com.processpuzzle.workflow.model.RoleUse::getRoleDefinitionId).isEqualTo("dev");
    }

    @Test
    void toModel_importOutcome() {
        ImportOutcome outcome = new ImportOutcome(2, 1, List.of("warn"));
        ImportResult result = mapper.toModel(outcome);

        assertThat(result.getCreated()).isEqualTo(2);
        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getErrors()).containsExactly("warn");
    }

    @Test
    void toRoleDomain_and_toRoleModel() {
        RoleDefinitionInput input = new RoleDefinitionInput()
                .id("r1").name("Role 1").description("Role Desc")
                .responsibleFor(List.of("wp1"))
                .entityRoleId("er-1");

        RoleDefinition domain = mapper.toRoleDomain(input);
        domain.setVersion(4L);
        domain.setCreatedAt(Instant.now());
        domain.setUpdatedAt(Instant.now());

        assertThat(domain.getId()).isEqualTo("r1");
        assertThat(domain.getName()).isEqualTo("Role 1");
        assertThat(domain.getDescription()).isEqualTo("Role Desc");
        assertThat(domain.getResponsibleFor()).containsExactly("wp1");
        assertThat(domain.getEntityRoleId()).isEqualTo("er-1");

        var model = mapper.toRoleModel(domain);
        assertThat(model.getId()).isEqualTo("r1");
        assertThat(model.getResponsibleFor()).containsExactly("wp1");
        assertThat(model.getEntityRoleId()).isEqualTo("er-1");
        assertThat(model.getVersion()).isEqualTo(4L);
        assertThat(model.getCreatedAt()).isNotNull();
        assertThat(model.getUpdatedAt()).isNotNull();

        // version travels in as well as out: it is what the replace guard compares against.
        assertThat(mapper.toRoleDomain(new RoleDefinitionInput().id("r1").version(7L)).getVersion()).isEqualTo(7L);
        assertThat(mapper.toRoleDomain(new RoleDefinitionInput().id("r1")).getVersion()).isNull();
    }

    @Test
    void toArtifactDomain_and_toArtifactModel() {
        ArtifactDefinitionInput input = new ArtifactDefinitionInput()
                .id("wp1").name("WP 1").description("WP Desc")
                .artifactType(com.processpuzzle.workflow.model.ArtifactType.ENTITY)
                .artifactTypeId("Invoice")
                .stateMachineId("sm-invoice");

        ArtifactDefinition domain = mapper.toArtifactDomain(input);
        domain.setVersion(2L);
        domain.setCreatedAt(Instant.now());

        assertThat(domain.getId()).isEqualTo("wp1");
        assertThat(domain.getArtifactType()).isEqualTo(ArtifactType.ENTITY);
        assertThat(domain.getArtifactTypeId()).isEqualTo("Invoice");
        assertThat(domain.getStateMachineId()).isEqualTo("sm-invoice");

        var model = mapper.toArtifactModel(domain);
        assertThat(model.getId()).isEqualTo("wp1");
        assertThat(model.getArtifactType()).isEqualTo(com.processpuzzle.workflow.model.ArtifactType.ENTITY);
        assertThat(model.getVersion()).isEqualTo(2L);

        assertThat(mapper.toArtifactDomain(new ArtifactDefinitionInput().id("wp1").version(7L)).getVersion())
                .isEqualTo(7L);
    }

    /** {@code artifactType} is the one nullable enum on an artifact, and both directions guard it. */
    @Test
    void artifactMappingToleratesAnAbsentArtifactType() {
        ArtifactDefinition domain =
                mapper.toArtifactDomain(new ArtifactDefinitionInput().id("wp1").artifactType(null));

        assertThat(domain.getArtifactType()).isNull();
        assertThat(mapper.toArtifactModel(domain).getArtifactType()).isNull();
    }

    @Test
    void toTaskDomain_and_toTaskModel() {
        TaskDefinitionInput input = new TaskDefinitionInput()
                .id("t1")
                .name("Task 1")
                .description("Task Desc")
                .performedByRoles(List.of("r1", "r2"))
                .inputs(List.of("wp1"))
                .outputs(List.of("wp2"))
                .preconditionRuleId("rule-pre")
                .postconditionRuleId("rule-post")
                .steps(List.of(new TaskStepDefinition()
                        .id("s1").name("Step 1").description("Step Desc")
                        .stepType(com.processpuzzle.workflow.model.TaskStepType.SERVICE_STEP)
                        .toolDefinitionId("tool-1").toolOperation("op-1")));

        TaskDefinition domain = mapper.toTaskDomain(input);
        domain.setVersion(7L);
        domain.setUpdatedAt(Instant.now());

        assertThat(domain.getId()).isEqualTo("t1");
        assertThat(domain.getPerformedByRoles()).containsExactly("r1", "r2");
        assertThat(domain.getInputs()).containsExactly("wp1");
        assertThat(domain.getOutputs()).containsExactly("wp2");
        assertThat(domain.getPreconditionRuleId()).isEqualTo("rule-pre");
        assertThat(domain.getPostconditionRuleId()).isEqualTo("rule-post");
        assertThat(domain.getSteps()).singleElement().satisfies(step -> {
            assertThat(step.getStepType()).isEqualTo(TaskStepType.SERVICE_STEP);
            assertThat(step.getToolDefinitionId()).isEqualTo("tool-1");
            assertThat(step.getToolOperation()).isEqualTo("op-1");
        });

        var model = mapper.toTaskModel(domain);
        assertThat(model.getId()).isEqualTo("t1");
        assertThat(model.getPerformedByRoles()).containsExactly("r1", "r2");
        assertThat(model.getInputs()).containsExactly("wp1");
        assertThat(model.getOutputs()).containsExactly("wp2");
        assertThat(model.getSteps()).singleElement()
                .extracting(TaskStepDefinition::getStepType)
                .isEqualTo(com.processpuzzle.workflow.model.TaskStepType.SERVICE_STEP);
        assertThat(model.getVersion()).isEqualTo(7L);

        assertThat(mapper.toTaskDomain(new TaskDefinitionInput().id("t1").version(7L)).getVersion()).isEqualTo(7L);
    }

    /** An omitted stepType means USER_STEP, in both directions. */
    @Test
    void stepMappingDefaultsAnAbsentStepTypeToUserStep() {
        TaskDefinition domain = mapper.toTaskDomain(new TaskDefinitionInput().id("t1").name("Task 1")
                .steps(List.of(new TaskStepDefinition().id("s1").name("Step 1").stepType(null))));

        assertThat(domain.getSteps().get(0).getStepType()).isEqualTo(TaskStepType.USER_STEP);

        TaskDefinition nulled = TaskDefinition.builder().id("t1")
                .steps(List.of(StepDefinition.builder().id("s1").stepType(null).build()))
                .build();
        assertThat(mapper.toTaskModel(nulled).getSteps().get(0).getStepType())
                .isEqualTo(com.processpuzzle.workflow.model.TaskStepType.USER_STEP);
    }

    @Test
    void toTaskDomain_toleratesAbsentCollections() {
        TaskDefinition domain = mapper.toTaskDomain(new TaskDefinitionInput().id("t1").name("Task 1")
                .performedByRoles(null).inputs(null).outputs(null).steps(null));

        assertThat(domain.getPerformedByRoles()).isEmpty();
        assertThat(domain.getInputs()).isEmpty();
        assertThat(domain.getOutputs()).isEmpty();
        assertThat(domain.getSteps()).isEmpty();
    }

    @Test
    void toToolDomain_and_toToolModel() {
        ToolDefinitionInput input = new ToolDefinitionInput()
                .id("tool-1")
                .name("Tool 1")
                .description("Tool Desc")
                .baseUrl(URI.create("https://api.example.com"))
                .auth(new com.processpuzzle.workflow.model.ToolAuthConfig()
                        .type(com.processpuzzle.workflow.model.AuthType.BEARER_TOKEN).secretRef("secret-key"))
                .operations(List.of(new ToolOperationDefinition()
                        .id("op-1")
                        .method(com.processpuzzle.workflow.model.HttpMethod.POST)
                        .path("/items")
                        .description("Create item")
                        .payloadTemplate("{\"name\": \"${item}\"}")
                        .expectedStatusCodes(List.of(200, 201))));

        ToolDefinition domain = mapper.toToolDomain(input);
        domain.setCreatedAt(Instant.now());
        domain.setVersion(1L);

        assertThat(domain.getId()).isEqualTo("tool-1");
        assertThat(domain.getBaseUrl()).isEqualTo("https://api.example.com");
        assertThat(domain.getAuth().getType().name()).isEqualTo("BEARER_TOKEN");
        assertThat(domain.getOperations()).hasSize(1);
        assertThat(domain.getOperations().get(0).getMethod()).isEqualTo(HttpMethod.POST);

        var model = mapper.toToolModel(domain);
        assertThat(model.getId()).isEqualTo("tool-1");
        assertThat(model.getBaseUrl()).isEqualTo(URI.create("https://api.example.com"));
        assertThat(model.getAuth().getSecretRef()).isEqualTo("secret-key");
        assertThat(model.getOperations()).hasSize(1);

        assertThat(mapper.toToolDomain(new ToolDefinitionInput().id("tool-1").version(7L)).getVersion()).isEqualTo(7L);
    }

    /** A tool with no auth block maps to NONE rather than to a null config. */
    @Test
    void toolMappingDefaultsAbsentAuthToNone() {
        ToolDefinition domain = mapper.toToolDomain(new ToolDefinitionInput().id("tool-1").auth(null).operations(null));

        assertThat(domain.getAuth().getType()).isEqualTo(com.processpuzzle.workflow.definition.domain.AuthType.NONE);
        assertThat(domain.getOperations()).isEmpty();

        domain.setAuth(null);
        assertThat(mapper.toToolModel(domain).getAuth().getType())
                .isEqualTo(com.processpuzzle.workflow.model.AuthType.NONE);
    }

    /** Uses have no audit fields of their own; they live and die with the workflow row. */
    @Test
    void useMappingIsSymmetric() {
        assertThat(mapper.toRoleUseModel(RoleUse.builder().roleDefinitionId("r1").build()).getRoleDefinitionId())
                .isEqualTo("r1");
        assertThat(mapper.toArtifactUseDomain(new com.processpuzzle.workflow.model.ArtifactUse()
                .artifactDefinitionId("wp1")))
                .isEqualTo(ArtifactUse.builder().artifactDefinitionId("wp1").build());
        assertThat(mapper.toToolUseDomain(new com.processpuzzle.workflow.model.ToolUse().toolDefinitionId("tool-1"))
                .getToolDefinitionId()).isEqualTo("tool-1");
    }
}
