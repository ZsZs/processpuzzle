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
| `base-entity` | `build-base-entity.yml` | `release-base-entity.yml` | `libs/js-shared/base-entity/**` |
| `e2e-testing` | `build-e2e-testing.yml` | `release-e2e-testing.yml` | `libs/js-shared/e2e-testing/**` |
| `object-store` | `build-object-store.yml` | `release-object-store.yml` | `libs/js-shared/object-store/**` |
| `test-util` | `build-test-util.yml` | `release-test-util.yml` | `libs/js-shared/test-util/**` |
| `util` | `build-util.yml` | `release-util.yml` | `libs/js-shared/util/**` |
| `widgets` | `build-widgets.yml` | `release-widgets.yml` | `libs/js-shared/widgets/**` |
| `api-contracts` (Java) | `build-api-contracts.yml` | `release-api-contracts.yml` | `libs/java-shared/api-contracts/**` |
| `processpuzzle-testbed` | `build-testbed.yml` + `deploy-testbed.yml` | `release-testbed.yml` | `apps/processpuzzle-testbed/**` |

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
The `develop` branch holds the potentially shippable version. Pushes here (and PRs targeting it) run the same `Build-*` workflows as feature branches, plus — for testbed paths only — [`deploy-testbed.yml`](workflows/deploy-testbed.yml):
- [Lint, Unit-Test, Sonar Scan](#lint-unit-test-sonar-scan)
- [Docker Compose, Publish](#docker-compose-publish)
- [Deploy to Firebase (STAGE)](#deploy-to-firebase)
- [Integration Test](#integration-test)

### `release/<project>/*`
Short-lived release branches trigger the matching `Release-*` workflow. Once the release succeeds, the branch is deleted. The workflow:
- [Lint, Unit-Test, Sonar Scan](#lint-unit-test-sonar-scan)
- For libraries: [NPM Publish](#npm-publish) (JS libraries) or [Maven Central Publish](#maven-central-publish) (Java libraries) + [GitHub Release](#github-release)
- For the testbed app: [Docker Compose, Publish](#docker-compose-publish), [Deploy to Firebase (PROD)](#deploy-to-firebase) and [Smoke Test](#smoke-test), then NPM publish + GitHub Release

### `master`
A holder of released versions only — no workflow is bound to it.

## Triggering Rules
Workflows are gated by both **branch** and **path filter**, so unrelated changes do not start unnecessary jobs.

- **Build workflows** trigger on `push` to `feature/**` and `develop`, and on `pull_request` to `develop`, restricted to the project's `paths:` filter.
- **`build-testbed.yml`** additionally has a `workflow_run:` trigger that fires when `Build-Auth`, `Build-Base-Entity`, `Build-Util`, or `Build-Widgets` complete — so the testbed is re-validated whenever an upstream library build runs.
- **`deploy-testbed.yml`** triggers only on `push` to `develop` under `apps/processpuzzle-testbed/**` and runs in the **STAGE** GitHub Environment.
- **Release workflows** trigger on `push` to `release/<project>/*` under the project's path filter. The testbed release runs in the **PROD** GitHub Environment.
- **`ng-update.yml`** runs on cron `30 5 * * 1,3,5`.

## Composite Actions
All workflows compose the same low-level steps via reusable composite actions:

| Action | Purpose |
| --- | --- |
| [`lint-test-build`](actions/lint-test-build/action.yml) | Setup Node 24 + Java 25 (Temurin) + Maven 3.9.14, install deps with `--legacy-peer-deps --ignore-scripts`, run `lint-<project>` and `test-<project>`, sanitize lcov paths, run SonarCloud scan, generate the environment file with the target `cicd_stage`, run `build-<project>`, and upload the coverage report as the `test-coverage-report-<sha>` artifact. Used by every `Build-*`, `Deploy-*`, and `Release-*` workflow. |
| [`build-image`](actions/build-image/action.yml) | Log in to DockerHub and build three images via `docker/build-push-action` — `zsuffazs/json-server`, `zsuffazs/<app>-firebase`, and `zsuffazs/processpuzzle-<app>` — tagging with both the supplied `app_version` (defaults to `github.sha`) and `latest`. |
| [`e2e-test`](actions/e2e-test/action.yml) | Install Playwright browsers and run `npm run e2e-test-processpuzzle-testbed` against the requested `target_environment` (`ci`, `stage`, or `prod`). Uploads the Playwright report as the `playwright-report-<sha>` artifact. |
| [`deploy-to-firebase`](actions/deploy-to-firebase/action.yml) | Deploys to Firebase Hosting using `FirebaseExtended/action-hosting-deploy`. Project ID and channel are inputs (e.g. `processpuzzle-testbed-stage` / `live`). |
| [`deploy-to-aws`](actions/deploy-to-aws/action.yml) | Zips `dist/apps/processpuzzle-testbed/browser`, uploads to the `processpuzzle-testbed` S3 bucket, then creates and activates a new Elastic Beanstalk application version on `ProcessPuzzleTestbed-Dev` (eu-central-1). Currently not referenced by any workflow — kept for direct-asset EB deployments. |
| [`deploy-to-aws-eb`](actions/deploy-to-aws-eb/action.yml) | Container-based EB deployment: substitutes AWS account/region/sha/project into `tools/docker/docker-compose-prod.yaml`, zips it, uploads to S3, and uses `einaregilsson/beanstalk-deploy` to roll it out (waits for completion). Currently not referenced by any workflow — kept for future Docker-on-EB releases. |
| [`release-java`](actions/release-java/action.yml) | Configures Node, Java 25, Maven 3.9.14 and a GPG key, reads the project version from the POM, signs and deploys artifacts to Maven Central via `mvn deploy -Prelease`, then creates a GitHub Release tagged `<project>@<version>`. Used by `release-api-contracts.yml`. |

## Tasks
### Lint, Unit-Test, Sonar Scan
Provided by [`lint-test-build`](actions/lint-test-build/action.yml). Each project exposes npm scripts `lint-<project>`, `test-<project>`, `config-env-<project>`, and `build-<project>` that the action invokes. Coverage is normalised by `tools/scripts/sanitize-lcov.cjs` so SonarCloud can resolve source paths from the monorepo root; the per-project `sonar-project.properties` file points `sonar.projectBaseDir` at the project directory.

### Docker Compose, Publish
For `build-testbed.yml`, after the build the Spring Boot backend jar is built (`npx nx run processpuzzle-backend:build --no-cloud`) and `hoverkraft-tech/compose-action` brings up `tools/docker/docker-compose-ci.yaml` so the Playwright suite can run against a real stack. For `deploy-testbed.yml` and `release-testbed.yml` the [`build-image`](actions/build-image/action.yml) action publishes the three application images to DockerHub.

### E2E Test
Runs in the `feature/**` testbed build against the `ci` environment using the [`e2e-test`](actions/e2e-test/action.yml) action.

### Integration Test
Runs in `deploy-testbed.yml` against the deployed STAGE environment, using the same `e2e-test` action with `target_environment: stage`.

### Smoke Test
Runs in `release-testbed.yml` against the deployed PROD environment, using `e2e-test` with `target_environment: prod`.

### Deploy to Firebase
Both `deploy-testbed.yml` (STAGE → Firebase project `processpuzzle-testbed-stage`) and `release-testbed.yml` (PROD → Firebase project `processpuzzle-testbed`) deploy to the `live` channel via [`deploy-to-firebase`](actions/deploy-to-firebase/action.yml).

### Deploy to AWS
Both AWS composite actions ([`deploy-to-aws`](actions/deploy-to-aws/action.yml) and [`deploy-to-aws-eb`](actions/deploy-to-aws-eb/action.yml)) are checked in but no workflow currently references them — testbed deployments target Firebase Hosting. They remain available for re-enabling EB-based deployment.

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
| `FIREBASE_TOKEN` | Injected as `FIREBASE_API_KEY` into the generated env file |
| `FIREBASE_SERVICE_ACCOUNT` | Firebase Hosting deployment |
| `NPM_TOKEN` | npm publish (`NODE_AUTH_TOKEN`) |
| `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` | Pushing images to DockerHub |
| `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD` | Maven Central deploy |
| `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE` | Signing Maven artifacts |

### GitHub Environments
- **STAGE** — used by `deploy-testbed.yml`; gates STAGE Firebase deploys.
- **PROD** — used by `release-testbed.yml`; gates PROD Firebase deploys and npm publish.

### Tool versions
Node `24.x`, Java `25` (Temurin), Maven `3.9.14`. All runners are `ubuntu-latest`.

## Adding a New Library or Application
1. Copy an existing pair of workflows (e.g. `build-widgets.yml` + `release-widgets.yml`) and rename them.
2. Update the workflow `name:`, the `paths:` filter, and the `base_dir` / `nx_project` inputs to `lint-test-build`.
3. For a JS library, point the release workflow at the new package: change `martinbeentjes/npm-get-version-action` `path:`, the `npx nx release publish --projects=<name>` argument, and the release title/tag.
4. For a Java library, follow `release-api-contracts.yml` and also pass it through the [`release-java`](actions/release-java/action.yml) action.
5. Ensure the project exposes the expected npm scripts: `lint-<project>`, `test-<project>`, `config-env-<project>`, `build-<project>`, plus a `sonar-project.properties` file at its root.
6. If the new project is a dependency of `processpuzzle-testbed`, add its build workflow name to the `workflow_run.workflows` list in `build-testbed.yml` so the testbed re-validates on upstream changes.
