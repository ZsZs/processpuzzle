package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.processpuzzle.ai.galleries.SubjectGalleries;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectCreatedEvent;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectDeletedEvent;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectUpdatedEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiSubjectChangeListenerTest {

    private static final String ORG = "processpuzzle-testbed";
    private static final UUID BOAT = UUID.randomUUID();

    private final SubjectGalleries galleries = mock(SubjectGalleries.class);
    private final AiSubjectChangeListener listener = new AiSubjectChangeListener(galleries);

    @Test
    void createdAndUpdatedObjectsAreRelayedAsChanges() {
        listener.on(new EntityObjectCreatedEvent(ORG, "boat", BOAT, Map.of(), 1, Instant.now()));
        listener.on(new EntityObjectUpdatedEvent(ORG, "boat", BOAT, Map.of(), 2, Instant.now()));

        verify(galleries, times(2)).subjectChanged(ORG, "boat", BOAT);
        verifyNoMoreInteractions(galleries);
    }

    @Test
    void aDeletedObjectIsRelayedAsADeletion() {
        listener.on(new EntityObjectDeletedEvent(ORG, "boat", BOAT, Instant.now()));

        verify(galleries).subjectDeleted(ORG, "boat", BOAT);
        verifyNoMoreInteractions(galleries);
    }

    @Test
    void aFailingGalleryNeverReachesThePublisher() {
        doThrow(new IllegalStateException("database down")).when(galleries).subjectChanged(ORG, "boat", BOAT);
        doThrow(new IllegalStateException("database down")).when(galleries).subjectDeleted(ORG, "boat", BOAT);

        assertThatCode(() -> listener.on(new EntityObjectCreatedEvent(ORG, "boat", BOAT, null, 1, Instant.now())))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.on(new EntityObjectUpdatedEvent(ORG, "boat", BOAT, null, 2, Instant.now())))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.on(new EntityObjectDeletedEvent(ORG, "boat", BOAT, Instant.now())))
                .doesNotThrowAnyException();
    }
}
