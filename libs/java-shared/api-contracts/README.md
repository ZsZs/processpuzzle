# ProcessPuzzle :: API Contracts

This library contains the API contracts and generated Java types for the ProcessPuzzle project. It serves as the single source of truth for API definitions, ensuring consistency and compatibility between client and server components.

## Overview

The library uses [OpenAPI 3.0.3](https://swagger.io/specification/) to define service interfaces and data models. Java code (Spring Boot interfaces and Jackson-annotated models) is automatically generated during the build process.

### Current API Definitions

- **Object Store API** (`object-store-api.yaml`): Provides endpoints for managing binary objects in buckets, including:
    - Uploading binary data with metadata.
    - Retrieving binary data and metadata by ID.
    - Deleting stored objects.
    - Generating signed, short-term URIs for downloads.

## Structure

- `src/main/resources/`: Contains the OpenAPI specification files (`.yaml`).
- `pom.xml`: Maven configuration for code generation and library publishing.
- `project.json`: Nx configuration for workspace integration.

## Usage

### As a Dependency

To use the generated contracts in your Spring Boot application, add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.processpuzzle</groupId>
    <artifactId>processpuzzle-api</artifactId>
    <version>${processpuzzle.version}</version>
</dependency>
```

### Generated Packages

The following packages are available after code generation:

- `com.processpuzzle.objectstore.api`: Contains the generated Spring `@Controller` interfaces.
- `com.processpuzzle.objectstore.model`: Contains the generated POJOs for request and response bodies.

## Development

### Building the Library

You can build the library using [Nx](https://nx.dev) from the workspace root:

```powershell
npx nx build api-contracts
```

Alternatively, you can use Maven directly:

```powershell
mvn clean install -f libs/java-shared/api-contracts/pom.xml
```

The build process invokes the `openapi-generator-maven-plugin`, which generates the source code in the `target/generated-sources/openapi` directory.

### Linting

Run the following command to validate the project and checkstyle:

```powershell
npx nx lint api-contracts
```

## Publishing

This library is configured for publication to Maven Central via the Sonatype Central Portal.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](../../../LICENSE) file for details.
