package com.processpuzzle.objectstore.usecases.inbound;

import com.processpuzzle.objectstore.adapters.outbound.MinioProperties;
import com.processpuzzle.objectstore.usecases.outbound.FileStorageService;
import com.processpuzzle.objectstore.usecases.outbound.StoredObject;
import org.springframework.stereotype.Service;

@Service
public class GetObject {
    private final FileStorageService fileStorageService;
    private final BucketNameFinder bucketNameFinder;

    public GetObject(FileStorageService fileStorageService, MinioProperties minioProperties, BucketNameFinder bucketNameFinder) {
        this.fileStorageService = fileStorageService;
        this.bucketNameFinder = bucketNameFinder;
    }

    public StoredObject execute(String objectID, String mimeType) {
        String bucketName = bucketNameFinder.findBucketName(mimeType);
        return fileStorageService.getObject(bucketName, objectID);
    }
}
