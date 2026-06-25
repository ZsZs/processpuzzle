package com.processpuzzle.objectstore.adapters.inbound;

import com.processpuzzle.objectstore.api.ObjectStoreApi;
import com.processpuzzle.objectstore.model.GetObjectUriByID200Response;
import com.processpuzzle.objectstore.model.UploadObject201Response;
import com.processpuzzle.objectstore.usecases.inbound.DeleteObject;
import com.processpuzzle.objectstore.usecases.inbound.GetObject;
import com.processpuzzle.objectstore.usecases.inbound.GetObjectUri;
import com.processpuzzle.objectstore.usecases.inbound.UploadObject;
import com.processpuzzle.objectstore.usecases.outbound.StoredObject;
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
public class ObjectEndpoint implements ObjectStoreApi {
    private final DeleteObject deleteObject;
    private final GetObject getObject;
    private final GetObjectUri getObjectUri;
    private final UploadObject uploadObject;

    public ObjectEndpoint(DeleteObject deleteObject, GetObject getObject, GetObjectUri getObjectUri, UploadObject uploadObject) {
        this.deleteObject = deleteObject;
        this.getObject = getObject;
        this.getObjectUri = getObjectUri;
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
    public ResponseEntity<GetObjectUriByID200Response> getObjectUriByID(String bucketName, String objectID) {
        String uri = getObjectUri.execute(bucketName, objectID);
        GetObjectUriByID200Response response = new GetObjectUriByID200Response();
        response.setUri(uri);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UploadObject201Response> uploadObject(MultipartFile file, String fileName, String mimeType) {
        try {
            String objectID = uploadObject.execute(fileName, file.getInputStream(), mimeType);
            UploadObject201Response response = new UploadObject201Response();
            response.setObjectID(objectID);
            response.setFileName(fileName);
            response.setMimeType(mimeType);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
