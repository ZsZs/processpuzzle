package com.processpuzzle.objectstore.adapters.inbound;

import com.processpuzzle.objectstore.api.DefaultApi;
import com.processpuzzle.objectstore.model.GetObjectByID200Response;
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
public class ObjectEndpoint implements DefaultApi {
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
    public ResponseEntity<Void> deleteObjectByID(String objectID) {
        deleteObject.execute(objectID);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    public ResponseEntity<GetObjectByID200Response> getObjectByID(String objectID, String mimeType) {
        StoredObject storedObject = getObject.execute(objectID, mimeType);
        Resource resource = new InputStreamResource(storedObject.inputStream());
        GetObjectByID200Response response = new GetObjectByID200Response();
        response.setMetadata(storedObject.metadata());
        response.setFile(resource);

        Map<String, String> metadata = storedObject.metadata();
        HttpHeaders headers = new HttpHeaders();
        if (metadata.containsKey("X-Object-Name")) {
            headers.add("X-Object-Name", metadata.get("X-Object-Name"));
        }
        if (metadata.containsKey("X-Object-Bucket")) {
            headers.add("X-Object-Bucket", metadata.get("X-Object-Bucket"));
        }
        if (metadata.containsKey("Content-Type")) {
            headers.add("Content-Type", metadata.get("Content-Type"));
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<GetObjectUriByID200Response> getObjectUriByID(String objectID, String mimeType) {
        String uri = getObjectUri.execute(objectID, mimeType);
        GetObjectUriByID200Response response = new GetObjectUriByID200Response();
        response.setUri(uri);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UploadObject201Response> uploadObject(MultipartFile file, String name, String mimeType) {
        try {
            uploadObject.execute(name, file.getInputStream(), mimeType);
            UploadObject201Response response = new UploadObject201Response();
            response.setObjectID(name);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
