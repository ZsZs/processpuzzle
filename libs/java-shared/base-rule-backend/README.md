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

The library provides persisted rule CRUD, YAML import/export and tenant-scoped GraalJS evaluation.
Resolution of inherited/overridden rule expressions remains unimplemented.

## Executable rule revisions

`RuleEngine` caches immutable GraalJS sources by `(orgKey, ruleId)` and compiled functions per
thread, because GraalJS values belong to their thread's context. Each compiled entry retains its
source identity: an edited expression replaces the entry on the next evaluation on **every**
thread, including after disable/re-enable or delete/re-create. A failed compilation is an evaluation
error, never a fallback to the preceding expression.

An evaluation uses the source snapshot read at its start. An in-flight evaluation may finish on
that snapshot while a rule is updated or removed; subsequent calls observe the new registration.
This cache policy alone does not define the transaction boundary for publishing registrations.

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
