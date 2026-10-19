package com.processpuzzle.workflow.execution.adapters.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArtifactStateListenerTest {

    private static final String ORG = "org-1";

    private final ArtifactInstanceRepository artifacts = mock(ArtifactInstanceRepository.class);
    private final WorkflowInstanceRepository instances = mock(WorkflowInstanceRepository.class);
    private final ArtifactStateListener listener = new ArtifactStateListener(artifacts, instances);

    @Test
    void refreshesTheStateOfEveryArtifactBoundToTheRecordInARunningRun() {
        ArtifactInstance running = bound(WorkflowInstanceStatus.ACTIVE);
        ArtifactInstance finished = bound(WorkflowInstanceStatus.COMPLETED);
        when(artifacts.findByOrgKeyAndEntityTypeAndEntityId(ORG, "order", "o-1")).thenReturn(List.of(running, finished));

        listener.on(PlatformEvent.stateChanged(ORG, "order", "o-1", "CONFIRMED", Map.of(), Instant.now()));

        assertThat(running.getCurrentState()).isEqualTo("CONFIRMED");
        assertThat(finished.getCurrentState()).isEqualTo("DRAFT");
        verify(artifacts).save(running);
    }

    @Test
    void ignoresAnythingButAStateChange() {
        listener.on(PlatformEvent.of(ORG, "order", "o-1", PlatformEventAction.UPDATED, Map.of(), Instant.now()));

        verify(artifacts, never()).findByOrgKeyAndEntityTypeAndEntityId(any(), any(), any());
    }

    private ArtifactInstance bound(WorkflowInstanceStatus status) {
        UUID run = UUID.randomUUID();
        when(instances.findByOrgKeyAndId(ORG, run)).thenReturn(Optional.of(WorkflowInstance.builder().id(run).orgKey(ORG).status(status).build()));
        return ArtifactInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(run).artifactDefinitionId("order-entity")
                .name("Order").type(ArtifactType.ENTITY).entityType("order").entityId("o-1").currentState("DRAFT").build();
    }
}
