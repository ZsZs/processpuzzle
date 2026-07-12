# ProcessPuzzle :: Core

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
