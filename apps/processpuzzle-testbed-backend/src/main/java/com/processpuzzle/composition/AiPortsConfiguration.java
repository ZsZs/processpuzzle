package com.processpuzzle.composition;

import com.processpuzzle.ai.usecase.port.MediaStore;
import com.processpuzzle.ai.usecase.port.SubjectDirectory;
import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.store.usecases.outbound.FileStorageService;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the AI feature's outbound ports to the modules that answer them: object storage to
 * processpuzzle-store, subjects to base-entity. Same arrangement, and same reasoning, as
 * {@link BaseAppPortsConfiguration}: base-ai-backend names neither module, and this application is the one
 * place that knows all three are deployed together.
 */
@Configuration
public class AiPortsConfiguration {

    /**
     * The AI feature's objects live in one bucket of their own, named like every other bucket of this
     * stack — {@code <prefix>-ai-media} — so that one MinIO can serve every stack without them sharing
     * it. Created on first use rather than at startup, so that a MinIO that is down delays enrollment
     * instead of failing the boot.
     */
    @Bean
    public MediaStore aiMediaStore(FileStorageService storage, @Value("${minio.bucket-prefix:}") String bucketPrefix) {
        String bucket = bucketPrefix == null || bucketPrefix.isBlank() ? "ai-media" : bucketPrefix + "-ai-media";
        return new FileStorageMediaStore(storage, bucket);
    }

    /**
     * Subjects are base-entity objects. {@code entityName} is base-entity's entity definition code, the
     * same mapping base-state's gateway makes. {@code orgKey} is accepted and not used: an
     * {@code EntityObject} has no organization column yet, as that gateway notes too.
     */
    @Bean
    public SubjectDirectory aiSubjectDirectory(EntityAttributeQuery attributes, EntityObjectAccess objects) {
        return new SubjectDirectory() {
            @Override
            public boolean entityTypeExists(String orgKey, String entityName) {
                return attributes.entityTypeExists(entityName);
            }

            @Override
            public boolean isTextAttribute(String orgKey, String entityName, String attributeKey) {
                return attributes.attributeKind(entityName, attributeKey)
                        .map(kind -> kind == EntityAttributeKind.TEXT)
                        .orElse(false);
            }

            @Override
            public boolean subjectExists(String orgKey, String entityName, UUID objectId) {
                try {
                    objects.find(entityName, objectId);
                    return true;
                } catch (EntityObjectAccessException.NotFound e) {
                    return false;
                }
            }

            @Override
            public Optional<String> identifier(String orgKey, String entityName, UUID objectId, String attributeKey) {
                try {
                    Object value = objects.find(entityName, objectId).payload().get(attributeKey);
                    return value == null || value.toString().isBlank() ? Optional.empty() : Optional.of(value.toString());
                } catch (EntityObjectAccessException.NotFound e) {
                    return Optional.empty();
                }
            }
        };
    }

    static final class FileStorageMediaStore implements MediaStore {

        private final FileStorageService storage;
        private final String bucket;
        private final AtomicBoolean bucketReady = new AtomicBoolean();

        FileStorageMediaStore(FileStorageService storage, String bucket) {
            this.storage = storage;
            this.bucket = bucket;
        }

        @Override
        public String uploadUrl(String objectName, String contentType, Duration expiry) {
            return storage.getUploadUri(bucket(), objectName, contentType, expiry);
        }

        /** processpuzzle-store signs browser URLs for one hour, the AI feature's default; {@code expiry} cannot shorten it. */
        @Override
        public String readUrl(String objectName, Duration expiry) {
            return storage.getObjectUri(bucket(), objectName);
        }

        @Override
        public String internalReadUrl(String objectName, Duration expiry) {
            return storage.getInternalObjectUri(bucket(), objectName, expiry);
        }

        @Override
        public boolean exists(String objectName) {
            return storage.objectExists(bucket(), objectName);
        }

        @Override
        public void put(String objectName, byte[] data, String contentType) {
            storage.uploadObject(bucket(), objectName, new ByteArrayInputStream(data), contentType, Map.of());
        }

        @Override
        public void delete(String objectName) {
            storage.deleteObject(bucket(), objectName);
        }

        private String bucket() {
            if (!bucketReady.get()) {
                storage.createBucket(bucket);
                bucketReady.set(true);
            }
            return bucket;
        }
    }
}
