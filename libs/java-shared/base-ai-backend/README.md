# base-ai-backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-ai-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_ai_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_ai_backend)

Spring Boot library (Modulith module `ai`) for ProcessPuzzle's AI capabilities. First feature: object
recognition — enrollment of identifiable objects and their identification in videos, delegating the models to
a separate vision service. See [the recognition design](../../../docs/ai/boat-recognition-design.md).

## Status

Implemented: recognition profiles, media upload slots, enrollment, and the vision job life cycle (submission,
`X-Callback-Token` notification, polling fallback). Recognition sessions, jobs and tracks answer 501 for now.

## Configuration (`processpuzzle.ai`)
| Property | Default | |
|---|---|---|
| `vision-server.base-url` | — | e.g. `http://vision-server:8000/v1`; unset means photos stay PENDING |
| `vision-server.service-token` | — | the vision server's `VISION_SERVICE_TOKEN` |
| `vision-server.callback-base-url` | — | this backend as the vision server reaches it, e.g. `http://testbed-backend:8080` |
| `vision-server.stack` | `processpuzzle` | for the vision server's logs and fair queuing |
| `media.upload-expiry` | `PT30M` | |
| `poller.interval` / `poller.overdue-after` | `PT1M` / `PT10M` | |

The host application implements `MediaStore` and `SubjectDirectory` (`ai :: port`) and must let
`POST /organizations/*/vision-notifications` through its security chain; see the testbed's
`AiPortsConfiguration` and `SecurityConfig`.
