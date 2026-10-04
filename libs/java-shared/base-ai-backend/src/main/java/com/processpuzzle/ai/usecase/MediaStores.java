package com.processpuzzle.ai.usecase;

import com.processpuzzle.ai.usecase.port.MediaStore;
import com.processpuzzle.ai.usecase.port.MediaStoreUnavailableException;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The wired {@link MediaStore}, or one that refuses every call when the application wires none. Resolved
 * with {@code getIfUnique} for the reason {@code OrganizationGuard} documents.
 */
@Component
public class MediaStores {

    private static final MediaStore UNAVAILABLE = new MediaStore() {
        @Override
        public String uploadUrl(String objectName, String contentType, Duration expiry) {
            throw unavailable();
        }

        @Override
        public String readUrl(String objectName, Duration expiry) {
            throw unavailable();
        }

        @Override
        public String internalReadUrl(String objectName, Duration expiry) {
            throw unavailable();
        }

        @Override
        public boolean exists(String objectName) {
            throw unavailable();
        }

        @Override
        public void put(String objectName, byte[] data, String contentType) {
            throw unavailable();
        }

        @Override
        public void delete(String objectName) {
            throw unavailable();
        }

        private MediaStoreUnavailableException unavailable() {
            return new MediaStoreUnavailableException("No media store is configured for the AI feature.");
        }
    };

    private final MediaStore store;

    public MediaStores(ObjectProvider<MediaStore> store) {
        this.store = store.getIfUnique(() -> UNAVAILABLE);
    }

    public MediaStore get() {
        return store;
    }
}
