package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.baseentity.instances.domain.event.EntityObjectCreatedEvent;
import com.processpuzzle.state.usecase.StartStateMachine;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityObjectCreatedListenerTest {

    private static final UUID OBJECT_ID = UUID.randomUUID();

    @Mock
    private StartStateMachine startStateMachine;

    private EntityObjectCreatedListener listener;

    @BeforeEach
    void setUp() {
        listener = new EntityObjectCreatedListener(startStateMachine);
    }

    private EntityObjectCreatedEvent event() {
        return new EntityObjectCreatedEvent(
                "acme", "order", OBJECT_ID, Map.of("total", 12), 0L, Instant.now());
    }

    @Test
    void handsTheCreatedObjectToStartStateMachine() {
        when(startStateMachine.execute("acme", "order", OBJECT_ID, Map.of("total", 12), 0L))
                .thenReturn(Optional.of("draft"));

        listener.on(event());

        verify(startStateMachine).execute("acme", "order", OBJECT_ID, Map.of("total", 12), 0L);
    }

    /**
     * The listener runs after the creating transaction has committed, so it cannot undo the
     * creation — a failure here has to be absorbed. Letting it propagate would surface as an
     * unhandled error on a request that already succeeded.
     */
    @Test
    void swallowsAFailureToWriteTheInitialState() {
        when(startStateMachine.execute("acme", "order", OBJECT_ID, Map.of("total", 12), 0L))
                .thenThrow(new UnsupportedOperationException("no EntityObjectGateway is configured"));

        assertThatCode(() -> listener.on(event())).doesNotThrowAnyException();
    }
}
