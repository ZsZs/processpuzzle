package com.processpuzzle.workflow.definition.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDefinitionDomainTest {

    @Test
    void processDefinitionKey_equalsAndHashCodeAndGettersSetters() {
        ProcessDefinitionKey key1 = new ProcessDefinitionKey("org1", "proc1");
        ProcessDefinitionKey key2 = new ProcessDefinitionKey("org1", "proc1");
        ProcessDefinitionKey key3 = new ProcessDefinitionKey("org2", "proc2");
        ProcessDefinitionKey emptyKey = new ProcessDefinitionKey();

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
    void toolDefinitionKey_equalsAndHashCodeAndGettersSetters() {
        ToolDefinitionKey key1 = new ToolDefinitionKey("org1", "tool1");
        ToolDefinitionKey key2 = new ToolDefinitionKey("org1", "tool1");
        ToolDefinitionKey key3 = new ToolDefinitionKey("org2", "tool2");
        ToolDefinitionKey emptyKey = new ToolDefinitionKey();

        emptyKey.setOrgKey("org1");
        emptyKey.setId("tool1");

        assertThat(key1)
                .isEqualTo(key2)
                .isEqualTo(emptyKey)
                .isNotEqualTo(key3)
                .isNotEqualTo(null)
                .isNotEqualTo("other")
                .hasSameHashCodeAs(key2);
        assertThat(key1.getOrgKey()).isEqualTo("org1");
        assertThat(key1.getId()).isEqualTo("tool1");
    }

    @Test
    void processDefinition_builderAndFindMethods() {
        RoleDefinition role = RoleDefinition.builder().id("analyst").name("Business Analyst").build();
        TaskDefinition task = TaskDefinition.builder().id("gather").name("Gather Requirements").performedBy("analyst").build();
        WorkProductDefinition wp = WorkProductDefinition.builder().id("spec").name("Specification").type(WorkProductType.ARTIFACT).build();

        ProcessDefinition def = ProcessDefinition.builder()
                .orgKey("org-1")
                .id("proc-1")
                .name("Process One")
                .description("Description")
                .version(1L)
                .extendsProcessId("proc-base")
                .roles(new ArrayList<>(List.of(role)))
                .tasks(new ArrayList<>(List.of(task)))
                .workProducts(new ArrayList<>(List.of(wp)))
                .tools(new ArrayList<>(List.of("jira")))
                .build();

        assertThat(def.getOrgKey()).isEqualTo("org-1");
        assertThat(def.getId()).isEqualTo("proc-1");
        assertThat(def.getName()).isEqualTo("Process One");
        assertThat(def.getDescription()).isEqualTo("Description");
        assertThat(def.getVersion()).isEqualTo(1L);
        assertThat(def.getExtendsProcessId()).isEqualTo("proc-base");
        assertThat(def.getRoles()).containsExactly(role);
        assertThat(def.getTasks()).containsExactly(task);
        assertThat(def.getWorkProducts()).containsExactly(wp);
        assertThat(def.getTools()).containsExactly("jira");

        assertThat(def.findRole("analyst")).contains(role);
        assertThat(def.findRole("unknown")).isEmpty();
        assertThat(def.findTask("gather")).contains(task);
        assertThat(def.findTask("unknown")).isEmpty();
        assertThat(def.findWorkProduct("spec")).contains(wp);
        assertThat(def.findWorkProduct("unknown")).isEmpty();
    }

    @Test
    void processDefinition_replaceContentAndEmptyDef() {
        ProcessDefinition def = ProcessDefinition.builder()
                .orgKey("org-1")
                .id("proc-1")
                .build();

        RoleDefinition newRole = RoleDefinition.builder().id("dev").name("Developer").build();
        TaskDefinition newTask = TaskDefinition.builder().id("code").name("Write Code").performedBy("dev").build();
        WorkProductDefinition newWp = WorkProductDefinition.builder().id("pr").name("Pull Request").type(WorkProductType.DELIVERABLE).build();

        def.replaceContent("New Name", "New Desc", "new-parent",
                List.of("git"), List.of(newRole), List.of(newWp), List.of(newTask));

        assertThat(def.getName()).isEqualTo("New Name");
        assertThat(def.getDescription()).isEqualTo("New Desc");
        assertThat(def.getExtendsProcessId()).isEqualTo("new-parent");
        assertThat(def.getRoles()).containsExactly(newRole);
        assertThat(def.getTasks()).containsExactly(newTask);
        assertThat(def.getWorkProducts()).containsExactly(newWp);
        assertThat(def.getTools()).containsExactly("git");

        // Test with null collections
        ProcessDefinition emptyDef = new ProcessDefinition();
        assertThat(emptyDef.getRoles()).isNotNull().isEmpty();
        assertThat(emptyDef.getTasks()).isNotNull().isEmpty();
        assertThat(emptyDef.getWorkProducts()).isNotNull().isEmpty();
        assertThat(emptyDef.getTools()).isNotNull().isEmpty();

        emptyDef.replaceContent(null, null, null, List.of(), List.of(), List.of(), List.of());
        assertThat(emptyDef.getRoles()).isEmpty();
        assertThat(emptyDef.getTasks()).isEmpty();
        assertThat(emptyDef.getWorkProducts()).isEmpty();
        assertThat(emptyDef.getTools()).isEmpty();
    }

    @Test
    void taskDefinition_builderAndGettersSetters() {
        StepDefinition step = StepDefinition.builder()
                .id("step-1")
                .name("Step One")
                .description("Step Description")
                .toolId("tool-1")
                .toolOperation("op-1")
                .inputMapping(Map.of("in", "ctxIn"))
                .outputMapping(Map.of("out", "ctxOut"))
                .build();

        TaskIOReference inputRef = TaskIOReference.builder()
                .refId("wp-in")
                .type(ReferenceType.DOCUMENT)
                .label("Specification Document")
                .build();

        UUID techId = UUID.randomUUID();
        TaskDefinition task = TaskDefinition.builder()
                .technicalId(techId)
                .id("task-1")
                .name("Task One")
                .description("Task description")
                .performedBy("role-1")
                .inputs(List.of(inputRef))
                .outputs(List.of())
                .steps(List.of(step))
                .dependsOn(List.of("task-0"))
                .preconditionRuleId("rule-pre")
                .postconditionRuleId("rule-post")
                .parallel(true)
                .override(true)
                .build();

        assertThat(task.getTechnicalId()).isEqualTo(techId);
        assertThat(task.getId()).isEqualTo("task-1");
        assertThat(task.getName()).isEqualTo("Task One");
        assertThat(task.getDescription()).isEqualTo("Task description");
        assertThat(task.getPerformedBy()).isEqualTo("role-1");
        assertThat(task.getInputs()).containsExactly(inputRef);
        assertThat(task.getOutputs()).isEmpty();
        assertThat(task.getSteps()).containsExactly(step);
        assertThat(task.getDependsOn()).containsExactly("task-0");
        assertThat(task.getPreconditionRuleId()).isEqualTo("rule-pre");
        assertThat(task.getPostconditionRuleId()).isEqualTo("rule-post");
        assertThat(task.isParallel()).isTrue();
        assertThat(task.isOverride()).isTrue();

        assertThat(step.getId()).isEqualTo("step-1");
        assertThat(step.getName()).isEqualTo("Step One");
        assertThat(step.getDescription()).isEqualTo("Step Description");
        assertThat(step.getToolId()).isEqualTo("tool-1");
        assertThat(step.getToolOperation()).isEqualTo("op-1");
        assertThat(step.getInputMapping()).containsEntry("in", "ctxIn");
        assertThat(step.getOutputMapping()).containsEntry("out", "ctxOut");

        assertThat(inputRef.getRefId()).isEqualTo("wp-in");
        assertThat(inputRef.getType()).isEqualTo(ReferenceType.DOCUMENT);
        assertThat(inputRef.getLabel()).isEqualTo("Specification Document");
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
    void workProductDefinition_builderAndGetters() {
        UUID techId = UUID.randomUUID();
        WorkProductDefinition wp = WorkProductDefinition.builder()
                .technicalId(techId)
                .id("wp-1")
                .name("Work Product 1")
                .description("WP Description")
                .type(WorkProductType.ENTITY)
                .entityTypeId("Invoice")
                .stateMachineId("sm-invoice")
                .build();

        assertThat(wp.getTechnicalId()).isEqualTo(techId);
        assertThat(wp.getId()).isEqualTo("wp-1");
        assertThat(wp.getName()).isEqualTo("Work Product 1");
        assertThat(wp.getDescription()).isEqualTo("WP Description");
        assertThat(wp.getType()).isEqualTo(WorkProductType.ENTITY);
        assertThat(wp.getEntityTypeId()).isEqualTo("Invoice");
        assertThat(wp.getStateMachineId()).isEqualTo("sm-invoice");
    }

    @Test
    void enums_allValuesCanBeInstantiated() {
        assertThat(AuthType.values()).containsExactlyInAnyOrder(AuthType.NONE, AuthType.BEARER_TOKEN, AuthType.API_KEY, AuthType.BASIC);
        assertThat(HttpMethod.values()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
        assertThat(ReferenceType.values()).containsExactlyInAnyOrder(ReferenceType.BASE_ENTITY, ReferenceType.DOCUMENT, ReferenceType.WIDGET);
        assertThat(WorkProductType.values()).containsExactlyInAnyOrder(WorkProductType.ARTIFACT, WorkProductType.DELIVERABLE, WorkProductType.OUTCOME, WorkProductType.ENTITY);
    }
}
