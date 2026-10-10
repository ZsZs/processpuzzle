package com.processpuzzle.store.adapters.inbound;

import com.processpuzzle.shared.event.StoredObjectsReleasedEvent;
import com.processpuzzle.shared.event.StoredObjectsReleasedEvent.StoredObjectRef;
import com.processpuzzle.store.usecases.outbound.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Released objects are deleted per bucket, with their thumbnails, once the release has committed. */
class ReleasedObjectsCleanerTest {

    private final FileStorageService storage = mock(FileStorageService.class);
    private final ReleasedObjectsCleaner cleaner = new ReleasedObjectsCleaner(storage);

    @Test
    void deletesEachObjectAndItsThumbnailBucketByBucket() {
        cleaner.onObjectsReleased(new StoredObjectsReleasedEvent("my-org", List.of(
                new StoredObjectRef("pp-images", "a"),
                new StoredObjectRef("pp-documents", "b"),
                new StoredObjectRef("pp-images", "c"))));

        verify(storage).deleteObjects("pp-images", List.of("a", "a-thumb", "c", "c-thumb"));
        verify(storage).deleteObjects("pp-documents", List.of("b", "b-thumb"));
    }

    /** The transaction has committed and nobody is left to report to; a leftover file is only storage. */
    @Test
    void aStorageFailureIsLoggedRatherThanThrown() {
        doThrow(new RuntimeException("minio down")).when(storage).deleteObjects(eq("pp-images"), anyCollection());

        assertThatCode(() -> cleaner.onObjectsReleased(new StoredObjectsReleasedEvent("my-org",
                List.of(new StoredObjectRef("pp-images", "a"))))).doesNotThrowAnyException();
    }

    /** Before commit, a rolled-back erasure would leave its rows pointing at files that are gone. */
    @Test
    void runsOnlyAfterTheReleasingTransactionCommits() throws NoSuchMethodException {
        TransactionalEventListener listener = ReleasedObjectsCleaner.class
                .getDeclaredMethod("onObjectsReleased", StoredObjectsReleasedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
