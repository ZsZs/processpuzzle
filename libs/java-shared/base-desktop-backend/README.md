# ProcessPuzzle :: Base Desktop Backend

ProcessPuzzle Base Desktop Backend is the server-side companion of [`@processpuzzle/base-desktop-frontend`](../../js-shared/base-desktop-frontend/README.md). It exposes desktop-level services (user workspace, preferences, panel layout) consumed by the Angular desktop shell.

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Status

This library is currently a scaffold. Domain, use cases, and adapter packages will be added as the desktop backend takes shape.

## Development

```powershell
npm exec nx build base-desktop-backend
npm exec nx test base-desktop-backend
npm exec nx lint base-desktop-backend
```

## License

This project is licensed under the Apache License 2.0.
