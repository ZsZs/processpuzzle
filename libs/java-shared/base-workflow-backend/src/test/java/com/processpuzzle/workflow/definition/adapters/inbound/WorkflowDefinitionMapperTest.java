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
import com.processpuzzle.workflow.definition.domain.EventUse;
import com.processpuzzle.workflow.definition.domain.TimerDefinition;
import com.processpuzzle.workflow.definition.domain.TimerType;
import com.processpuzzle.workflow.definition.domain.EventDirection;
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
                .startEvents(List.of(new com.processpuzzle.workflow.model.StartEvent()
                        .id("wp1-drafted")
                        .name("Drafted")
                        .startType(com.processpuzzle.workflow.model.WorkflowStartConditionType.INPUT_ARTIFACT)
                        .requiredArtifacts(List.of(new com.processpuzzle.workflow.model.RequiredStartArtifact()
                                .artifactDefinitionId("wp1").state("DRAFT")))))
                .roles(List.of(new com.processpuzzle.workflow.model.RoleUse().roleDefinitionId("r1")))
                .artifacts(List.of(new com.processpuzzle.workflow.model.ArtifactUse().artifactDefinitionId("wp1").objectName("spec")))
                .tools(List.of(new com.processpuzzle.workflow.model.ToolUse().toolDefinitionId("tool-1")))
                .tasks(List.of(new com.processpuzzle.workflow.model.TaskUse()
                        .taskDefinitionId("t1")
                        .performedBy("r1")
                        .dependsOn(List.of("t0"))
                        .joinType(com.processpuzzle.workflow.model.JoinType.ANY)
                        .parallel(true)
                        .override(true)
                        .artifactStates(List.of(new com.processpuzzle.workflow.model.TaskArtifactState()
                                .artifactDefinitionId("wp1").inputState("DRAFT").outputState("FINAL")))));

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
        assertThat(domain.getStartEvents()).singleElement().satisfies(startEvent -> {
            assertThat(startEvent.getId()).isEqualTo("wp1-drafted");
            assertThat(startEvent.getName()).isEqualTo("Drafted");
            assertThat(startEvent.getStartType()).isEqualTo(WorkflowStartConditionType.INPUT_ARTIFACT);
        });
        assertThat(domain.getStartEvents().get(0).getRequiredArtifacts()).singleElement()
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
        assertThat(use.getArtifactStates()).singleElement().satisfies(state -> {
            assertThat(state.getArtifactDefinitionId()).isEqualTo("wp1");
            assertThat(state.getInputState()).isEqualTo("DRAFT");
            assertThat(state.getOutputState()).isEqualTo("FINAL");
        });

        domain.setCreatedAt(Instant.now());
        domain.setUpdatedAt(Instant.now());
        domain.setVersion(1L);

        var model = mapper.toModel(domain);
        assertThat(model.getId()).isEqualTo("wf-1");
        assertThat(model.getName()).isEqualTo("Workflow 1");
        assertThat(model.getExtends()).isEqualTo("parent-wf");
        assertThat(model.getRoles()).singleElement()
                .extracting(com.processpuzzle.workflow.model.RoleUse::getRoleDefinitionId).isEqualTo("r1");
        assertThat(model.getArtifacts()).singleElement().satisfies(artifactUse -> {
            assertThat(artifactUse.getArtifactDefinitionId()).isEqualTo("wp1");
            assertThat(artifactUse.getObjectName()).isEqualTo("spec");
        });
        assertThat(model.getTools()).singleElement()
                .extracting(com.processpuzzle.workflow.model.ToolUse::getToolDefinitionId).isEqualTo("tool-1");
        assertThat(model.getStartEvents()).singleElement().satisfies(startEvent -> {
            assertThat(startEvent.getId()).isEqualTo("wp1-drafted");
            assertThat(startEvent.getName()).isEqualTo("Drafted");
            assertThat(startEvent.getRequiredArtifacts()).hasSize(1);
        });
        assertThat(model.getTasks()).hasSize(1);
        assertThat(model.getTasks().get(0).getTaskDefinitionId()).isEqualTo("t1");
        assertThat(model.getTasks().get(0).getDependsOn()).containsExactly("t0");
        assertThat(model.getTasks().get(0).getArtifactStates()).singleElement()
                .extracting(com.processpuzzle.workflow.model.TaskArtifactState::getOutputState).isEqualTo("FINAL");
        assertThat(model.getTasks().get(0).getJoinType())
                .isEqualTo(com.processpuzzle.workflow.model.JoinType.ANY);
        assertThat(model.getVersion()).isEqualTo(1L);
        assertThat(model.getCreatedAt()).isNotNull();
        assertThat(model.getUpdatedAt()).isNotNull();
    }

    /**
     * Generated inputs pre-fill list defaults, so an explicitly nulled collection is the only way to
     * reach the mapper's null branches. Start events are genuinely optional — a workflow without
     * any can be started by anyone through /instances.
     */
    @Test
    void toDomain_workflowToleratesAbsentCollectionsAndStartEvents() {
        WorkflowInput emptyInput = new WorkflowInput().id("empty").name("Empty")
                .startEvents(null).roles(null).artifacts(null).tools(null).tasks(null);

        Workflow emptyDomain = mapper.toDomain("org-1", emptyInput);

        assertThat(emptyDomain.getRoles()).isEmpty();
        assertThat(emptyDomain.getArtifacts()).isEmpty();
        assertThat(emptyDomain.getTools()).isEmpty();
        assertThat(emptyDomain.getTasks()).isEmpty();
        assertThat(emptyDomain.getStartEvents()).isEmpty();
        assertThat(emptyDomain.getEvents()).isEmpty();
        assertThat(mapper.toModel(emptyDomain).getStartEvents()).isEmpty();
        assertThat(mapper.toModel(emptyDomain).getEvents()).isEmpty();
    }

    @Test
    void eventUseMappingIsSymmetricAndDefaultsJoinTypeToAll() {
        WorkflowInput input = new WorkflowInput().id("wf").name("WF")
                .events(List.of(
                        new com.processpuzzle.workflow.model.EventUse().id("ask").name("Ask")
                                .eventDefinitionId("InvoiceRequested")
                                .direction(com.processpuzzle.workflow.model.EventDirection.THROW)
                                .dependsOn(List.of("approve")).correlationKey("orderId")
                                .payloadMapping(Map.of("number", "$.orderNumber")),
                        new com.processpuzzle.workflow.model.EventUse().id("issued").eventDefinitionId("InvoiceIssued")
                                .direction(com.processpuzzle.workflow.model.EventDirection.CATCH)
                                .dependsOn(null).joinType(null)));

        Workflow domain = mapper.toDomain("org-1", input);

        assertThat(domain.getEvents()).hasSize(2);
        EventUse ask = domain.getEvents().get(0);
        assertThat(ask.getDirection()).isEqualTo(EventDirection.THROW);
        assertThat(ask.getDependsOn()).containsExactly("approve");
        assertThat(ask.getJoinType()).isEqualTo(JoinType.ALL);
        assertThat(ask.getCorrelationKey()).isEqualTo("orderId");
        assertThat(ask.getPayloadMapping()).containsEntry("number", "$.orderNumber");
        assertThat(domain.getEvents().get(1).getDependsOn()).isEmpty();

        var model = mapper.toModel(domain).getEvents();
        assertThat(model.get(0).getId()).isEqualTo("ask");
        assertThat(model.get(0).getName()).isEqualTo("Ask");
        assertThat(model.get(0).getDirection()).isEqualTo(com.processpuzzle.workflow.model.EventDirection.THROW);
        assertThat(model.get(0).getPayloadMapping()).containsEntry("number", "$.orderNumber");
        assertThat(model.get(1).getDirection()).isEqualTo(com.processpuzzle.workflow.model.EventDirection.CATCH);
        assertThat(model.get(1).getJoinType()).isEqualTo(com.processpuzzle.workflow.model.JoinType.ALL);
        assertThat(mapper.toModel(Workflow.builder().id("x").events(List.of(EventUse.builder().id("e").build())).build())
                .getEvents().get(0).getDirection()).isNull();
    }

    @Test
    void timersAndBoundariesMapBothWays() {
        WorkflowInput input = new WorkflowInput().id("wf").name("WF")
                .startEvents(List.of(new com.processpuzzle.workflow.model.StartEvent().id("nightly")
                        .startType(com.processpuzzle.workflow.model.WorkflowStartConditionType.TIME_BASED_PRECONDITION)
                        .timer(new com.processpuzzle.workflow.model.TimerDefinition()
                                .type(com.processpuzzle.workflow.model.TimerType.CYCLE).expression("R/P1D"))))
                .events(List.of(
                        new com.processpuzzle.workflow.model.EventUse().id("overdue")
                                .direction(com.processpuzzle.workflow.model.EventDirection.CATCH)
                                .attachedTo("issue").interrupting(null)
                                .timer(new com.processpuzzle.workflow.model.TimerDefinition()
                                        .type(com.processpuzzle.workflow.model.TimerType.DURATION).expression("PT1H")),
                        new com.processpuzzle.workflow.model.EventUse().id("nudge")
                                .direction(com.processpuzzle.workflow.model.EventDirection.CATCH)
                                .attachedTo("issue").interrupting(false)
                                .timer(new com.processpuzzle.workflow.model.TimerDefinition().expression("R2/PT5M"))));

        Workflow domain = mapper.toDomain("org-1", input);

        EventUse overdue = domain.getEvents().get(0);
        assertThat(overdue.getTimer()).isEqualTo(new TimerDefinition(TimerType.DURATION, "PT1H"));
        assertThat(overdue.getAttachedTo()).isEqualTo("issue");
        assertThat(overdue.isInterrupting()).isTrue();
        assertThat(domain.getEvents().get(1).isInterrupting()).isFalse();
        assertThat(domain.getEvents().get(1).getTimer().getType()).isNull();
        assertThat(domain.getStartEvents().get(0).getTimer()).isEqualTo(new TimerDefinition(TimerType.CYCLE, "R/P1D"));

        var model = mapper.toModel(domain);
        assertThat(model.getEvents().get(0).getTimer().getType()).isEqualTo(com.processpuzzle.workflow.model.TimerType.DURATION);
        assertThat(model.getEvents().get(0).getTimer().getExpression()).isEqualTo("PT1H");
        assertThat(model.getEvents().get(0).getAttachedTo()).isEqualTo("issue");
        assertThat(model.getEvents().get(1).getInterrupting()).isFalse();
        assertThat(model.getEvents().get(1).getTimer().getType()).isNull();
        assertThat(model.getStartEvents().get(0).getTimer().getExpression()).isEqualTo("R/P1D");
        assertThat(mapper.toModel(Workflow.builder().id("x").events(List.of(EventUse.builder().id("e").build())).build())
                .getEvents().get(0).getTimer()).isNull();
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

    /** A start event's own collections are nullable too — ROLE_DEFINITION uses none of them. */
    @Test
    void startEventMappingToleratesAbsentCollections() {
        Workflow domain = mapper.toDomain("org-1", new WorkflowInput().id("wf-1").name("Workflow 1")
                .startEvents(List.of(new com.processpuzzle.workflow.model.StartEvent()
                        .id("start")
                        .startType(null)
                        .requiredArtifacts(null)
                        .authorizedRoles(null))));

        var startEvent = domain.getStartEvents().get(0);
        assertThat(startEvent.getStartType()).isNull();
        assertThat(startEvent.getRequiredArtifacts()).isEmpty();
        assertThat(startEvent.getAuthorizedRoles()).isNull();

        var model = mapper.toModel(domain).getStartEvents().get(0);
        assertThat(model.getStartType()).isNull();
        assertThat(model.getAuthorizedRoles()).isNull();
    }

    /** The TRIGGERING_EVENT and TIME_BASED_PRECONDITION fields ride the same flat schema. */
    @Test
    void startEventMappingCarriesEveryMechanismsFields() {
        Workflow domain = mapper.toDomain("org-1", new WorkflowInput().id("wf-1").name("Workflow 1")
                .startEvents(List.of(new com.processpuzzle.workflow.model.StartEvent()
                        .id("submitted")
                        .startType(com.processpuzzle.workflow.model.WorkflowStartConditionType.TRIGGERING_EVENT)
                        .eventType("order.submitted")
                        .payloadMapping(Map.of("orderId", "$.id"))
                        .authorizedRoles(List.of("clerk"))
                        .milestoneRef("MILESTONE_REACHED")
                        .preconditionExpression("milestone.status == 'PASSED'"))));

        var condition = domain.getStartEvents().get(0);
        assertThat(condition.getEventType()).isEqualTo("order.submitted");
        assertThat(condition.getPayloadMapping()).containsEntry("orderId", "$.id");
        assertThat(condition.getAuthorizedRoles()).containsExactly("clerk");
        assertThat(condition.getMilestoneRef()).isEqualTo("MILESTONE_REACHED");
        assertThat(condition.getPreconditionExpression()).isEqualTo("milestone.status == 'PASSED'");

        var model = mapper.toModel(domain).getStartEvents().get(0);
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
