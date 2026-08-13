# ProcessPuzzle :: Base Workflow Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-workflow-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_workflow_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_workflow_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/base-workflow-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/base-workflow-backend)

ProcessPuzzle Base Workflow Backend is the server-side companion of [`@processpuzzle/base-workflow-frontend`](../../js-shared/base-workflow-frontend/README.md). It provides the building blocks for defining, executing, and monitoring long-running business workflows in a Spring Boot application. It builds on top of [`base-state-backend`](../base-state-backend/README.md).

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Status

This library is currently a scaffold. Domain, use cases, and adapter packages will be added as the workflow engine takes shape.

## Development

```powershell
npm exec nx build base-workflow-backend
npm exec nx test base-workflow-backend
npm exec nx lint base-workflow-backend
```

## License

This project is licensed under the Apache License 2.0.
