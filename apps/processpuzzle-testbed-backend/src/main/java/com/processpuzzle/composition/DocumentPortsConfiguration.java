package com.processpuzzle.composition;

import com.processpuzzle.document.usecase.port.DocumentFileStore;
import com.processpuzzle.store.usecases.outbound.FileStorageService;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires base-document's outbound port to object storage. The PDFs of signed reports land where any
 * other {@code application/pdf} upload does — the stack's {@code <prefix>-documents} bucket, which is what
 * processpuzzle-store's {@code BucketNameFinder} answers for a type it has no bucket of its own for (that class is
 * not exposed to this module, so the name is built the same way here, as {@link AiPortsConfiguration} does for
 * its bucket) — so that the {@code /objects/{bucket}/{id}} endpoints that serve every other file serve them too,
 * and no storage endpoint of base-document's own is needed.
 */
@Configuration
public class DocumentPortsConfiguration {

    @Bean
    public DocumentFileStore documentFileStore(FileStorageService storage, @Value("${minio.bucket-prefix:}") String bucketPrefix) {
        String bucket = bucketPrefix == null || bucketPrefix.isBlank() ? "documents" : bucketPrefix + "-documents";
        return (content, fileName, mimeType) -> {
            if (!storage.bucketExists(bucket)) {
                storage.createBucket(bucket);
            }
            String objectId = UUID.randomUUID().toString();
            Map<String, String> metadata = new HashMap<>();
            metadata.put("bucket", bucket);
            metadata.put("name", fileName);
            metadata.put("mimeType", mimeType);
            storage.uploadObject(bucket, objectId, new ByteArrayInputStream(content), mimeType, metadata);
            return new DocumentFileStore.StoredFile(bucket, objectId);
        };
    }
}
