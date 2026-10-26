# ProcessPuzzle :: Base App Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-app-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_app_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_app_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/base-app-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/base-app-backend)

ProcessPuzzle Base App Backend is the server-side companion of [`@processpuzzle/base-app-frontend`](../../js-shared/base-app-frontend/README.md). It exposes application-level services (user workspace, preferences, panel layout) consumed by the Angular application shell.

Roles on routes and navigation items (`NavNode.roles`, `AppRoute.roles`) are declared to the organization's role
catalog when an app definition or module is created, updated or imported (`OrganizationGuard.declareRoles`
publishes `RolesReferenced`), so they exist in the identity provider and can be picked on My Profile.

## Modules: translations and assets

A `ModuleDefinition` brings its own translations and files, stored per channel (`DRAFT` / `RELEASED`) like the module itself, so a release publishes
them together with the routes that name them. See [the plan](../../../docs/module-refinement-plan.md).

- **Translations** are not edited in the app. `GET /modules/{key}/translations` prepares one `<lang>.json` per supported language in a zip — the
  module name and every route title, keyed relative to the module's transloco scope (an element without a `translocoId` gets `module.name` /
  `route.<path>`). The user translates off-line and `PUT`s the zip, or a single file, back; content problems are reported, structural ones reject the
  whole upload. The languages come from the `SupportedLanguages` port; the testbed answers it from `base-app.supported-languages`, which has to be kept
  in step with the frontend's `AVAILABLE_LANGUAGES`.
- **Assets** (markdown, images) are uploaded one file at a time under a path relative to the module. The bytes go to the `ModuleAssetStore` port — the
  testbed names objects `<orgKey>/<sha256>` in the `<prefix>-modules` bucket — and the module records path, type, size and hash, so a release copies
  records and never files. Content is streamed by `GET /modules/{key}/assets/content?path=` with an `ETag`; SVG is sandboxed and HTML is a download.
  Objects nothing names any more are removed after the transaction commits (`ModuleAssetSweeper`).

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
