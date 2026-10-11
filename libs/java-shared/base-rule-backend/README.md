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

## Transactions and evaluation

CRUD and YAML import/removal publish engine registrations **after the surrounding transaction
commits**. Rollback, including a failed commit-time flush, leaves the previous registration intact.
`RuleEngineSync` captures immutable key/expression/enabled values when scheduling its callback,
not the mutable managed entity. Callbacks in one transaction run in registration order, so a
replacement import removes the old entry before registering the recreated rule. Outside a
transaction, the synchronization helper applies changes immediately.

`EvaluateObject` executes the exact expression read with each rule's database row, not whichever
revision currently happens to be registered. Evaluation compiles/caches that snapshot per thread
without publishing a shared registration. This supports cold starts, concurrent commits and a
caller's own pending edits without leaking those edits into another transaction's verdict. An
older transaction can still see an older database snapshot according to its isolation level; its
expression and violation metadata stay aligned.

After-commit callbacks are in-process, not durable or distributed. A crash or delayed callback can
leave the local registry stale, but API evaluation remains driven by the database snapshot rather
than depending on callback delivery. No cross-replica cache invalidation protocol is claimed.

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
