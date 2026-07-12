package com.processpuzzle.store.usecases.outbound;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface FileStorageService {
    void createBucket(String bucketName);
    void deleteBucket(String bucketName);
    boolean bucketExists(String bucketName);
    List<String> listBuckets();

    void uploadObject(String bucketName, String objectName, InputStream inputStream, String contentType, Map<String, String> metadata);
    StoredObject getObject(String bucketName, String objectName);
    String getObjectUri(String bucketName, String objectName);
    void deleteObject(String bucketName, String objectName);
}
