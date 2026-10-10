package com.processpuzzle.shared.event;

import java.util.List;

/**
 * Files in object storage that no row refers to any more, and that the store should delete.
 *
 * <p>Published by a feature that has deleted the rows naming them — today base-entity, when a tenant
 * is erased and its objects' ARTIFACT values go with them. The feature knows <em>which</em> files
 * are referenced, because the references live in its own data; only the store can delete them. This
 * event is how the one hands the other the list without either depending on the other.
 *
 * <p><b>Publish it inside the transaction that deletes the references; the store acts after
 * commit.</b> A file deleted before the rows naming it are gone could leave a tenant whose deletion
 * then rolled back pointing at nothing, which is not recoverable; a file left behind after its rows
 * are gone is only storage, and is. So the store's listener is
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, and a rolled-back deletion releases
 * nothing.
 *
 * <p>Deleting is idempotent and best-effort on the store's side: an object already gone is not an
 * error, and a failure is logged rather than thrown, because the transaction it would report to has
 * already committed.
 *
 * @param orgKey the tenant the objects belonged to, for the log
 * @param objects the objects to delete; their derived objects (thumbnails) go with them
 */
public record StoredObjectsReleasedEvent(String orgKey, List<StoredObjectRef> objects) {

    public StoredObjectsReleasedEvent {
        objects = objects == null ? List.of() : List.copyOf(objects);
    }

    /**
     * One object, by where it is: the bucket and the object id within it, as an ARTIFACT value
     * records them.
     */
    public record StoredObjectRef(String bucket, String objectId) {
    }
}
