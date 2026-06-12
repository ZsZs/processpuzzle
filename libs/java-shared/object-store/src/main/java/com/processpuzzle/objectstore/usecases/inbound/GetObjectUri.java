package com.processpuzzle.objectstore.usecases.inbound;

import com.processpuzzle.objectstore.usecases.outbound.FileStorageService;
import org.springframework.stereotype.Service;

@Service
public class GetObjectUri {
    private final FileStorageService fileStorageService;

    public GetObjectUri(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public String execute(String bucketName, String objectID) {
        return fileStorageService.getObjectUri(bucketName, objectID);
    }
}
