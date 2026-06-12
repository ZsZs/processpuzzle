# ProcessPuzzle :: Object Store

ProcessPuzzle Object Store is a Spring Boot-based library designed to provide a unified interface for file storage operations, specifically integrating with [MinIO](https://min.io/). It follows a clean architecture (Hexagonal) to decouple storage logic from external storage providers and API protocols.

## Features

- **Bucket Management**: Retrieve bucket names and check for their existence.
- **Object Operations**:
    - **Upload**: Securely upload files with metadata and MIME type support.
    - **Download**: Retrieve objects as input streams.
    - **Delete**: Remove objects from specific buckets.
    - **URI Generation**: Get the public or internal URI for an object.
- **Metadata Support**: Handle custom headers like `X-Object-Name` and `X-Object-Bucket`.
- **Spring Boot Integration**: Easy configuration via standard properties and YAML factories.

## Technologies

- **Java 17+**
- **Spring Boot 3+**
- **MinIO Java SDK**
- **Project Lombok**
- **Maven** for building and dependency management
- **Nx** for monorepo task execution

## Configuration

The library uses `@ConfigurationProperties` to manage MinIO connection details. You can provide these in your `application.yaml` or a dedicated `minio-config.yaml`.

```yaml
minio:
  endpoint: http://localhost:9000
  accessKey: YOUR_ACCESS_KEY
  secretKey: YOUR_SECRET_KEY
  buckets:
    default: "my-bucket"
  mimeTypes:
    pdf: "application/pdf"
    png: "image/png"
```

### Properties

| Property | Description |
|----------|-------------|
| `minio.endpoint` | The URL of the MinIO server. |
| `minio.accessKey` | Access key for authentication. |
| `minio.secretKey` | Secret key for authentication. |
| `minio.buckets` | Map of logical bucket names to actual MinIO bucket names. |
| `minio.mimeTypes` | Map of extensions to MIME types. |

## Usage

### Using Use Cases

The library exposes its core functionality through specific "Use Case" classes (Inbound Ports):

- `UploadObject`: `execute(String fileName, InputStream inputStream, String mimeType)`
- `GetObject`: `execute(String bucketName, String objectID)`
- `DeleteObject`: `execute(String bucketName, String objectID)`
- `GetObjectUri`: `execute(String bucketName, String objectID)`

Example:
```java
@Autowired
private UploadObject uploadObject;

public void saveFile(MultipartFile file) throws IOException {
    String objectID = uploadObject.execute(
        file.getOriginalFilename(), 
        file.getInputStream(), 
        file.getContentType()
    );
}
```

### REST API

The library includes an `ObjectEndpoint` (Inbound Adapter) which implements the `DefaultApi` interface (generated from OpenAPI).

- `POST /uploadObject`: Upload a file.
- `GET /getObjectByID/{bucketName}/{objectID}`: Retrieve a file.
- `DELETE /deleteObjectByID/{bucketName}/{objectID}`: Delete a file.
- `GET /getObjectUriByID/{bucketName}/{objectID}`: Get the URI for a file.

## Development

### Build

To build the library using Nx:

```powershell
npm exec nx build object-store
```

Or using Maven directly:

```powershell
mvn clean install -pl libs/java-shared/object-store -am
```

### Testing

Run unit tests:

```powershell
npm exec nx test object-store
```

### Linting

Run checkstyle validation:

```powershell
npm exec nx lint object-store
```

## License

This project is licensed under the Apache License 2.0.
