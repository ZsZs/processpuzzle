package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.usecases.outbound.FileStorageService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GetThumbnailUri {
    private final FileStorageService fileStorageService;

    public GetThumbnailUri(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public Optional<String> execute(String bucketName, String objectID) {
        String thumbnailKey = ThumbnailNaming.thumbnailKey(objectID);
        if (!fileStorageService.objectExists(bucketName, thumbnailKey)) {
            return Optional.empty();
        }
        return Optional.of(fileStorageService.getObjectUri(bucketName, thumbnailKey));
    }
}
