package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.usecases.outbound.FileStorageService;
import com.processpuzzle.store.usecases.outbound.StoredObject;
import org.springframework.stereotype.Service;

@Service
public class GetObject {
    private final FileStorageService fileStorageService;

    public GetObject(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public StoredObject execute(String bucketName, String objectID) {
        return fileStorageService.getObject(bucketName, objectID);
    }
}
