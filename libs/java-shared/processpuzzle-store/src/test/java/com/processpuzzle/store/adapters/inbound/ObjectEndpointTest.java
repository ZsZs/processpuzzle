package com.processpuzzle.store.adapters.inbound;

import com.processpuzzle.store.model.ObjectUriResponse;
import com.processpuzzle.store.model.UploadObject201Response;
import com.processpuzzle.store.usecases.inbound.DeleteObject;
import com.processpuzzle.store.usecases.inbound.GetObject;
import com.processpuzzle.store.usecases.inbound.GetObjectUri;
import com.processpuzzle.store.usecases.inbound.GetThumbnailUri;
import com.processpuzzle.store.usecases.inbound.UploadObject;
import com.processpuzzle.store.usecases.outbound.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    void deleteObjectByID_shouldDelegateAndReturn204() {
        ResponseEntity<Void> response = endpoint.deleteObjectByID("images", "abc");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(deleteObject).execute("images", "abc");
    }

    @Test
    void getObjectByID_shouldReturnResourceWithMetadataHeaders() {
        InputStream content = new ByteArrayInputStream("data".getBytes());
        Map<String, String> metadata = Map.of(
                "X-Object-Name", "photo.png",
                "X-Object-Bucket", "images",
                "Content-Type", "image/png");
        when(getObject.execute("images", "abc")).thenReturn(new StoredObject(content, metadata));

        ResponseEntity<Resource> response = endpoint.getObjectByID("images", "abc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        HttpHeaders headers = response.getHeaders();
        assertEquals("photo.png", headers.getFirst("X-Object-Name"));
        assertEquals("images", headers.getFirst("X-Object-Bucket"));
        assertEquals(MediaType.IMAGE_PNG, headers.getContentType());
    }

    @Test
    void getObjectUriByID_shouldReturnUri() {
        when(getObjectUri.execute("images", "abc")).thenReturn("http://localhost/images/abc");

        ResponseEntity<ObjectUriResponse> response = endpoint.getObjectUriByID("images", "abc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("http://localhost/images/abc", response.getBody().getUri());
    }

    @Test
    void uploadObject_shouldReturn201WithLocationHeader() throws IOException {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        InputStream content = new ByteArrayInputStream("data".getBytes());
        when(file.getInputStream()).thenReturn(content);
        when(uploadObject.execute(eq("photo.png"), any(InputStream.class), eq("image/png")))
                .thenReturn(new UploadObject.Result("images", "abc"));

        ResponseEntity<UploadObject201Response> response = endpoint.uploadObject(file, "photo.png", "image/png");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("abc", response.getBody().getObjectID());
        assertEquals("photo.png", response.getBody().getFileName());
        assertEquals("image/png", response.getBody().getMimeType());
        assertEquals("images", response.getHeaders().getFirst(HttpHeaders.LOCATION));
    }

    @Test
    void uploadObject_shouldReturn500_whenFileStreamFails() throws IOException {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("stream broken"));

        ResponseEntity<UploadObject201Response> response = endpoint.uploadObject(file, "photo.png", "image/png");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNull(response.getBody());
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
