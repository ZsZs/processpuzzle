# ProcessPuzzle Continuous Delivery
The foundation for the ProcessPuzzle CI/CD pipeline is [GitFlow](https://www.atlassian.com/de/git/tutorials/comparing-workflows/gitflow-workflow).
Depending on the target branch and the path of the changed files, GitHub Actions triggers a different workflow.
For a high-level overview see:

<a href="https://github.com/ZsZs/processpuzzle/blob/develop/docs/processpuzzle-cicd-pipeline.png?raw=true">
  <img src="https://github.com/ZsZs/processpuzzle/blob/develop/docs/processpuzzle-cicd-pipeline.png?raw=true" width=600px alt="CI/CD Pipeline">
</a>

## Pipeline Layout
The pipeline is **project-scoped**: every publishable library and application owns its own pair of workflows under [`.github/workflows`](workflows):

| Nx project | Build workflow | Release workflow | Path filter |
| --- | --- | --- | --- |
| `auth` | `build-auth.yml` | `release-auth.yml` | `libs/js-shared/auth/**` |
| `base-entity-frontend` | `build-base-entity-frontend.yml` | `release-base-entity-frontend.yml` | `libs/js-shared/base-entity-frontend/**` |
| `e2e-testing` | `build-e2e-testing.yml` | `release-e2e-testing.yml` | `libs/js-shared/e2e-testing/**` |
| `processpuzzle-store` | `build-processpuzzle-store.yml` | `release-processpuzzle-store.yml` | `libs/java-shared/processpuzzle-store/**` |
| `test-util` | `build-test-util.yml` | `release-test-util.yml` | `libs/js-shared/test-util/**` |
| `util` | `build-util.yml` | `release-util.yml` | `libs/js-shared/util/**` |
| `widgets` | `build-widgets.yml` | `release-widgets.yml` | `libs/js-shared/widgets/**` |
| `api-contracts` (Java) | `build-api-contracts.yml` | `release-api-contracts.yml` | `libs/java-shared/api-contracts/**` |
| `processpuzzle-testbed-frontend` | `build-processpuzzle-testbed-frontend.yml` + `build-testbed-apps.yml` | `release-processpuzzle-testbed-frontend.yml` | `apps/processpuzzle-testbed-frontend/**` |

Two scheduled / maintenance workflows live next to these: [`ng-update.yml`](workflows/ng-update.yml) runs Angular dependency updates every Mon/Wed/Fri at 05:30 UTC, and Dependabot is configured via [`dependabot.yml`](dependabot.yml).

Shared logic is factored into **composite actions** under [`.github/actions`](actions) so workflows stay thin (see [Composite Actions](#composite-actions)).

## Branches
### `feature/**`
Each push to a feature branch runs the project's `Build-*` workflow for the paths that changed.
- [Lint, Unit-Test, Sonar Scan](#lint-unit-test-sonar-scan)

For the testbed application the build also runs:
- [Docker Compose, Publish](#docker-compose-publish)
- [E2E Test](#e2e-test)

### `develop`
The `develop` branch holds the potentially shippable version. Pushes here (and PRs targeting it) run the same `Build-*` workflows as feature branches, plus — for testbed paths only — [`build-testbed-apps.yml`](workflows/build-testbed-apps.yml):
- [Lint, Unit-Test, Sonar Scan](#lint-unit-test-sonar-scan)
- [Docker Compose, Publish](#docker-compose-publish)
- [Deploy the testbed applications (STAGE)](#deploy-the-testbed-applications)
- [Integration Test](#integration-test)

### `release/<project>/*`
Short-lived release branches trigger the matching `Release-*` workflow. Once the release succeeds, the branch is deleted. The workflow:
- [Lint, Unit-Test, Sonar Scan](#lint-unit-test-sonar-scan)
- For libraries: [NPM Publish](#npm-publish) (JS libraries) or [Maven Central Publish](#maven-central-publish) (Java libraries) + [GitHub Release](#github-release)
- For the testbed app: [Smoke Test](#smoke-test) against the currently deployed PROD, then NPM publish + GitHub Release. Deploying PROD is a separate, deliberate [Deploy-Testbed-Apps](#deploy-the-testbed-applications) dispatch that promotes a verified image.

### `master`
A holder of released versions only — no workflow is bound to it.

## Triggering Rules
Workflows are gated by both **branch** and **path filter**, so unrelated changes do not start unnecessary jobs.

- **Build workflows** trigger on `push` to `feature/**` and `develop`, and on `pull_request` to `develop`, restricted to the project's `paths:` filter.
- **`build-processpuzzle-testbed-frontend.yml`** additionally has a `workflow_run:` trigger that fires when `Build-Auth`, `Build-Base-Entity-Frontend`, `Build-Util`, or `Build-Widgets` complete — so the testbed is re-validated whenever an upstream library build runs.
- **`build-testbed-apps.yml`** triggers on `push` to `develop` and `pull_request` to `develop` under either testbed app, `libs/**`, or their Dockerfiles. PRs **build only**; `develop` publishes to GHCR and then calls the deploy workflow. There is no `feature/**` trigger — `build-processpuzzle-testbed-frontend.yml` already builds both halves there.
- **`deploy-testbed-apps.yml`** has **no `push` trigger** — `workflow_call` from the build workflow, or `workflow_dispatch` for a **PROD** promotion. It never builds; `image_tag` is required.
- **Release workflows** trigger on `push` to `release/<project>/*` under the project's path filter. The testbed release runs in the **PROD** GitHub Environment.
- **`build-infrastructure.yml`** triggers on any change under `tools/docker/**` or `tools/mock-backend/**` — `push` to `feature/**` and `develop`, and `pull_request` to `develop`. Feature branches and PRs **build only**; `develop` also pushes to GHCR and then calls the deploy workflow.
- **`deploy-infrastructure.yml`** has **no `push` trigger** — it is reached by `workflow_call` from the build workflow, or by `workflow_dispatch` for a **PROD** promotion. It runs in the **STAGE** or **PROD** GitHub Environment depending on its `environment` input.
- **`ng-update.yml`** runs on cron `30 5 * * 1,3,5`.

## Composite Actions
All workflows compose the same low-level steps via reusable composite actions:

| Action | Purpose |
| --- | --- |
| [`lint-test-build`](actions/lint-test-build/action.yml) | Setup Node 24 + Java 25 (Temurin), install deps with `--legacy-peer-deps --ignore-scripts`, run `lint-<project>` and `test-<project>`, sanitize lcov paths, run SonarCloud scan, generate the environment file with the target `cicd_stage`, run `build-<project>`, and upload the coverage report as the `test-coverage-report-<sha>` artifact. Used by every `Build-*`, `Deploy-*`, and `Release-*` workflow. |
| [`e2e-test`](actions/e2e-test/action.yml) | Install Playwright browsers and run `npm run e2e-test-processpuzzle-testbed` against the requested `target_environment` (`ci`, `stage`, or `prod`). Uploads the Playwright report as the `playwright-report-<sha>` artifact. |
| [`deploy-to-aws`](actions/deploy-to-aws/action.yml) | Zips `dist/apps/processpuzzle-testbed-frontend/browser`, uploads to the `processpuzzle-testbed` S3 bucket, then creates and activates a new Elastic Beanstalk application version on `ProcessPuzzleTestbed-Dev` (eu-central-1). Currently not referenced by any workflow — kept for direct-asset EB deployments. |
| [`coolify-deploy`](actions/coolify-deploy/action.yml) | Triggers a redeploy of one Coolify resource: a single `curl` against the deploy webhook URL the resource's Webhook page prints, with the API token as a bearer header. Taking the whole URL from a secret keeps the action independent of Coolify's URL shape, and written to be reused by the per-app workflows. Used by `deploy-infrastructure.yml` and `deploy-testbed-apps.yml`. |
| [`release-java`](actions/release-java/action.yml) | Configures Node, Java 25 and a GPG key, reads the project version from the POM, signs and deploys artifacts to Maven Central via `mvn deploy -Prelease`, then creates a GitHub Release tagged `<project>@<version>`. Used by `release-api-contracts.yml`. |

## Tasks
### Lint, Unit-Test, Sonar Scan
Provided by [`lint-test-build`](actions/lint-test-build/action.yml). Each project exposes npm scripts `lint-<project>`, `test-<project>`, `config-env-<project>`, and `build-<project>` that the action invokes. Coverage is normalised by `tools/scripts/sanitize-lcov.cjs` so SonarCloud can resolve source paths from the monorepo root; the per-project `sonar-project.properties` file points `sonar.projectBaseDir` at the project directory.

### Docker Compose, Publish
For `build-processpuzzle-testbed-frontend.yml`, after the build the Spring Boot backend jar is built (`npx nx run processpuzzle-testbed-backend:build --no-cloud`) and `hoverkraft-tech/compose-action` brings up `tools/docker/docker-compose-infrastructure.yaml` plus `docker-compose-build.yaml` (the infrastructure file is pull-only, so this overlay restores its `build:` sections) and `docker-compose-apps.yaml`, with `--env-file tools/docker/env/.env.ci`, so the Playwright suite can run against a real stack. Publishing the application images is [`build-testbed-apps.yml`](workflows/build-testbed-apps.yml)’s job, and it publishes to GHCR.

The **infrastructure** images are the other half, and they are two workflows rather than one:

| Workflow | Fires on | Does |
| --- | --- | --- |
| `Build-ProcessPuzzle-Infrastructure` | any change under `tools/docker/**` / `tools/mock-backend/**` | Matrix-builds the five built infra images. On `develop` only, pushes them to `ghcr.io/zszs/processpuzzle-*` as `sha-<commit>` + `latest` using the built-in `GITHUB_TOKEN`, then calls the workflow below with that `sha-<commit>` |
| `Deploy-Infrastructure` | `workflow_call` from the above, or `workflow_dispatch` | Re-tags the given digest as `:stage` / `:prod` (`docker buildx imagetools create` — no rebuild), or builds first when dispatched without an `image_tag`, then triggers the Coolify redeploy |

Splitting them this way is what makes a promotion a re-tag: `develop` builds once, and both `stage` and later `prod` point at that same digest. See [`docs/build-deploy-strategy.md`](../docs/build-deploy-strategy.md) §10 for the per-environment secrets they need.

### E2E Test
Runs in the `feature/**` testbed build against the `ci` environment using the [`e2e-test`](actions/e2e-test/action.yml) action.

### Integration Test
Runs in `deploy-testbed-apps.yml` against the deployed environment, using the same `e2e-test` action with `target_environment` set to `stage` or `prod`. Skipped unless the environment declares `TESTBED_FRONTEND_PUBLIC_URL`.

### Smoke Test
Runs in `release-processpuzzle-testbed-frontend.yml` against the deployed PROD environment, using `e2e-test` with `target_environment: prod`.

### Deploy to AWS
[`deploy-to-aws`](actions/deploy-to-aws/action.yml) is checked in but no workflow references it — testbed deployments target Coolify. It remains available for direct-asset EB deployments. Its container-based counterpart, `deploy-to-aws-eb`, was **deleted**: its only real step templated `tools/docker/docker-compose-prod.yaml`, which no longer exists, and no workflow referenced it.

### Deploy the shared infrastructure
`deploy-infrastructure.yml` builds/pushes the infrastructure images and then calls [`coolify-deploy`](actions/coolify-deploy/action.yml) with the target environment's `COOLIFY_WEBHOOK` and `COOLIFY_TOKEN`, followed by a best-effort readiness poll of Keycloak and MinIO (skipped unless the environment declares `KEYCLOAK_PUBLIC_URL` / `MINIO_PUBLIC_URL` as variables), so that a red workflow means a red stack.

### Deploy the testbed applications
`build-testbed-apps.yml` builds the Angular bundle and the Spring Boot jar, packages each into an image and pushes `ghcr.io/zszs/processpuzzle-testbed-{frontend,backend}` tagged `sha-<commit>` (plus `latest` on `develop`), then calls `deploy-testbed-apps.yml`, which promotes those digests to `:stage` with `docker buildx imagetools create` and fires each **Application** resource's own webhook through [`coolify-deploy`](actions/coolify-deploy/action.yml). Promoting a verified build to production is the same workflow dispatched with `environment: PROD` and that `sha-<commit>`.

Both Dockerfiles only `COPY dist/…`, so **Coolify cannot build these images** — a resource pointed at either one fails on the missing `dist/`. They must be deployed from the registry, which is what this pair exists to fill.

### NPM Publish
Each JS library's `Release-*` workflow uses `nrwl/nx-set-shas` to set NX_BASE / NX_HEAD, runs `lint-test-build`, then publishes with `npx nx release publish --projects=<project> --access public --no-cloud` (with `NPM_CONFIG_PROVENANCE: true`). The testbed `Release-*` workflow does the same plus copies `package.json`/`README.md` into the dist folder and strips `environment.ts` before publishing.

### Maven Central Publish
For `api-contracts` the [`release-java`](actions/release-java/action.yml) action signs artifacts with the imported GPG key and deploys them to Maven Central using the Sonatype credentials.

### GitHub Release
Every release workflow finishes by creating a tag and release via `elgohr/Github-Release-Action`, named `@processpuzzle/<project>/<version>` with tag `<project>@<version>`.

## Required Configuration
### Secrets (repo level)
| Secret | Used for |
| --- | --- |
| `GITHUB_TOKEN` | Automatic — checkout, releases, Sonar callback |
| `SONAR_TOKEN` | SonarCloud scan |
| `FIREBASE_TOKEN` | Injected as `FIREBASE_API_KEY` into the generated env file. Still required: the testbed container’s entrypoint fails fast on an unset one, and the Firebase-Auth / Firestore adapters read it when an application chooses them |
| `NPM_TOKEN` | npm publish (`NODE_AUTH_TOKEN`) |
| `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD` | Maven Central deploy |
| `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE` | Signing Maven artifacts |

### GitHub Environments
- **STAGE** — used by `deploy-infrastructure.yml` and `deploy-testbed-apps.yml`; gates STAGE Coolify deploys.
- **PROD** — used by `deploy-testbed-apps.yml` and `release-processpuzzle-testbed-frontend.yml`; gates PROD Coolify deploys and npm publish.


### Tool versions
Node `24.x`, Java `25` (Temurin). All runners are `ubuntu-latest`, whose preinstalled
Maven (3.9.x) is used as-is — no version is pinned, because nothing in the build requires one.

## Adding a New Library or Application
1. Copy an existing pair of workflows (e.g. `build-widgets.yml` + `release-widgets.yml`) and rename them.
2. Update the workflow `name:`, the `paths:` filter, and the `base_dir` / `nx_project` inputs to `lint-test-build`.
3. For a JS library, point the release workflow at the new package: change `martinbeentjes/npm-get-version-action` `path:`, the `npx nx release publish --projects=<name>` argument, and the release title/tag.
4. For a Java library, follow `release-api-contracts.yml` and also pass it through the [`release-java`](actions/release-java/action.yml) action.
5. Ensure the project exposes the expected npm scripts: `lint-<project>`, `test-<project>`, `config-env-<project>`, `build-<project>`, plus a `sonar-project.properties` file at its root.
6. If the new project is a dependency of `processpuzzle-testbed-frontend`, add its build workflow name to the `workflow_run.workflows` list in `build-processpuzzle-testbed-frontend.yml` so the testbed re-validates on upstream changes.
