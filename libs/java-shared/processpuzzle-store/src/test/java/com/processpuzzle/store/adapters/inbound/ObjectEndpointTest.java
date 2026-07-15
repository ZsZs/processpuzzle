package com.processpuzzle.store.adapters.inbound;

import com.processpuzzle.store.model.ObjectUriResponse;
import com.processpuzzle.store.usecases.inbound.DeleteObject;
import com.processpuzzle.store.usecases.inbound.GetObject;
import com.processpuzzle.store.usecases.inbound.GetObjectUri;
import com.processpuzzle.store.usecases.inbound.GetThumbnailUri;
import com.processpuzzle.store.usecases.inbound.UploadObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

class ObjectEndpointTest {

    @Mock
    private DeleteObject deleteObject;

    @Mock
    private GetObject getObject;

    @Mock
    private GetObjectUri getObjectUri;

    @Mock
    private GetThumbnailUri getThumbnailUri;

    @Mock
    private UploadObject uploadObject;

    private ObjectEndpoint endpoint;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        endpoint = new ObjectEndpoint(deleteObject, getObject, getObjectUri, getThumbnailUri, uploadObject);
    }

    @Test
    void getThumbnailUriByID_shouldReturn200WithUri_whenThumbnailPresent() {
        String bucket = "images";
        String objectID = "abc";
        String signedUri = "http://localhost:7000/images/abc-thumb?signed=true";
        when(getThumbnailUri.execute(bucket, objectID)).thenReturn(Optional.of(signedUri));

        ResponseEntity<ObjectUriResponse> response = endpoint.getThumbnailUriByID(bucket, objectID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(signedUri, response.getBody().getUri());
    }

    @Test
    void getThumbnailUriByID_shouldReturn404_whenThumbnailMissing() {
        when(getThumbnailUri.execute("images", "missing")).thenReturn(Optional.empty());

        ResponseEntity<ObjectUriResponse> response = endpoint.getThumbnailUriByID("images", "missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }
}
