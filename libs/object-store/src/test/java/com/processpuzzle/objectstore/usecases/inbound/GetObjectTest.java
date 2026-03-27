package com.processpuzzle.objectstore.usecases.inbound;

import com.processpuzzle.objectstore.adapters.outbound.MinioProperties;
import com.processpuzzle.objectstore.usecases.outbound.FileStorageService;
import com.processpuzzle.objectstore.usecases.outbound.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class GetObjectTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private MinioProperties minioProperties;

    @Mock
    private BucketNameFinder bucketNameFinder;

    private GetObject getObject;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getObject = new GetObject(fileStorageService, minioProperties, bucketNameFinder);

        when(bucketNameFinder.findBucketName("application/pdf")).thenReturn("test-bucket");
    }

    @Test
    void execute_shouldReturnStoredObjectFromFileStorageService() {
        String objectID = "test-object-id";
        String mimeType = "application/pdf";
        InputStream expectedInputStream = new ByteArrayInputStream("test content".getBytes());
        Map<String, String> expectedMetadata = Map.of("key", "value");
        StoredObject expectedStoredObject = new StoredObject(expectedInputStream, expectedMetadata);
        when(fileStorageService.getObject("test-bucket", objectID)).thenReturn(expectedStoredObject);

        StoredObject actualStoredObject = getObject.execute(objectID, mimeType);

        assertEquals(expectedStoredObject, actualStoredObject);
    }
}
