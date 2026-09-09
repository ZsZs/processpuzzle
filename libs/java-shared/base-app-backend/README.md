# ProcessPuzzle :: Base App Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-app-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_app_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_app_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/base-app-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/base-app-backend)

ProcessPuzzle Base App Backend is the server-side companion of [`@processpuzzle/base-app-frontend`](../../js-shared/base-app-frontend/README.md). It exposes application-level services (user workspace, preferences, panel layout) consumed by the Angular application shell.

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Status

This library is currently a scaffold. Domain, use cases, and adapter packages will be added as the application backend takes shape.

## Development

```powershell
npm exec nx build base-app-backend
npm exec nx test base-app-backend
npm exec nx lint base-app-backend
```

## License

This project is licensed under the Apache License 2.0.
