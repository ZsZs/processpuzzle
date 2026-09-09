# @processpuzzle/base-document

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-document-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_document_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_document_frontend)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Fbase-document?style=flat)](https://www.npmjs.com/package/@processpuzzle/base-document)

## Introduction

`@processpuzzle/base-document` is the front-end half of ProcessPuzzle's `base-document` feature: wiki-style textual documents (project plans, design docs, reports) composed of a flat, ordered list of blocks. A block is either a TEXT block — static or Tiptap-editable rich text — or a WIDGET block, a reference into the same widget registry `base-app` resolves against. A document also declares input and output ports, which record that a wiring exists without this library resolving the data that flows through it.

The library provides the descriptors, mapper, service, store and container component that let the generic `base-entity` machinery render a document and its ports, plus the Tiptap-based editor for block content. It complements the [`base-document-backend`](../../java-shared/base-document-backend/README.md) Spring Boot module, which owns the persisted document/block structure.

Unrelated to `base-entity`'s `FormControlType.ARTIFACT` control, which handles file and blob attachments through the object store.

## Status

This library is under construction. The public API will grow as document management takes shape; nothing in the workspace consumes it yet.
