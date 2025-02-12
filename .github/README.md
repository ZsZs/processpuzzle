# ProcessPuzzle Continuous Delivery
The foundation for ProcessPuzzle CI/CD pipeline is [GitFlow](https://www.atlassian.com/de/git/tutorials/comparing-workflows/gitflow-workflow). 
Depending on the target branch various actions will be triggered.
To have an overview see:

<a href="https://github.com/ZsZs/processpuzzle/blob/develop/docs/processpuzzle-cicd-pipeline.png?raw=true">
  <img src="https://github.com/ZsZs/processpuzzle/blob/develop/docs/processpuzzle-cicd-pipeline.png?raw=true" width=600px alt="CI/CD Pipeline">
</a>

## Branches
### feature
Feature branch serves as the basis of actual development changes. After each commit and push the following tasks will be executed:
- [Lint, Unit-Test, Sonar Scan](#lint-unit-test-sonar-scan)
- [Docker Compose, Publish](#docker-compose-publish)
- [E2E Test](#e2e-test)

### develop
The develop branch serves as the potentially shippable version of the artifact. When changes are merged into this branch, they are already proved in the CI/CD pipeline.
From the other hand, this the first time, when a increment (new library/application version) is deployed to a production like environment.
- [Lint, Unit-Test, Sonar Scan](#lint-unit-test-sonar-scan)
- [Docker Compose, Publish](#docker-compose-publish)
- [Deploy to AWS](#deploy-to-aws)
- [Integration Test](#integration-test)
### release
The release branch serves as a means to build, publish and deploy a release version of the library/application.
It's a short lived branch, it is open only for the time of releasing. If successfull, then the branch will be deleted.
- [Lint, Unit-Test, Sonar Scan](#lint-unit-test-sonar-scan)
- [Docker Compose, Publish](#docker-compose-publish)
- [NPM Publish](#npm-publish)
- [Deploy to AWS](#deploy-to-aws)
- [Smoke Test](#smoke-test)
### master
The master branch serves only a holder of the release application/library versions. It doesn't trigger any action.

## Tasks
### Lint, Unit-Test, Sonar Scan
### Docker Compose, Publish
### E2E Test
### Deploy to AWS
### Integration Test
### NPM Publish
### Smoke Test
