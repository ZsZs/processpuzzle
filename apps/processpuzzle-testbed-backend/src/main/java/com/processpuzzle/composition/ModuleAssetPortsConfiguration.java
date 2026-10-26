package com.processpuzzle.composition;

import com.processpuzzle.app.usecase.port.ModuleAssetStore;
import com.processpuzzle.store.usecases.outbound.FileStorageService;
import com.processpuzzle.store.usecases.outbound.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires base-app's outbound port for module assets to object storage.
 *
 * <p>One bucket for all of them, {@code <prefix>-modules}, with each object named {@code <orgKey>/<sha256>}:
 * named by content, so a path replaced by identical bytes stores nothing new and a release never copies a file,
 * and prefixed by the organization, so erasing a tenant is one delete by prefix. This does not go through the
 * {@code /objects} endpoints on purpose — those hand out an id that anyone holding it can fetch, while a module
 * asset is served by base-app, which checks the organization and the channel first.
 */
@Configuration
public class ModuleAssetPortsConfiguration {

    @Bean
    public ModuleAssetStore moduleAssetStore(FileStorageService storage, @Value("${minio.bucket-prefix:}") String bucketPrefix) {
        String bucket = bucketPrefix == null || bucketPrefix.isBlank() ? "modules" : bucketPrefix + "-modules";
        return new ModuleAssetStore() {
            @Override
            public void put(String orgKey, String sha256, byte[] content, String mimeType) {
                if (!storage.bucketExists(bucket)) {
                    storage.createBucket(bucket);
                }
                String name = objectName(orgKey, sha256);
                if (!storage.objectExists(bucket, name)) {
                    storage.uploadObject(bucket, name, new ByteArrayInputStream(content), mimeType, Map.of("mimeType", mimeType));
                }
            }

            @Override
            public Optional<byte[]> get(String orgKey, String sha256) {
                String name = objectName(orgKey, sha256);
                if (!storage.bucketExists(bucket) || !storage.objectExists(bucket, name)) {
                    return Optional.empty();
                }
                StoredObject object = storage.getObject(bucket, name);
                try (var stream = object.inputStream()) {
                    return Optional.of(stream.readAllBytes());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void delete(String orgKey, String sha256) {
                if (storage.bucketExists(bucket)) {
                    storage.deleteObject(bucket, objectName(orgKey, sha256));
                }
            }

            @Override
            public void deleteAll(String orgKey) {
                storage.deleteByPrefix(bucket, orgKey + "/");
            }
        };
    }

    private static String objectName(String orgKey, String sha256) {
        return orgKey + "/" + sha256;
    }
}
