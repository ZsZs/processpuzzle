# ProcessPuzzle :: Base State Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-state-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_state_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_state_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/base-state-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/base-state-backend)

ProcessPuzzle Base State Backend is the server-side companion of [`@processpuzzle/base-state-frontend`](../../js-shared/base-state-frontend/README.md). It provides the building blocks for declaring, persisting, and executing state machines in a Spring Boot application following the platform's Hexagonal architecture.

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Status

This library is currently a scaffold. Domain, use cases, and adapter packages will be added as the state machine takes shape.

## Development

```powershell
npm exec nx build base-state-backend
npm exec nx test base-state-backend
npm exec nx lint base-state-backend
```

## License

This project is licensed under the Apache License 2.0.
