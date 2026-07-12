package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.adapters.outbound.MinioProperties;
import com.processpuzzle.store.usecases.outbound.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadObjectTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private MinioProperties minioProperties;

    @Mock
    private CreateBucket createBucket;

    @Mock
    private BucketNameFinder bucketNameFinder;

    private UploadObject uploadObject;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        uploadObject = new UploadObject(fileStorageService, createBucket, bucketNameFinder);

        when(bucketNameFinder.findBucketName("application/json")).thenReturn("config-bucket");
        when(bucketNameFinder.findBucketName("application/pdf")).thenReturn("test-bucket");
    }

    @Test
    void execute_shouldUseBucketFromMimeTypeMapping() {
        String name = "config.json";
        InputStream inputStream = new ByteArrayInputStream("{}".getBytes());
        String mimeType = "application/json";

        uploadObject.execute(name, inputStream, mimeType);

        verify(fileStorageService).uploadObject(eq("config-bucket"), anyString(), eq(inputStream), eq(mimeType), anyMap());
    }

    @Test
    void execute_shouldFallbackToDefaultBucketIfMimeTypeMappingMissing() {
        String name = "test-file.pdf";
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        String mimeType = "application/pdf";

        uploadObject.execute(name, inputStream, mimeType);

        verify(fileStorageService).uploadObject(eq("test-bucket"), anyString(), eq(inputStream), eq(mimeType), anyMap());
    }
    @Test
    void execute_shouldCreateBucketIfItDoesNotExist() {
        String name = "test-file.pdf";
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        String mimeType = "application/pdf";
        String bucketName = "test-bucket";

        when(fileStorageService.bucketExists(bucketName)).thenReturn(false);

        uploadObject.execute(name, inputStream, mimeType);

        verify(createBucket).execute(bucketName);
        verify(fileStorageService).uploadObject(eq(bucketName), anyString(), eq(inputStream), eq(mimeType), anyMap());
    }
}
