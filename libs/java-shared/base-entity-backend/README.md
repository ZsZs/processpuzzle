# ProcessPuzzle :: Base Entity Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-entity-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_entity_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_entity_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/base-entity-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/base-entity-backend)

ProcessPuzzle Base Entity Backend is the server-side companion of [`@processpuzzle/base-entity`](../../js-shared/base-entity-frontend/README.md). It provides the building blocks for declaring, persisting, and serving entity descriptors — and the entities they describe — in a Spring Boot application following the platform's Hexagonal architecture.

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Status

This library is currently a scaffold. Entities are served today by [`processpuzzle-store`](../processpuzzle-store/README.md), plain REST, or Firestore; domain, use cases, and adapter packages will be added as descriptor persistence moves into this library.

## Development

```powershell
npm exec nx build base-entity-backend
npm exec nx test base-entity-backend
npm exec nx lint base-entity-backend
```

## License

This project is licensed under the Apache License 2.0.
