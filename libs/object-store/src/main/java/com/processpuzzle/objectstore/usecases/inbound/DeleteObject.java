package com.processpuzzle.objectstore.usecases.inbound;

import com.processpuzzle.objectstore.adapters.outbound.MinioProperties;
import com.processpuzzle.objectstore.usecases.outbound.FileStorageService;
import org.springframework.stereotype.Service;

@Service
public class DeleteObject {
    private final FileStorageService fileStorageService;
    private final MinioProperties minioProperties;

    public DeleteObject(FileStorageService fileStorageService, MinioProperties minioProperties) {
        this.fileStorageService = fileStorageService;
        this.minioProperties = minioProperties;
    }

    public void execute(String objectID) {
        String bucketName = minioProperties.getBuckets().get("documents");
        fileStorageService.deleteObject(bucketName, objectID);
    }
}
