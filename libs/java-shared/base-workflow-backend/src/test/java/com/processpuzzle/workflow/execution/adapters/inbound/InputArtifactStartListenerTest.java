package com.processpuzzle.workflow.execution.adapters.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.RequiredStartArtifact;
import com.processpuzzle.workflow.definition.domain.StartEvent;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.usecases.inbound.StartEventAdmission;
import com.processpuzzle.workflow.execution.usecases.inbound.StartWorkflowInstanceUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InputArtifactStartListenerTest {

    private static final String ORG = "acme";
    private static final Instant AT = Instant.parse("2026-10-08T10:00:00Z");
    private static final ArtifactDefinition ORDER = ArtifactDefinition.builder().orgKey(ORG).id("order-entity")
            .name("Order").artifactType(ArtifactType.ENTITY).artifactTypeId("order").build();

    private WorkflowRepository workflows;
    private ResolveWorkflowUseCase resolve;
    private StartEventAdmission admission;
    private StartWorkflowInstanceUseCase start;
    private InputArtifactStartListener listener;

    @BeforeEach
    void setUp() {
        workflows = mock(WorkflowRepository.class);
        resolve = mock(ResolveWorkflowUseCase.class);
        admission = mock(StartEventAdmission.class);
        start = mock(StartWorkflowInstanceUseCase.class);
        listener = new InputArtifactStartListener(workflows, resolve, admission, start);
        when(admission.requiredArtifactsInState(any(), any(), any(), any(), any())).thenReturn(true);
        when(start.startTriggered(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(Optional.of(WorkflowInstance.builder().id(UUID.randomUUID()).build()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void anObjectEnteringTheRequiredStateStartsTheWorkflowWithTheMappedContext() {
        define(inputArtifact("delivered", "DELIVERED", Map.of("orderId", "$.subjectId", "number", "$.payload.orderNumber")));

        listener.on(stateChanged("DELIVERED"));

        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
        verify(start).startTriggered(eq(ORG), eq("feedback"), eq("delivered"), eq("order-1"), eq("order"), context.capture());
        assertThat(context.getValue()).containsEntry("orderId", "order-1").containsEntry("number", "O-1");
        // The fact proves the matched artifact in state; only the others are read back.
        verify(admission).requiredArtifactsInState(eq(ORG), any(), any(), eq("order-1"), eq(List.of("order-entity")));
    }

    @Test
    void anArtifactWithoutStateStartsOnCreation() {
        define(inputArtifact("created", null, null));

        listener.on(PlatformEvent.of(ORG, "order", "order-1", PlatformEventAction.CREATED, Map.of(), AT));

        verify(start).startTriggered(eq(ORG), eq("feedback"), eq("created"), eq("order-1"), eq("order"), anyMap());
    }

    @Test
    void anotherStateAnotherTypeOrAnUpdateStartsNothing() {
        define(inputArtifact("delivered", "DELIVERED", null));

        listener.on(stateChanged("SHIPPED"));
        listener.on(PlatformEvent.stateChanged(ORG, "invoice", "order-1", "DELIVERED", Map.of(), AT));
        listener.on(PlatformEvent.of(ORG, "order", "order-1", PlatformEventAction.UPDATED, Map.of(), AT));
        listener.on(PlatformEvent.of(ORG, "order", "order-1", PlatformEventAction.CREATED, Map.of(), AT));

        verifyNoInteractions(start);
    }

    @Test
    void theOtherRequiredArtifactsHaveToBeInStateToo() {
        define(inputArtifact("delivered", "DELIVERED", null));
        when(admission.requiredArtifactsInState(any(), any(), any(), any(), any())).thenReturn(false);

        listener.on(stateChanged("DELIVERED"));

        verifyNoInteractions(start);
    }

    @Test
    void aWorkflowThatCannotBeResolvedIsLoggedAndSkipped() {
        define(inputArtifact("delivered", "DELIVERED", null));
        when(resolve.resolveByOrgKeyAndId(ORG, "feedback")).thenThrow(new IllegalStateException("dangling"));

        listener.on(stateChanged("DELIVERED"));

        verifyNoInteractions(start);
    }

    private static PlatformEvent stateChanged(String state) {
        return PlatformEvent.stateChanged(ORG, "order", "order-1", state, Map.of("orderNumber", "O-1"), AT);
    }

    private void define(StartEvent startEvent) {
        Workflow workflow = Workflow.builder().orgKey(ORG).id("feedback").name("feedback")
                .startEvents(List.of(startEvent)).build();
        when(workflows.findByOrgKey(ORG)).thenReturn(List.of(workflow));
        when(resolve.resolveByOrgKeyAndId(ORG, "feedback"))
                .thenReturn(new ResolvedWorkflow(workflow, List.of(), List.of(ORDER), List.of()));
    }

    private static StartEvent inputArtifact(String id, String state, Map<String, String> mapping) {
        return StartEvent.builder().id(id).startType(WorkflowStartConditionType.INPUT_ARTIFACT)
                .requiredArtifacts(List.of(RequiredStartArtifact.builder().artifactDefinitionId("order-entity").state(state).build()))
                .payloadMapping(mapping).build();
    }
}
