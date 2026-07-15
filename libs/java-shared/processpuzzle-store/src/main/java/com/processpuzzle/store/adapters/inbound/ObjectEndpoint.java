package com.processpuzzle.store.adapters.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.store.api.ObjectStoreApi;
import com.processpuzzle.store.model.ObjectUriResponse;
import com.processpuzzle.store.model.UploadObject201Response;
import com.processpuzzle.store.usecases.inbound.DeleteObject;
import com.processpuzzle.store.usecases.inbound.GetObject;
import com.processpuzzle.store.usecases.inbound.GetObjectUri;
import com.processpuzzle.store.usecases.inbound.GetThumbnailUri;
import com.processpuzzle.store.usecases.inbound.UploadObject;
import com.processpuzzle.store.usecases.outbound.StoredObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@LogClass
public class ObjectEndpoint implements ObjectStoreApi {
    private final DeleteObject deleteObject;
    private final GetObject getObject;
    private final GetObjectUri getObjectUri;
    private final GetThumbnailUri getThumbnailUri;
    private final UploadObject uploadObject;

    public ObjectEndpoint(DeleteObject deleteObject, GetObject getObject, GetObjectUri getObjectUri, GetThumbnailUri getThumbnailUri, UploadObject uploadObject) {
        this.deleteObject = deleteObject;
        this.getObject = getObject;
        this.getObjectUri = getObjectUri;
        this.getThumbnailUri = getThumbnailUri;
        this.uploadObject = uploadObject;
    }

    @Override
    public ResponseEntity<Void> deleteObjectByID(String bucketName, String objectID) {
        deleteObject.execute(bucketName, objectID);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    public ResponseEntity<Resource> getObjectByID(String bucketName, String objectID) {
        StoredObject storedObject = getObject.execute(bucketName, objectID);
        Resource resource = new InputStreamResource(storedObject.inputStream());

        Map<String, String> metadata = storedObject.metadata();
        HttpHeaders headers = new HttpHeaders();
        if (metadata.containsKey("X-Object-Name")) {
            headers.add("X-Object-Name", metadata.get("X-Object-Name"));
        }
        if (metadata.containsKey("X-Object-Bucket")) {
            headers.add("X-Object-Bucket", metadata.get("X-Object-Bucket"));
        }
        if (metadata.containsKey("Content-Type")) {
            headers.setContentType(org.springframework.http.MediaType.valueOf(metadata.get("Content-Type")));
        }

        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<ObjectUriResponse> getObjectUriByID(String bucketName, String objectID) {
        String uri = getObjectUri.execute(bucketName, objectID);
        ObjectUriResponse response = new ObjectUriResponse();
        response.setUri(uri);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ObjectUriResponse> getThumbnailUriByID(String bucketName, String objectID) {
        return getThumbnailUri.execute(bucketName, objectID)
                .map(uri -> {
                    ObjectUriResponse response = new ObjectUriResponse();
                    response.setUri(uri);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<UploadObject201Response> uploadObject(MultipartFile file, String fileName, String mimeType) {
        try {
            UploadObject.Result result = uploadObject.execute(fileName, file.getInputStream(), mimeType);
            UploadObject201Response response = new UploadObject201Response();
            response.setObjectID(result.objectID());
            response.setFileName(fileName);
            response.setMimeType(mimeType);
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.LOCATION, result.bucketName());
            return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
