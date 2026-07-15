package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.usecases.outbound.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GetThumbnailUriTest {

    @Mock
    private FileStorageService fileStorageService;

    private GetThumbnailUri getThumbnailUri;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getThumbnailUri = new GetThumbnailUri(fileStorageService);
    }

    @Test
    void execute_shouldReturnSignedUri_whenThumbnailExists() {
        String bucket = "images";
        String objectID = "abc";
        String thumbKey = objectID + ThumbnailNaming.SUFFIX;
        String signedUri = "http://localhost:7000/images/abc-thumb?signed=true";
        when(fileStorageService.objectExists(bucket, thumbKey)).thenReturn(true);
        when(fileStorageService.getObjectUri(bucket, thumbKey)).thenReturn(signedUri);

        Optional<String> result = getThumbnailUri.execute(bucket, objectID);

        assertTrue(result.isPresent());
        assertEquals(signedUri, result.get());
    }

    @Test
    void execute_shouldReturnEmpty_whenThumbnailDoesNotExist() {
        String bucket = "images";
        String objectID = "missing";
        when(fileStorageService.objectExists(bucket, objectID + ThumbnailNaming.SUFFIX)).thenReturn(false);

        Optional<String> result = getThumbnailUri.execute(bucket, objectID);

        assertTrue(result.isEmpty());
        verify(fileStorageService).objectExists(bucket, objectID + ThumbnailNaming.SUFFIX);
        verifyNoMoreInteractions(fileStorageService);
    }
}
