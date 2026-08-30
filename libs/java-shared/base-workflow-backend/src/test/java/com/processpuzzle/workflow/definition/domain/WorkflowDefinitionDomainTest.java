package com.processpuzzle.workflow.definition.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDefinitionDomainTest {

    @Test
    void workflowKey_equalsAndHashCodeAndGettersSetters() {
        WorkflowKey key1 = new WorkflowKey("org1", "proc1");
        WorkflowKey key2 = new WorkflowKey("org1", "proc1");
        WorkflowKey key3 = new WorkflowKey("org2", "proc2");
        WorkflowKey emptyKey = new WorkflowKey();

        emptyKey.setOrgKey("org1");
        emptyKey.setId("proc1");

        assertThat(key1)
                .isEqualTo(key2)
                .isEqualTo(emptyKey)
                .isNotEqualTo(key3)
                .isNotEqualTo(null)
                .isNotEqualTo("other")
                .hasSameHashCodeAs(key2);
        assertThat(key1.getOrgKey()).isEqualTo("org1");
        assertThat(key1.getId()).isEqualTo("proc1");
    }

    @Test
    void theOtherFourKeysBehaveTheSameWay() {
        assertKeyContract(new ToolDefinitionKey("org1", "tool1"), new ToolDefinitionKey("org1", "tool1"),
                new ToolDefinitionKey("org2", "tool2"), new ToolDefinitionKey(), "tool1");
        assertKeyContract(new RoleDefinitionKey("org1", "role1"), new RoleDefinitionKey("org1", "role1"),
                new RoleDefinitionKey("org2", "role2"), new RoleDefinitionKey(), "role1");
        assertKeyContract(new ArtifactDefinitionKey("org1", "art1"), new ArtifactDefinitionKey("org1", "art1"),
                new ArtifactDefinitionKey("org2", "art2"), new ArtifactDefinitionKey(), "art1");
        assertKeyContract(new TaskDefinitionKey("org1", "task1"), new TaskDefinitionKey("org1", "task1"),
                new TaskDefinitionKey("org2", "task2"), new TaskDefinitionKey(), "task1");
    }

    @Test
    void workflowHoldsUsesRatherThanTheDefinitionsThemselves() {
        TaskUse taskUse = TaskUse.builder()
                .taskDefinitionId("gather").performedBy("analyst").build();

        Workflow def = Workflow.builder()
                .orgKey("org-1")
                .id("wf-1")
                .name("Workflow One")
                .description("Description")
                .version(1L)
                .extendsProcessId("wf-base")
                .roles(new ArrayList<>(List.of(RoleUse.builder().roleDefinitionId("analyst").build())))
                .tasks(new ArrayList<>(List.of(taskUse)))
                .artifacts(new ArrayList<>(List.of(ArtifactUse.builder().artifactDefinitionId("spec").build())))
                .tools(new ArrayList<>(List.of(ToolUse.builder().toolDefinitionId("jira").build())))
                .build();

        assertThat(def.getOrgKey()).isEqualTo("org-1");
        assertThat(def.getId()).isEqualTo("wf-1");
        assertThat(def.getName()).isEqualTo("Workflow One");
        assertThat(def.getDescription()).isEqualTo("Description");
        assertThat(def.getVersion()).isEqualTo(1L);
        assertThat(def.getExtendsProcessId()).isEqualTo("wf-base");
        assertThat(def.getTasks()).containsExactly(taskUse);

        // The four id accessors are what the validator, resolver and export read the uses through.
        assertThat(def.roleDefinitionIds()).containsExactly("analyst");
        assertThat(def.artifactDefinitionIds()).containsExactly("spec");
        assertThat(def.toolDefinitionIds()).containsExactly("jira");
        assertThat(def.taskDefinitionIds()).containsExactly("gather");

        assertThat(def.findTaskUse("gather")).contains(taskUse);
        assertThat(def.findTaskUse("unknown")).isEmpty();
    }

    @Test
    void taskUseCarriesTheWiringThatIsTrueInThisWorkflowOnly() {
        TaskUse use = TaskUse.builder()
                .taskDefinitionId("review")
                .performedBy("reviewer")
                .dependsOn(List.of("code"))
                .joinType(JoinType.ANY)
                .parallel(true)
                .override(true)
                .build();

        assertThat(use.getTaskDefinitionId()).isEqualTo("review");
        assertThat(use.getPerformedBy()).isEqualTo("reviewer");
        assertThat(use.getDependsOn()).containsExactly("code");
        assertThat(use.getJoinType()).isEqualTo(JoinType.ANY);
        assertThat(use.isParallel()).isTrue();
        assertThat(use.isOverride()).isTrue();

        TaskUse plain = new TaskUse();
        assertThat(plain.getDependsOn()).isNotNull().isEmpty();
        assertThat(plain.getJoinType()).isEqualTo(JoinType.ALL);
        assertThat(plain.isParallel()).isFalse();
        assertThat(plain.isOverride()).isFalse();
    }

    /**
     * The other three uses are a definition id and nothing else — for now. The assertion is that they
     * exist as objects rather than as bare strings, which is what lets a per-workflow field be added
     * later without changing the shape of the workflow's reference lists.
     */
    @Test
    void theOtherThreeUsesAreObjectsAroundADefinitionId() {
        assertThat(RoleUse.builder().roleDefinitionId("analyst").build())
                .isEqualTo(new RoleUse("analyst"))
                .hasToString("RoleUse(roleDefinitionId=analyst)");
        assertThat(ArtifactUse.builder().artifactDefinitionId("spec").build())
                .isEqualTo(new ArtifactUse("spec"));
        assertThat(ToolUse.builder().toolDefinitionId("jira").build())
                .isEqualTo(new ToolUse("jira"));

        RoleUse plain = new RoleUse();
        plain.setRoleDefinitionId("dev");
        assertThat(plain.getRoleDefinitionId()).isEqualTo("dev");
    }

    @Test
    void workflow_replaceContentAndEmptyDef() {
        Workflow def = Workflow.builder()
                .orgKey("org-1")
                .id("wf-1")
                .build();

        TaskUse newUse = TaskUse.builder()
                .taskDefinitionId("code").performedBy("dev").build();
        WorkflowStartCondition condition = WorkflowStartCondition.builder()
                .startType(WorkflowStartConditionType.ROLE_DEFINITION)
                .authorizedRoles(List.of("dev"))
                .build();

        def.replaceContent("New Name", "New Desc", "new-parent", condition,
                List.of(RoleUse.builder().roleDefinitionId("dev").build()),
                List.of(ArtifactUse.builder().artifactDefinitionId("pr").build()),
                List.of(ToolUse.builder().toolDefinitionId("git").build()),
                List.of(newUse));

        assertThat(def.getName()).isEqualTo("New Name");
        assertThat(def.getDescription()).isEqualTo("New Desc");
        assertThat(def.getExtendsProcessId()).isEqualTo("new-parent");
        assertThat(def.getStartCondition()).isSameAs(condition);
        assertThat(def.roleDefinitionIds()).containsExactly("dev");
        assertThat(def.getTasks()).containsExactly(newUse);
        assertThat(def.artifactDefinitionIds()).containsExactly("pr");
        assertThat(def.toolDefinitionIds()).containsExactly("git");

        // Test with null collections
        Workflow emptyDef = new Workflow();
        assertThat(emptyDef.getRoles()).isNotNull().isEmpty();
        assertThat(emptyDef.getTasks()).isNotNull().isEmpty();
        assertThat(emptyDef.getArtifacts()).isNotNull().isEmpty();
        assertThat(emptyDef.getTools()).isNotNull().isEmpty();
        assertThat(emptyDef.getStartCondition()).isNull();

        emptyDef.replaceContent(null, null, null, null, List.of(), List.of(), List.of(), List.of());
        assertThat(emptyDef.getRoles()).isEmpty();
        assertThat(emptyDef.getTasks()).isEmpty();
        assertThat(emptyDef.getArtifacts()).isEmpty();
        assertThat(emptyDef.getTools()).isEmpty();
        assertThat(emptyDef.getStartCondition()).isNull();
    }

    /**
     * A start condition is one flat value with a discriminant, so the assertion that matters is that
     * every mechanism's fields survive on the same object — nothing is lost by the absence of a
     * subtype per {@code startType}.
     */
    @Test
    void startCondition_carriesEveryMechanismsFields() {
        WorkflowStartCondition condition = WorkflowStartCondition.builder()
                .startType(WorkflowStartConditionType.INPUT_ARTIFACT)
                .requiredArtifacts(List.of(RequiredStartArtifact.builder()
                        .artifactDefinitionId("order-entity").state("DRAFT").build()))
                .eventType("order.submitted")
                .payloadMapping(Map.of("orderId", "$.id"))
                .authorizedRoles(List.of("clerk"))
                .milestoneRef("MILESTONE_REACHED")
                .preconditionExpression("milestone.status == 'PASSED'")
                .build();

        assertThat(condition.getStartType()).isEqualTo(WorkflowStartConditionType.INPUT_ARTIFACT);
        assertThat(condition.getRequiredArtifacts()).singleElement().satisfies(required -> {
            assertThat(required.getArtifactDefinitionId()).isEqualTo("order-entity");
            assertThat(required.getState()).isEqualTo("DRAFT");
        });
        assertThat(condition.getEventType()).isEqualTo("order.submitted");
        assertThat(condition.getPayloadMapping()).containsEntry("orderId", "$.id");
        assertThat(condition.getAuthorizedRoles()).containsExactly("clerk");
        assertThat(condition.getMilestoneRef()).isEqualTo("MILESTONE_REACHED");
        assertThat(condition.getPreconditionExpression()).isEqualTo("milestone.status == 'PASSED'");

        WorkflowStartCondition plain = new WorkflowStartCondition();
        assertThat(plain.getRequiredArtifacts()).isNotNull().isEmpty();
        assertThat(plain.getStartType()).isNull();

        RequiredStartArtifact anyState = new RequiredStartArtifact();
        anyState.setArtifactDefinitionId("order-entity");
        assertThat(anyState.getState()).isNull();
    }

    @Test
    void taskDefinition_builderAndGettersSetters() {
        StepDefinition step = StepDefinition.builder()
                .id("step-1")
                .name("Step One")
                .description("Step Description")
                .stepType(TaskStepType.SERVICE_STEP)
                .toolDefinitionId("tool-1")
                .toolOperation("op-1")
                .inputMapping(Map.of("in", "ctxIn"))
                .outputMapping(Map.of("out", "ctxOut"))
                .build();

        TaskDefinition task = TaskDefinition.builder()
                .orgKey("org-1")
                .id("task-1")
                .name("Task One")
                .description("Task description")
                .performedByRoles(List.of("role-1", "role-2"))
                .inputs(List.of("wp-in"))
                .outputs(List.of())
                .steps(List.of(step))
                .preconditionRuleId("rule-pre")
                .postconditionRuleId("rule-post")
                .version(3L)
                .build();

        assertThat(task.getOrgKey()).isEqualTo("org-1");
        assertThat(task.getId()).isEqualTo("task-1");
        assertThat(task.getName()).isEqualTo("Task One");
        assertThat(task.getDescription()).isEqualTo("Task description");
        assertThat(task.getPerformedByRoles()).containsExactly("role-1", "role-2");
        assertThat(task.getInputs()).containsExactly("wp-in");
        assertThat(task.getOutputs()).isEmpty();
        assertThat(task.getSteps()).containsExactly(step);
        assertThat(task.getPreconditionRuleId()).isEqualTo("rule-pre");
        assertThat(task.getPostconditionRuleId()).isEqualTo("rule-post");
        assertThat(task.getVersion()).isEqualTo(3L);

        TaskDefinition emptyTask = new TaskDefinition();
        assertThat(emptyTask.getPerformedByRoles()).isNotNull().isEmpty();
        assertThat(emptyTask.getInputs()).isNotNull().isEmpty();
        assertThat(emptyTask.getOutputs()).isNotNull().isEmpty();
        assertThat(emptyTask.getSteps()).isNotNull().isEmpty();

        assertThat(step.getId()).isEqualTo("step-1");
        assertThat(step.getName()).isEqualTo("Step One");
        assertThat(step.getDescription()).isEqualTo("Step Description");
        assertThat(step.getStepType()).isEqualTo(TaskStepType.SERVICE_STEP);
        assertThat(step.getToolDefinitionId()).isEqualTo("tool-1");
        assertThat(step.getToolOperation()).isEqualTo("op-1");
        assertThat(step.getInputMapping()).containsEntry("in", "ctxIn");
        assertThat(step.getOutputMapping()).containsEntry("out", "ctxOut");

        // A step is a user step unless it says otherwise; only a SERVICE_STEP reads the tool fields.
        assertThat(new StepDefinition().getStepType()).isEqualTo(TaskStepType.USER_STEP);
        assertThat(StepDefinition.builder().id("step-2").build().getStepType())
                .isEqualTo(TaskStepType.USER_STEP);
    }

    @Test
    void roleDefinition_builderAndGetters() {
        RoleDefinition role = RoleDefinition.builder()
                .orgKey("org-1")
                .id("analyst")
                .name("Business Analyst")
                .description("Gathers requirements")
                .responsibleFor(List.of("spec"))
                .entityRoleId("analyst-role")
                .version(2L)
                .build();

        assertThat(role.getOrgKey()).isEqualTo("org-1");
        assertThat(role.getId()).isEqualTo("analyst");
        assertThat(role.getName()).isEqualTo("Business Analyst");
        assertThat(role.getDescription()).isEqualTo("Gathers requirements");
        assertThat(role.getResponsibleFor()).containsExactly("spec");
        assertThat(role.getEntityRoleId()).isEqualTo("analyst-role");
        assertThat(role.getVersion()).isEqualTo(2L);

        // Ownership is optional, and absent means "owns nothing" rather than null.
        assertThat(new RoleDefinition().getResponsibleFor()).isNotNull().isEmpty();
    }

    @Test
    void toolDefinition_builderAndMethods() {
        ToolAuthConfig auth = ToolAuthConfig.builder()
                .type(AuthType.BEARER_TOKEN)
                .secretRef("MY_SECRET")
                .build();

        ToolOperation op = ToolOperation.builder()
                .id("create-issue")
                .method(HttpMethod.POST)
                .path("/issues")
                .payloadTemplate("{\"title\":\"${title}\"}")
                .expectedStatusCodes(List.of(200, 201))
                .build();

        ToolDefinition tool = ToolDefinition.builder()
                .orgKey("org-1")
                .id("jira")
                .name("Jira")
                .description("Issue tracker")
                .baseUrl("https://jira.example.com")
                .auth(auth)
                .operations(new ArrayList<>(List.of(op)))
                .build();

        assertThat(tool.getOrgKey()).isEqualTo("org-1");
        assertThat(tool.getId()).isEqualTo("jira");
        assertThat(tool.getName()).isEqualTo("Jira");
        assertThat(tool.getDescription()).isEqualTo("Issue tracker");
        assertThat(tool.getBaseUrl()).isEqualTo("https://jira.example.com");
        assertThat(tool.getAuth().getType()).isEqualTo(AuthType.BEARER_TOKEN);
        assertThat(tool.getAuth().getSecretRef()).isEqualTo("MY_SECRET");
        assertThat(tool.getOperations()).containsExactly(op);

        assertThat(tool.findOperation("create-issue")).contains(op);
        assertThat(tool.findOperation("unknown")).isEmpty();

        ToolOperation newOp = ToolOperation.builder()
                .id("get-issue")
                .method(HttpMethod.GET)
                .path("/issues/{id}")
                .build();

        tool.setName("New Jira");
        tool.setBaseUrl("https://newjira.example.com");
        tool.setOperations(List.of(newOp));
        assertThat(tool.getName()).isEqualTo("New Jira");
        assertThat(tool.getBaseUrl()).isEqualTo("https://newjira.example.com");
        assertThat(tool.getOperations()).containsExactly(newOp);

        ToolDefinition emptyTool = new ToolDefinition();
        assertThat(emptyTool.getOperations()).isNotNull().isEmpty();
    }

    @Test
    void artifactDefinition_builderAndGetters() {
        ArtifactDefinition wp = ArtifactDefinition.builder()
                .orgKey("org-1")
                .id("wp-1")
                .name("Artifact 1")
                .description("WP Description")
                .artifactType(ArtifactType.ENTITY)
                .artifactTypeId("Invoice")
                .stateMachineId("sm-invoice")
                .build();

        assertThat(wp.getOrgKey()).isEqualTo("org-1");
        assertThat(wp.getId()).isEqualTo("wp-1");
        assertThat(wp.getName()).isEqualTo("Artifact 1");
        assertThat(wp.getDescription()).isEqualTo("WP Description");
        assertThat(wp.getArtifactType()).isEqualTo(ArtifactType.ENTITY);
        assertThat(wp.getArtifactTypeId()).isEqualTo("Invoice");
        assertThat(wp.getStateMachineId()).isEqualTo("sm-invoice");
    }

    @Test
    void enums_allValuesCanBeInstantiated() {
        assertThat(AuthType.values()).containsExactlyInAnyOrder(AuthType.NONE, AuthType.BEARER_TOKEN, AuthType.API_KEY, AuthType.BASIC);
        assertThat(HttpMethod.values()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
        assertThat(ArtifactType.values()).containsExactlyInAnyOrder(ArtifactType.DOCUMENT, ArtifactType.ENTITY,
                ArtifactType.WIDGET);
        assertThat(TaskStepType.values()).containsExactlyInAnyOrder(TaskStepType.USER_STEP, TaskStepType.SERVICE_STEP);
        assertThat(JoinType.values()).containsExactlyInAnyOrder(JoinType.ALL, JoinType.ANY);
        assertThat(WorkflowStartConditionType.values()).containsExactlyInAnyOrder(
                WorkflowStartConditionType.INPUT_ARTIFACT, WorkflowStartConditionType.TRIGGERING_EVENT,
                WorkflowStartConditionType.ROLE_DEFINITION, WorkflowStartConditionType.TIME_BASED_PRECONDITION);
    }

    /**
     * The five {@code @IdClass} keys are generated the same way, so one assertion covers the
     * contract JPA relies on: value equality on {@code (orgKey, id)}, a no-arg constructor the
     * provider can populate through setters, and a hash code consistent with equals.
     */
    private void assertKeyContract(Object key, Object same, Object different, Object empty, String id) {
        try {
            empty.getClass().getMethod("setOrgKey", String.class).invoke(empty, "org1");
            empty.getClass().getMethod("setId", String.class).invoke(empty, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Key class is missing the setters JPA needs", e);
        }
        assertThat(key)
                .isEqualTo(same)
                .isEqualTo(empty)
                .isNotEqualTo(different)
                .isNotEqualTo(null)
                .isNotEqualTo("other")
                .hasSameHashCodeAs(same);
    }
}
