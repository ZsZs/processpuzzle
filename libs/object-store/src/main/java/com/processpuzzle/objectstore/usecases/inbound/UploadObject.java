package com.processpuzzle.objectstore.usecases.inbound;

import com.processpuzzle.objectstore.adapters.outbound.MinioProperties;
import com.processpuzzle.objectstore.usecases.outbound.FileStorageService;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UploadObject {
    private final FileStorageService fileStorageService;
    private final CreateBucket createBucket;
    private final BucketNameFinder bucketNameFinder;

    public UploadObject(FileStorageService fileStorageService, MinioProperties minioProperties, CreateBucket createBucket, BucketNameFinder bucketNameFinder) {
        this.fileStorageService = fileStorageService;
        this.createBucket = createBucket;
        this.bucketNameFinder = bucketNameFinder;
    }

    public void execute(String name, InputStream inputStream, String mimeType) {
        String bucketName = bucketNameFinder.findBucketName(mimeType);
        if (!fileStorageService.bucketExists(bucketName)) {
            createBucket.execute(bucketName);
        }
        String objectID = UUID.randomUUID().toString();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("name", name);
        metadata.put("mimeType", mimeType);
        fileStorageService.uploadObject(bucketName, objectID, inputStream, mimeType, metadata);
    }
}
