package com.processpuzzle.store.usecases.outbound;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class MinioFileStorageService implements FileStorageService {
    private final MinioClient minioClient;

    public MinioFileStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public void createBucket(String bucketName) {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error creating bucket: " + bucketName, e);
        }
    }

    @Override
    public void deleteBucket(String bucketName) {
        try {
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            throw new RuntimeException("Error deleting bucket: " + bucketName, e);
        }
    }

    @Override
    public boolean bucketExists(String bucketName) {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            throw new RuntimeException("Error checking bucket existence: " + bucketName, e);
        }
    }

    @Override
    public List<String> listBuckets() {
        try {
            return minioClient.listBuckets().stream()
                    .map(Bucket::name)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Error listing buckets", e);
        }
    }

    @Override
    public void uploadObject(String bucketName, String objectName, InputStream inputStream, String contentType, Map<String, String> metadata) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, inputStream.available(), -1)
                            .contentType(contentType)
                            .userMetadata(metadata)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Error uploading object: " + objectName + " to bucket: " + bucketName, e);
        }
    }

    @Override
    public StoredObject getObject(String bucketName, String objectName) {
        try {
            GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());

            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());

            Map<String, String> metadata = new java.util.HashMap<>(stat.userMetadata());
            metadata.put("Content-Type", stat.contentType());
            metadata.put("X-Object-Name", objectName);
            metadata.put("X-Object-Bucket", bucketName);

            return new StoredObject(response, metadata);
        } catch (Exception e) {
            throw new RuntimeException("Error getting object: " + objectName + " from bucket: " + bucketName, e);
        }
    }

    @Override
    public String getObjectUri(String bucketName, String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Error getting signed URL for object: " + objectName + " from bucket: " + bucketName, e);
        }
    }

    @Override
    public boolean objectExists(String bucketName, String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new RuntimeException("Error checking object existence: " + objectName + " in bucket: " + bucketName, e);
        } catch (Exception e) {
            throw new RuntimeException("Error checking object existence: " + objectName + " in bucket: " + bucketName, e);
        }
    }

    @Override
    public void deleteObject(String bucketName, String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Error deleting object: " + objectName + " from bucket: " + bucketName, e);
        }
    }
}
