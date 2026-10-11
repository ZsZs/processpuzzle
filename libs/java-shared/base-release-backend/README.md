# base-release-backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-release-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_release_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_release_backend)

## Introduction

`base-release-backend` releases an organization's definitions. A designer edits drafts; the running
application reads only what was released; one release makes every pending draft live at once, or none.
It is a Spring Modulith application module (`release`) that depends on no feature library.
Design: [definition-release.md](../../../docs/definition-release.md).

## How a release runs

`POST /organizations/{orgKey}/releases` with `{ acknowledgeWarnings }`:

1. `ReleaseOrganization` checks design rights and opens one transaction.
2. `ReleasePlanner` asks every `ReleaseParticipant` bean, in kind order, for its drafts and pending changes,
   resolves every reference of every draft against all drafts, and collects each participant's own checks.
3. Any error, unconfirmed warnings, or nothing pending: refused with 409 and the plan; nothing changes.
4. Otherwise every participant releases its kind, and the release is recorded in `org_releases` and
   `org_release_items`, with the SHA-256 of each definition's canonical JSON.

`GET .../releases/pending` is the same plan without releasing; `GET .../releases` the history.
`ReleaseOrganization.provision(orgKey, releasedBy)` releases without the design-rights check and confirms
warnings, for a provisioning call.

## Participating

A feature implements `com.processpuzzle.core.definition.ReleaseParticipant` as a bean, keeps its
definitions on two rows told apart by `Channel`, and moves an existing table onto channels with
`ChannelBackfill`. Two participants for one kind fail startup.

## Tenant erasure

`TenantDataCleaner` deletes both tables when an `OrganizationDeletedEvent` commits. The released
definitions themselves belong to the features, and their own cleaners remove them.
