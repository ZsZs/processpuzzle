package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.adapters.outbound.MinioProperties;
import com.processpuzzle.store.usecases.outbound.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private ThumbnailGenerator thumbnailGenerator;

    private UploadObject uploadObject;
    private MinioProperties.Thumbnail thumbnailConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        uploadObject = new UploadObject(fileStorageService, createBucket, bucketNameFinder, minioProperties, thumbnailGenerator);

        lenient().when(bucketNameFinder.findBucketName("application/json")).thenReturn("config-bucket");
        lenient().when(bucketNameFinder.findBucketName("application/pdf")).thenReturn("test-bucket");
        lenient().when(bucketNameFinder.findBucketName("image/png")).thenReturn("images");
        lenient().when(bucketNameFinder.findBucketName("image/svg+xml")).thenReturn("images");

        thumbnailConfig = new MinioProperties.Thumbnail();
        lenient().when(minioProperties.getThumbnail()).thenReturn(thumbnailConfig);
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

    @Test
    void execute_shouldWriteBothOriginalAndThumbnailForImageMime() throws IOException {
        byte[] source = new byte[]{1, 2, 3};
        byte[] thumbnail = new byte[]{9, 9};
        when(fileStorageService.bucketExists("images")).thenReturn(true);
        when(thumbnailGenerator.generate(any(byte[].class), anyInt(), anyDouble())).thenReturn(thumbnail);

        uploadObject.execute("photo.png", new ByteArrayInputStream(source), "image/png");

        verify(fileStorageService).uploadObject(eq("images"), argThat(id -> !id.endsWith(ThumbnailNaming.SUFFIX)), any(ByteArrayInputStream.class), eq("image/png"), anyMap());
        verify(fileStorageService).uploadObject(eq("images"), argThat(id -> id.endsWith(ThumbnailNaming.SUFFIX)), any(ByteArrayInputStream.class), eq("image/jpeg"), anyMap());
    }

    @Test
    void execute_shouldNotGenerateThumbnailForNonImageMime() {
        uploadObject.execute("config.json", new ByteArrayInputStream("{}".getBytes()), "application/json");

        verify(fileStorageService, times(1)).uploadObject(anyString(), anyString(), any(InputStream.class), anyString(), anyMap());
        verifyNoInteractions(thumbnailGenerator);
    }

    @Test
    void execute_shouldNotGenerateThumbnailWhenDisabled() {
        thumbnailConfig.setEnabled(false);
        when(fileStorageService.bucketExists("images")).thenReturn(true);

        uploadObject.execute("photo.png", new ByteArrayInputStream(new byte[]{1, 2, 3}), "image/png");

        verify(fileStorageService, times(1)).uploadObject(anyString(), anyString(), any(InputStream.class), anyString(), anyMap());
        verifyNoInteractions(thumbnailGenerator);
    }

    @Test
    void execute_shouldSkipThumbnailForSvgMime() {
        when(fileStorageService.bucketExists("images")).thenReturn(true);

        uploadObject.execute("logo.svg", new ByteArrayInputStream("<svg/>".getBytes()), "image/svg+xml");

        verify(fileStorageService, times(1)).uploadObject(anyString(), anyString(), any(InputStream.class), anyString(), anyMap());
        verifyNoInteractions(thumbnailGenerator);
    }

    @Test
    void execute_shouldWrapIOException_whenReadingImageStreamFails() {
        when(fileStorageService.bucketExists("images")).thenReturn(true);
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };

        RuntimeException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> uploadObject.execute("photo.png", broken, "image/png"));

        org.junit.jupiter.api.Assertions.assertNotNull(thrown.getCause());
    }

    @Test
    void execute_shouldSucceedEvenWhenThumbnailGenerationFails() throws IOException {
        when(fileStorageService.bucketExists("images")).thenReturn(true);
        when(thumbnailGenerator.generate(any(byte[].class), anyInt(), anyDouble())).thenThrow(new IOException("bad image"));

        UploadObject.Result result = uploadObject.execute("photo.png", new ByteArrayInputStream(new byte[]{1, 2, 3}), "image/png");

        assertNotNull(result);
        assertEquals("images", result.bucketName());
        assertNotNull(result.objectID());
        verify(fileStorageService, times(1)).uploadObject(eq("images"), anyString(), any(ByteArrayInputStream.class), eq("image/png"), anyMap());
    }
}
