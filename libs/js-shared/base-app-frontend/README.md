# @processpuzzle/base-app

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-app-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_app_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_app_frontend)
[![npm version](https://img.shields.io/npm/v/%40processpuzzle%2Fbase-app?style=flat)](https://www.npmjs.com/package/@processpuzzle/base-app)

## Introduction

`@processpuzzle/base-app` is the front-end building block of the ProcessPuzzle application shell. It provides the Angular shell (workspace layout, navigation, dockable panels, task surfaces) that hosts the other ProcessPuzzle Low-Code building blocks. The library complements the [`base-app-backend`](../../java-shared/base-app-backend/README.md) Spring Boot module that exposes application-level services such as user workspace and preferences.

## Modules: translations and assets

The Module form has two sections that are not form fields — nothing in them is saved with the module — so they are disabled controls the form never
submits. **Translations** downloads the prepared files (one per supported language), takes the translated zip or file back, and shows how far each
language is covered. **Assets** lists the files the module brings along and adds or removes them one by one.

When a module is mounted, its transloco scope is registered with an inline loader that reads the uploaded translations (draft in the Preview, released
in the live app) over whatever the scope already ships, and every route title is translated under its `translocoId` or the derived
`<scope>.route.<path>`. The mount also provides a `MODULE_ASSET_CONTEXT`: a widget property saying `module:docs/welcome.md` — `markdownSrc` of the
markdown widget, `src` of the image widgets — is looked up among the module's own files, and relative images and links inside a module's markdown file
resolve against that file's path. See [the plan](../../../docs/module-refinement-plan.md).

## Status
This library is currently a scaffold. The public API will grow as the application shell takes shape.
