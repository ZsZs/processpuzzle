package com.processpuzzle.objectstore.usecases.inbound;

import com.processpuzzle.objectstore.adapters.outbound.MinioProperties;
import com.processpuzzle.objectstore.usecases.outbound.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteObjectTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private MinioProperties minioProperties;

    private DeleteObject deleteObject;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        deleteObject = new DeleteObject(fileStorageService, minioProperties);

        Map<String, String> buckets = new HashMap<>();
        buckets.put("documents", "test-bucket");
        when(minioProperties.getBuckets()).thenReturn(buckets);
    }

    @Test
    void execute_shouldCallFileStorageServiceWithDefaultBucket() {
        String objectID = "test-object-id";
        deleteObject.execute(objectID);

        verify(fileStorageService).deleteObject("test-bucket", objectID);
    }
}
