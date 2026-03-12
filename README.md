![ProcessPuzzle - Business Agility](https://raw.githubusercontent.com/ZsZs/processpuzzle-parent/master/processpuzzle-logo-small.jpg)
# ProcessPuzzle
## Purpose
ProcessPuzzle is a Low-Code platform for content management and business workflow based applications. For more details see [the ProcessPuzzle website](https://processpuzzle.com). 
ProcessPuzzle has a couple of Building Blocks:
- [ProcessPuzzle Framework](/libs/README.md) – Is a set of libraries for building Low-Code Angular applications
- [ProcessPuzzle Testbed](/apps/processpuzzle-testbed) – Web application to test and demonstrate the framework capabilities
- [ProcessPuzzle UI](/apps/processpuzzle-ui) – Web application to help you to define your own business application.
## Architecture

## Technology
### Front-end application development
- **Angular 21** with TypeScript 5.9
- **Angular Material 21** for UI components
- **Angular CDK** for component behaviors
- **NgRx Signals & Component Store** for state management
- **Transloco** for internationalization
- **FontAwesome** for icons
- **Angular Auth OIDC Client** and **Keycloak** for authentication
- **AngularFire** for Firebase integration
- **ngx-markdown** with Prism.js for content rendering
### Front-end unit testing
- **Vitest 4** with jsdom/happy-dom environments
- **Angular Testing Library** for component testing
- **vitest-mock-extended** for mocking
- Coverage reporting via v8 provider
- Nx executor: `@angular/build:unit-test`
### Front-end E2E testing
- **Playwright** with Nx plugin (`@nx/playwright`)
### Build & Bundling
- **esbuild** for fast builds
- **ng-packagr** for library packaging
- **Vite** for development server and build tooling
### Code Quality
- **ESLint 10** with Angular, TypeScript, and Prettier plugins
- **Prettier** for code formatting
### Backend & Development Tools
- **Firebase Functions** for serverless backend
- **json-server** for API mocking
- **oauth2-mock-server** for OAuth testing
### Monorepo
- **Nx 22** monorepo with independent versioning
- Nx Cloud for distributed caching
- Conventional commits for changelog generation
