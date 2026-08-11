# ProcessPuzzle :: Core

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-processpuzzle-core.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_processpuzzle_core&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_processpuzzle_core)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/processpuzzle-core?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/processpuzzle-core)

ProcessPuzzle Core provides cross-cutting building blocks shared by every backend library and application on the platform. It is intentionally kept small and dependency-light so that any Spring Boot module can pull it in without pulling in domain-specific baggage.

## Contents

- `com.processpuzzle.core.exception` — generic REST exception handling (`ApiExceptionHandler`) for common Spring / Jackson / validation failures. Domain modules add their own `@RestControllerAdvice` for their specific exceptions.
- `com.processpuzzle.core.logging` — reserved for cross-cutting logging support (AOP logger — TBD).

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Development

### Build

```powershell
npm exec nx build processpuzzle-core
```

### Test

```powershell
npm exec nx test processpuzzle-core
```

### Lint

```powershell
npm exec nx lint processpuzzle-core
```

## License

This project is licensed under the Apache License 2.0.
