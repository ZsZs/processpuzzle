package com.processpuzzle.store.adapters.inbound;

import com.processpuzzle.shared.event.StoredObjectsReleasedEvent;
import com.processpuzzle.shared.event.StoredObjectsReleasedEvent.StoredObjectRef;
import com.processpuzzle.store.usecases.inbound.ThumbnailNaming;
import com.processpuzzle.store.usecases.outbound.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deletes the files a feature has stopped referring to, once the deletion of the references has
 * committed.
 *
 * <p><b>After commit, and only then.</b> The references are rows in another feature's tables, deleted
 * in the transaction that published the event. A file deleted before that transaction commits could
 * leave a tenant whose erasure then rolled back pointing at files that are gone; a file still present
 * after its rows are gone is only storage. So this waits for the commit, and a rolled-back transaction
 * releases nothing.
 *
 * <p><b>Each object's thumbnail goes with it.</b> {@code UploadObject} writes one for every image
 * under {@link ThumbnailNaming#thumbnailKey}, and the feature that released the object never knew it
 * existed. Asking for a thumbnail that was never written is harmless — the delete is idempotent.
 *
 * <p><b>Failures are logged, not thrown.</b> The transaction this would report to has already
 * committed, and the caller is gone. What is left behind is a file nothing refers to, which a later
 * sweep of the bucket can remove; rethrowing would only put the same stack trace in a different log.
 */
@Component
public class ReleasedObjectsCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(ReleasedObjectsCleaner.class);

    private final FileStorageService storage;

    public ReleasedObjectsCleaner(FileStorageService storage) {
        this.storage = storage;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onObjectsReleased(StoredObjectsReleasedEvent event) {
        for (Map.Entry<String, List<String>> bucket : byBucket(event.objects()).entrySet()) {
            try {
                storage.deleteObjects(bucket.getKey(), bucket.getValue());
            } catch (RuntimeException ex) {
                LOG.error("Could not delete {} object(s) of organization '{}' from bucket '{}'; they are "
                        + "no longer referenced and can be removed by hand.", bucket.getValue().size(),
                        event.orgKey(), bucket.getKey(), ex);
            }
        }
        if (!event.objects().isEmpty()) {
            LOG.info("Deleted {} released object(s) of organization '{}'.", event.objects().size(),
                    event.orgKey());
        }
    }

    private static Map<String, List<String>> byBucket(List<StoredObjectRef> objects) {
        Map<String, List<String>> names = new LinkedHashMap<>();
        for (StoredObjectRef object : objects) {
            if (object.bucket() == null || object.objectId() == null) {
                continue;
            }
            List<String> bucket = names.computeIfAbsent(object.bucket(), key -> new ArrayList<>());
            bucket.add(object.objectId());
            bucket.add(ThumbnailNaming.thumbnailKey(object.objectId()));
        }
        return names;
    }
}
