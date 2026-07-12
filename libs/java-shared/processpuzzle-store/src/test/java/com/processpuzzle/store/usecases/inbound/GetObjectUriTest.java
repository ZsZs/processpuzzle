package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.usecases.outbound.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class GetObjectUriTest {

    @Mock
    private FileStorageService fileStorageService;

    private GetObjectUri getObjectUri;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getObjectUri = new GetObjectUri(fileStorageService);
    }

    @Test
    void execute_shouldReturnUriFromFileStorageService() {
        String objectID = "test-bucket/test-object-id";
        String expectedUri = "http://localhost:9000/test-bucket/test-object-id?signed=true";
        when(fileStorageService.getObjectUri("test-bucket", objectID)).thenReturn(expectedUri);

        String actualUri = getObjectUri.execute("test-bucket", objectID );

        assertEquals(expectedUri, actualUri);
    }
}
