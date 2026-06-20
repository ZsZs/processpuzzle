# ProcessPuzzle :: Base Rule Backend

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
