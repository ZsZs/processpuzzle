# ProcessPuzzle :: Base Rule Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-rule-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_rule_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_rule_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/base-rule-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/base-rule-backend)

ProcessPuzzle Base Rule Backend is the server-side companion of [`@processpuzzle/base-rule-frontend`](../../js-shared/base-rule-frontend/README.md). It provides the building blocks for authoring and evaluating business rules in a Spring Boot application, following the Hexagonal architecture used across the platform.

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Status

This library is currently a scaffold. Domain, use cases, and adapter packages will be added as the rule engine takes shape.

## Development

### Build

```powershell
npm exec nx build base-rule-backend
```

### Test

```powershell
npm exec nx test base-rule-backend
```

### Lint

```powershell
npm exec nx lint base-rule-backend
```

## License

This project is licensed under the Apache License 2.0.
