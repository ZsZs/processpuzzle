# ProcessPuzzle Testbed
![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-testbed.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_testbed&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_testbed&branch=develop)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Ftestbed?style=flat)](https://www.npmjs.com/package/@processpuzzle/testbed)

## Introduction
ProcessPuzzle Testbed is the reference Angular application of the [ProcessPuzzle](https://processpuzzle.com) platform. It serves two purposes: it is a **living demonstration** of what can be built with the `@processpuzzle/*` libraries, and it is the **integration test harness** that exercises those libraries end-to-end in a realistic application context.

Each feature module of the testbed focuses on a single library — showing typical usage patterns, configuration options and the resulting UI — so that developers evaluating or adopting the framework can see the libraries at work before pulling them into their own projects. Browse the [live demo app](https://testbed.processpuzzle.de/home) to try it out, or explore the [source code on GitHub](https://github.com/ZsZs/processpuzzle).

## Demonstrated libraries
The testbed exercises the following libraries from the [ProcessPuzzle Framework](https://github.com/ZsZs/processpuzzle/tree/develop/libs):
- [@processpuzzle/util](https://github.com/ZsZs/processpuzzle/tree/develop/libs/js-shared/util) — general-purpose helpers, runtime config loader, error handler, logging and layout services.
- [@processpuzzle/base-entity](https://github.com/ZsZs/processpuzzle/tree/develop/libs/js-shared/base-entity) — Angular Material table and reactive form generator driven by entity definitions.
- [@processpuzzle/widgets](https://github.com/ZsZs/processpuzzle/tree/develop/libs/js-shared/widgets) — reusable UI widgets for application development.
- [@processpuzzle/auth](https://github.com/ZsZs/processpuzzle/tree/develop/libs/js-shared/auth) — authentication and authorization (OIDC / Keycloak).
- [@processpuzzle/test-util](https://github.com/ZsZs/processpuzzle/tree/develop/libs/js-shared/test-util) — helper utilities for unit testing.
- [@processpuzzle/e2e-testing](https://github.com/ZsZs/processpuzzle/tree/develop/libs/js-shared/e2e-testing) — building blocks for Playwright-based end-to-end tests.
