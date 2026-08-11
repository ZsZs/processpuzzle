# ProcessPuzzle :: Base Artifact Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-artifact-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_artifact_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_artifact_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/base-artifact-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/base-artifact-backend)

ProcessPuzzle Base Artifact Backend is the server-side companion of [`@processpuzzle/base-artifact`](../../js-shared/base-artifact-frontend/README.md). It provides the building blocks for describing, storing, and retrieving the artifacts (documents, media, generated output) an application produces and consumes, in a Spring Boot application following the platform's Hexagonal architecture.

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Status

This library is currently a scaffold. Domain, use cases, and adapter packages will be added as artifact management takes shape.

## Development

```powershell
npm exec nx build base-artifact-backend
npm exec nx test base-artifact-backend
npm exec nx lint base-artifact-backend
```

## License

This project is licensed under the Apache License 2.0.
