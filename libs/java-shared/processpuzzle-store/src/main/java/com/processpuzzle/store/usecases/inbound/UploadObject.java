package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.adapters.outbound.MinioProperties;
import com.processpuzzle.store.usecases.outbound.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class UploadObject {
    private final FileStorageService fileStorageService;
    private final CreateBucket createBucket;
    private final BucketNameFinder bucketNameFinder;
    private final MinioProperties minioProperties;
    private final ThumbnailGenerator thumbnailGenerator;

    public UploadObject(FileStorageService fileStorageService, CreateBucket createBucket, BucketNameFinder bucketNameFinder,
                        MinioProperties minioProperties, ThumbnailGenerator thumbnailGenerator) {
        this.fileStorageService = fileStorageService;
        this.createBucket = createBucket;
        this.bucketNameFinder = bucketNameFinder;
        this.minioProperties = minioProperties;
        this.thumbnailGenerator = thumbnailGenerator;
    }

    public Result execute(String fileName, InputStream inputStream, String mimeType) {
        String bucketName = bucketNameFinder.findBucketName(mimeType);
        if (!fileStorageService.bucketExists(bucketName)) {
            createBucket.execute(bucketName);
        }
        String objectID = UUID.randomUUID().toString();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("bucket", bucketName);
        metadata.put("name", fileName);
        metadata.put("mimeType", mimeType);

        if (shouldGenerateThumbnail(mimeType)) {
            byte[] source;
            try {
                source = inputStream.readAllBytes();
            } catch (IOException e) {
                throw new RuntimeException("Error reading upload stream for " + fileName, e);
            }
            fileStorageService.uploadObject(bucketName, objectID, new ByteArrayInputStream(source), mimeType, metadata);
            storeThumbnailBestEffort(bucketName, objectID, source);
        } else {
            fileStorageService.uploadObject(bucketName, objectID, inputStream, mimeType, metadata);
        }
        return new Result(bucketName, objectID);
    }

    public record Result(String bucketName, String objectID) {}

    private boolean shouldGenerateThumbnail(String mimeType) {
        if (mimeType == null) return false;
        String normalized = mimeType.toLowerCase();
        if (!normalized.startsWith("image/") || normalized.equals("image/svg+xml")) return false;
        return minioProperties.getThumbnail().isEnabled();
    }

    private void storeThumbnailBestEffort(String bucketName, String objectID, byte[] source) {
        MinioProperties.Thumbnail cfg = minioProperties.getThumbnail();
        try {
            byte[] thumbnail = thumbnailGenerator.generate(source, cfg.getMaxDimension(), cfg.getQuality());
            String thumbKey = ThumbnailNaming.thumbnailKey(objectID);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("bucket", bucketName);
            metadata.put("name", thumbKey);
            metadata.put("mimeType", "image/jpeg");
            fileStorageService.uploadObject(bucketName, thumbKey, new ByteArrayInputStream(thumbnail), "image/jpeg", metadata);
        } catch (Exception e) {
            log.warn("Failed to generate/store thumbnail for {}/{}: {}", bucketName, objectID, e.getMessage());
        }
    }
}
