# ProcessPuzzle :: Base Process Backend

ProcessPuzzle Base Process Backend is the server-side companion of [`@processpuzzle/base-process-frontend`](../../js-shared/base-process-frontend/README.md). It provides the building blocks for defining, executing, and monitoring long-running business processes in a Spring Boot application. It builds on top of [`base-state-backend`](../base-state-backend/README.md).

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Status

This library is currently a scaffold. Domain, use cases, and adapter packages will be added as the process engine takes shape.

## Development

```powershell
npm exec nx build base-process-backend
npm exec nx test base-process-backend
npm exec nx lint base-process-backend
```

## License

This project is licensed under the Apache License 2.0.
