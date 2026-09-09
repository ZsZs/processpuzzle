package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.state.api.StateTransitionResult;
import com.processpuzzle.state.usecase.EntityObjectStateProjection;
import com.processpuzzle.state.usecase.FireStateTransition;
import com.processpuzzle.state.usecase.GetEntityObjectState;
import com.processpuzzle.state.usecase.TransitionOutcome;
import com.processpuzzle.state.usecase.exception.StaleEntityObjectVersionException;
import com.processpuzzle.state.usecase.exception.UnknownTriggerException;
import com.processpuzzle.state.usecase.port.EntityObjectGateway;
import com.processpuzzle.state.usecase.port.EntityObjectSnapshot;
import com.processpuzzle.state.usecase.service.EntityObjectGatewayResolver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StateOperationApiAdapterTest {

    private static final String ORG = "acme";
    private static final String ENTITY = "order";
    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final String ENTITY_ID = OBJECT_ID.toString();

    @Mock
    private FireStateTransition fireStateTransition;
    @Mock
    private GetEntityObjectState getEntityObjectState;
    @Mock
    private EntityObjectGatewayResolver gatewayResolver;
    @Mock
    private EntityObjectGateway gateway;

    private StateOperationApiAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new StateOperationApiAdapter(fireStateTransition, getEntityObjectState, gatewayResolver);
    }

    private void objectIsAtVersion(long version) {
        when(gatewayResolver.gateway()).thenReturn(gateway);
        when(gateway.findObject(ORG, ENTITY, OBJECT_ID))
                .thenReturn(new EntityObjectSnapshot(OBJECT_ID, version, Map.of()));
    }

    @Test
    void applyTrigger_readsTheCurrentVersionAndReportsTheNewState() {
        objectIsAtVersion(4L);
        TransitionOutcome outcome = TransitionOutcome.success("draft", "confirmed", "t1", List.of());
        when(fireStateTransition.execute(ORG, ENTITY, OBJECT_ID, "confirm", Map.of(), 4L))
                .thenReturn(new FireStateTransition.Result(outcome, 5L));

        StateTransitionResult result = adapter.applyTrigger(ORG, ENTITY, ENTITY_ID, "confirm");

        assertThat(result.success()).isTrue();
        assertThat(result.conflict()).isFalse();
        assertThat(result.newStateKey()).isEqualTo("confirmed");
        assertThat(result.message()).isNull();
        verify(fireStateTransition).execute(ORG, ENTITY, OBJECT_ID, "confirm", Map.of(), 4L);
    }

    @Test
    void applyTrigger_reportsAGuardRejectionAsAnUnsuccessfulNonConflict() {
        objectIsAtVersion(4L);
        TransitionOutcome outcome =
                new TransitionOutcome(false, "draft", null, "t1", List.of(), "total must be positive");
        when(fireStateTransition.execute(ORG, ENTITY, OBJECT_ID, "confirm", Map.of(), 4L))
                .thenReturn(new FireStateTransition.Result(outcome, 4L));

        StateTransitionResult result = adapter.applyTrigger(ORG, ENTITY, ENTITY_ID, "confirm");

        assertThat(result.success()).isFalse();
        assertThat(result.conflict()).isFalse();
        assertThat(result.newStateKey()).isNull();
        assertThat(result.message()).isEqualTo("total must be positive");
    }

    /**
     * The version is read here rather than supplied by the caller, so a write landing between the
     * read and the attempt is the one conflict this path can still see. It has to be reported as a
     * conflict and not as a plain rejection, or a caller would retry a trigger that was in fact
     * never evaluated against the object's real state.
     */
    @Test
    void applyTrigger_reportsAConcurrentWriteAsAConflict() {
        objectIsAtVersion(4L);
        when(fireStateTransition.execute(ORG, ENTITY, OBJECT_ID, "confirm", Map.of(), 4L))
                .thenThrow(new StaleEntityObjectVersionException(OBJECT_ID, 4L));

        StateTransitionResult result = adapter.applyTrigger(ORG, ENTITY, ENTITY_ID, "confirm");

        assertThat(result.success()).isFalse();
        assertThat(result.conflict()).isTrue();
        assertThat(result.message()).isNotBlank();
    }

    @Test
    void applyTrigger_reportsAnUnknownTriggerAsAnOrdinaryRejection() {
        objectIsAtVersion(4L);
        when(fireStateTransition.execute(ORG, ENTITY, OBJECT_ID, "teleport", Map.of(), 4L))
                .thenThrow(new UnknownTriggerException(ENTITY, "teleport"));

        StateTransitionResult result = adapter.applyTrigger(ORG, ENTITY, ENTITY_ID, "teleport");

        assertThat(result.success()).isFalse();
        assertThat(result.conflict()).isFalse();
        assertThat(result.message()).contains("teleport");
    }

    @Test
    void currentStateKey_delegatesToGetEntityObjectState() {
        when(getEntityObjectState.execute(ORG, ENTITY, OBJECT_ID)).thenReturn(
                new EntityObjectStateProjection(OBJECT_ID, ENTITY, "confirmed", false, Instant.now(), List.of()));

        assertThat(adapter.currentStateKey(ORG, ENTITY, ENTITY_ID)).isEqualTo("confirmed");
    }
}
