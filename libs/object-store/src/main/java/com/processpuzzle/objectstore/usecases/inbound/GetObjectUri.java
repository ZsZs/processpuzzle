package com.processpuzzle.objectstore.usecases.inbound;

import com.processpuzzle.objectstore.usecases.outbound.FileStorageService;
import org.springframework.stereotype.Service;

@Service
public class GetObjectUri {
    private final FileStorageService fileStorageService;
    private final BucketNameFinder bucketNameFinder;

    public GetObjectUri(FileStorageService fileStorageService, BucketNameFinder bucketNameFinder) {
        this.fileStorageService = fileStorageService;
        this.bucketNameFinder = bucketNameFinder;
    }

    public String execute(String objectID, String mimeType) {
        String bucketName = bucketNameFinder.findBucketName(mimeType);
        return fileStorageService.getObjectUri(bucketName, objectID);
    }
}
