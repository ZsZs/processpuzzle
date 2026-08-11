# ProcessPuzzle :: Base Document Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-document-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_document_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_document_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/base-document-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/base-document-backend)

ProcessPuzzle Base Document Backend is the server-side companion of [`@processpuzzle/base-document`](../../js-shared/base-document-frontend/README.md). It is the structural persistence for wiki-style textual documents (project plans, design docs, reports) composed of a flat, ordered list of TEXT and WIDGET blocks, in a Spring Boot application following the platform's Hexagonal architecture.

The module owns **only** document/block structure, widget component references and their static props, declared input/output ports, and opaque Tiptap JSON content. It has no knowledge of the runtime data flowing through a widget's bindings — resolving those to actual values happens entirely in the frontend runtime interpreter. It never queries `base-entity` or fetches data itself; it only records that a wiring exists.

Unrelated to `base-entity`'s `ARTIFACT` form control, which handles file and blob attachments through `processpuzzle-store`.

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Status

Under construction. Domain, use cases and the inbound adapter are in place; the REST surface is generated from `base-document-api.yaml` in `api-contracts`.

## Development

```powershell
npm exec nx build base-document-backend
npm exec nx test base-document-backend
npm exec nx lint base-document-backend
```

## License

This project is licensed under the Apache License 2.0.
