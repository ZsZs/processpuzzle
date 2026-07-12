package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.adapters.outbound.MinioProperties;
import com.processpuzzle.store.usecases.outbound.FileStorageService;
import com.processpuzzle.store.usecases.outbound.StoredObject;
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

    private GetObject getObject;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getObject = new GetObject(fileStorageService);
    }

    @Test
    void execute_shouldReturnStoredObjectFromFileStorageService() {
        String objectID = "test-object-id";
        InputStream expectedInputStream = new ByteArrayInputStream("test content".getBytes());
        Map<String, String> expectedMetadata = Map.of("key", "value");
        StoredObject expectedStoredObject = new StoredObject(expectedInputStream, expectedMetadata);
        when(fileStorageService.getObject("test-bucket", objectID)).thenReturn(expectedStoredObject);

        StoredObject actualStoredObject = getObject.execute("test-bucket", objectID);

        assertEquals(expectedStoredObject, actualStoredObject);
    }
}
