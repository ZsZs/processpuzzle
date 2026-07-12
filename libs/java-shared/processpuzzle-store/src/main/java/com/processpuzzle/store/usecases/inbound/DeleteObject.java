package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.usecases.outbound.FileStorageService;
import org.springframework.stereotype.Service;

@Service
public class DeleteObject {
    private final FileStorageService fileStorageService;

    public DeleteObject(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public void execute(String bucketName, String objectID) {
        fileStorageService.deleteObject(bucketName, objectID);
    }
}
