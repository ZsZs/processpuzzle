![ProcessPuzzle - Business Agility](https://raw.githubusercontent.com/ZsZs/processpuzzle-parent/master/processpuzzle-logo-small.jpg)
# ProcessPuzzle
## Purpose
ProcessPuzzle is a Low-Code platform for content management and business workflow based applications. For more details see [the ProcessPuzzle website](https://processpuzzle.com). 
ProcessPuzzle has a couple of Building Blocks:
- [ProcessPuzzle Framework](/libs/README.md) – Is a set of libraries for building Low-Code Angular applications
- [ProcessPuzzle Testbed](/apps/processpuzzle-testbed) – Web application to test and demonstrate the framework capabilities
- [ProcessPuzzle UI](/apps/processpuzzle-ui) – Web application to help you to define your own business application.
## Architecture

## Theming
The framework ships a small set of **named brand colors** as CSS custom properties, defined in
`libs/js-shared/widgets/src/theme/pp-colors.css`. Framework components (header, sidenav, cards, form &
card buttons) reference these tokens instead of hard-coded values, so a single stylesheet controls the
platform's look.

### Tokens
| Tier | Token | Default | Used for |
| --- | --- | --- | --- |
| Base | `--pp-color-white` | `#eeeeee` | content background, logo border, on-sidenav text |
| Base | `--pp-color-light-green` | `rgb(92, 218, 207)` | header / footer |
| Base | `--pp-color-light-blue` | `rgb(153, 217, 235)` | content cards |
| Base | `--pp-color-dark-blue` | `rgb(24, 111, 206)` | sidenav |
| Surface | `--pp-surface-header` / `-card` / `-sidenav` / `-base` | → base colors | semantic roles |
| Surface | `--pp-on-sidenav` | `--pp-color-white` | sidenav text |
| Button | `--pp-button-primary-bg` / `-text` | dark-blue / white | Save, card actions |
| Button | `--pp-button-secondary-bg` / `-text` | white / dark-blue | Cancel |
| Button | `--pp-button-delete-bg` / `-text` | light-green / dark-blue | Delete |
| Chip | `--pp-chip-bg` / `-text` | dark-blue / white | TAGS control chips |

### Consuming the theme
Add the token file to your app's `styles` array (Angular `project.json` / `angular.json`), **before** your
own global styles:
```jsonc
"styles": [
  "libs/js-shared/widgets/src/theme/pp-colors.css", // or the published package path
  "src/styles.scss"
]
```

### Overriding colors
Redefine any token in a `:root` block in your **own** global stylesheet (loaded after `pp-colors.css`);
the later declaration wins. Override a **base** color to re-tint every surface derived from it, or a
**surface / button** token to retarget just one place:
```css
/* your app's global styles */
:root {
  --pp-color-light-green: #a8e6cf;   /* recolors header + footer + secondary buttons */
  --pp-surface-sidenav: #0d1b2a;     /* dark sidenav only, base palette untouched */
  --pp-button-primary-bg: #6200ee;   /* Save / Delete / card action buttons only */
}
```
Because the tokens cascade at runtime, no rebuild of the framework libraries is required.

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
