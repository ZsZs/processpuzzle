# mock-backend

A json-server standing in for the **third-party REST sources** a ProcessPuzzle application integrates
with. It is not a backend for the platform's own features.

`db.json` therefore holds only generic REST fixtures:

| Collection                                               | Stands in for                                                           |
| -------------------------------------------------------- | ----------------------------------------------------------------------- |
| `application-properties`                                 | an arbitrary key/value service, read by the `like-button` demo widget   |
| `test-entity`, `test-entity-component`, `related-entity` | a foreign entity API the testbed's `base-entity` screens are pointed at |
| `trunk-data`                                             | a lookup/reference-data service                                         |

Everything the platform itself owns — entities and entity definitions, rules, app and module
definitions, widget definitions, state machines, workflows, documents, organizations — is served by
`apps/processpuzzle-testbed-backend` (`:8080`) from the seed YAML in each `libs/java-shared/base-*-backend`
library. The frontend reaches it through `APP_SERVICE_ROOT` and its per-feature siblings
(`RULE_SERVICE_ROOT`, `STATE_SERVICE_ROOT`, …); only `BACKEND_SERVICE_ROOT` points here.

Duplicating a platform resource here would give every such resource two answers that drift apart, so
this mock deliberately serves none. Earlier revisions generated `<orgKey>-rules`,
`<orgKey>-app-definitions`, `<orgKey>-modules` and `<orgKey>-state-machines` from the backend's own
seed files (a `db.js` generator plus an `org-scope.js` middleware that mapped
`/organizations/{orgKey}/<collection>` onto json-server's flat collection names); both are gone.

## Running

```sh
npm run run-mock-backend          # from the workspace root, watches db.json on :3000
```

The Docker image (`tools/docker/json-server/Dockerfile`) runs `npm start` from this directory, which is how
the `json-server` service in `tools/docker/docker-compose-infrastructure.yaml` serves the same `db.json`.
