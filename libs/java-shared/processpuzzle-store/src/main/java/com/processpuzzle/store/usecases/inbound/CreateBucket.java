package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.usecases.outbound.FileStorageService;
import org.springframework.stereotype.Service;

@Service
public class CreateBucket {
    private final FileStorageService fileStorageService;

    public CreateBucket(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public void execute(String bucketName) {
        fileStorageService.createBucket(bucketName);
    }
}
