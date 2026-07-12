package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.usecases.outbound.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.verify;

class DeleteObjectTest {

    @Mock
    private FileStorageService fileStorageService;

    private DeleteObject deleteObject;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        deleteObject = new DeleteObject(fileStorageService);
    }

    @Test
    void execute_shouldCallFileStorageServiceWithDefaultBucket() {
        String objectID = "test-object-id";
        deleteObject.execute("test-bucket", objectID);

        verify(fileStorageService).deleteObject("test-bucket", objectID);
    }
}
