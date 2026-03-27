package com.processpuzzle.objectstore.usecases.outbound;

import io.minio.*;
import io.minio.messages.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MinioFileStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private MinioFileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fileStorageService = new MinioFileStorageService(minioClient);
    }

    @Test
    void createBucket_whenBucketDoesNotExist_shouldMakeBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        fileStorageService.createBucket("test-bucket");

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void createBucket_whenBucketExists_shouldNotMakeBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        fileStorageService.createBucket("test-bucket");

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void deleteBucket_shouldRemoveBucket() throws Exception {
        fileStorageService.deleteBucket("test-bucket");

        verify(minioClient).removeBucket(any(RemoveBucketArgs.class));
    }

    @Test
    void bucketExists_shouldReturnCorrectValue() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        assertTrue(fileStorageService.bucketExists("test-bucket"));

        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        assertFalse(fileStorageService.bucketExists("test-bucket"));
    }

    @Test
    void listBuckets_shouldReturnNames() throws Exception {
        Bucket bucket = mock(Bucket.class);
        when(bucket.name()).thenReturn("test-bucket");
        when(minioClient.listBuckets()).thenReturn(Collections.singletonList(bucket));

        List<String> buckets = fileStorageService.listBuckets();

        assertEquals(1, buckets.size());
        assertEquals("test-bucket", buckets.get(0));
    }

    @Test
    void uploadObject_shouldPutObject() throws Exception {
        String bucketName = "test-bucket";
        String objectName = "test-object";
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        String contentType = "text/plain";
        Map<String, String> metadata = Map.of("name", "test-object", "mimeType", contentType);

        fileStorageService.uploadObject(bucketName, objectName, inputStream, contentType, metadata);

        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void getObject_shouldReturnStoredObject() throws Exception {
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);
        StatObjectResponse mockStat = mock(StatObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mockStat);
        when(mockStat.userMetadata()).thenReturn(Map.of("custom", "value"));
        when(mockStat.contentType()).thenReturn("application/pdf");

        StoredObject storedObject = fileStorageService.getObject("test-bucket", "test-object");

        assertNotNull(storedObject);
        assertEquals(mockResponse, storedObject.inputStream());
        assertEquals("application/pdf", storedObject.metadata().get("Content-Type"));
        assertEquals("test-object", storedObject.metadata().get("X-Object-Name"));
        assertEquals("test-bucket", storedObject.metadata().get("X-Object-Bucket"));
        assertEquals("value", storedObject.metadata().get("custom"));

        verify(minioClient).getObject(any(GetObjectArgs.class));
        verify(minioClient).statObject(any(StatObjectArgs.class));
    }

    @Test
    void getObjectUri_shouldReturnPresignedUrl() throws Exception {
        String expectedUrl = "http://localhost:9000/test-bucket/test-object?signed=true";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(expectedUrl);

        String actualUrl = fileStorageService.getObjectUri("test-bucket", "test-object");

        assertEquals(expectedUrl, actualUrl);
        verify(minioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void deleteObject_shouldRemoveObject() throws Exception {
        fileStorageService.deleteObject("test-bucket", "test-object");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }
}
