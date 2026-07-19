# @processpuzzle/widgets
![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-widgets.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_widgets&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_widgets)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Fwidgets?style=flat)](https://www.npmjs.com/package/@processpuzzle/widgets)

This library provides a range of small widgets that can be built into an Angular application. These widgets primarily define UI elements 
but also domain classes or signal stores. Some of them are even persistent, so they need appropriate configuration to access the backend.

## Language Selector Component
The **LanguageSelector** is an Angular component designed to allow users to select their preferred language in an application. It integrates seamlessly with the Transloco library for internationalization and supports dynamic updates of the application's language.

### Features
- **Language Selection**: Allows switching between predefined languages.
- **Integration with Transloco**: Updates active language using `TranslocoService`.
- **Dynamic Label Translation**: Displays localized language names.
- **Single Selection**: Users can select only one language at a time.
- **Customizable Options**: Easily configure available languages and their labels.
### Setup and Usage
#### Installation
Ensure that you have the required dependencies for Angular Material and Transloco installed:
```
npm install @angular/material @jsverse/transloco
```
### Template Example
Add the `LanguageSelector` component to your application:
```xhtml
<pp-language-selector [languages]="languages" [selectedLanguage]="selectedLanguage"></pp-language-selector>
```
### Component Configuration
#### Input Properties

| Input | Type | Description |
| --- | --- | --- |
| `languages` | `Array` | An array of available language objects. Each object should include `code`, `label`, and `flag`. |
| `selectedLanguage` | `string` | The current active language code. This value indicates which language is selected by default. |
Example Language Configuration:

## Like Button Component
The **LikeButton** (`pp-like-button`) is an icon button that lets users "like" content and displays the current like count. The count is persisted through the `ApplicationPropertyStore` (a `likes` application property), and backend errors are surfaced via a snackbar.

### Features
- **Toggle Like**: Increments the persisted `likes` counter on each click, creating it on first use.
- **Live Count**: Displays the current like count next to the icon.
- **Persistent State**: Reads and writes through the `ApplicationPropertyStore`.
- **Error Reporting**: Shows a snackbar when the store reports an error.

### Setup and Usage
Provide the application property store (see [Application Property Store](#application-property-store)) in the component or route where the button is used.

### Template Example
```xhtml
<pp-like-button></pp-like-button>
```

## Navigate Back Button Component
The **NavigateBack** (`pp-navigate-back`) is an icon button that returns the user to the previously visited route. It relies on the `NavigateBackService`, which maintains a stack of visited routes by listening to router navigation events.

### Features
- **History Navigation**: Navigates to the previous route in the visited-route stack.
- **Router Integration**: The `NavigateBackService` tracks routes from `NavigationEnd` events.
- **No-history Signal**: `NavigateBackService.noRouteAvailable` emits when there is no previous route to go back to.

### Setup and Usage
`NavigateBackService` is provided in root, so no extra configuration is required.

### Template Example
```xhtml
<pp-navigate-back></pp-navigate-back>
```

### NavigateBackService API
| Member | Description |
| --- | --- |
| `goBack()` | Navigates to the previous route, or emits on `noRouteAvailable` if none exists. |
| `getRouteStack()` | Returns the current stack of visited routes. |
| `clearHistory()` | Clears the visited-route stack. |
| `noRouteAvailable` | `BehaviorSubject<string>` that emits when no previous route is available. |

## Share Button Component
The **ShareButton** (`pp-share-button`) is an icon button that opens a set of social share buttons in a CDK overlay. It is built on top of `ngx-sharebuttons`.

### Features
- **Overlay Share Panel**: Toggles a share panel anchored to the button using the Angular CDK overlay.
- **Backdrop Dismissal**: Clicking the backdrop closes the panel.
- **Social Buttons**: Renders share targets via `ngx-sharebuttons`.

### Setup and Usage
Ensure `ngx-sharebuttons` is installed and configured:
```
npm install ngx-sharebuttons
```

### Template Example
```xhtml
<pp-share-button></pp-share-button>
```

## Design Button Component
The **DesignButton** (`pp-design-button`) is an icon button that toggles between an application's design and home routes, switching its icon and target based on the current URL.

### Features
- **Route-aware Toggle**: Shows a `design_services` icon linking to `/design` when outside design mode, and a `home` icon linking to `/home` while on a `/design*` route.
- **Reactive Updates**: Tracks `NavigationEnd` events to keep the icon and link in sync with the active route.
- **Accessible Label**: Updates its `aria-label` to match the current action.

### Template Example
```xhtml
<pp-design-button></pp-design-button>
```

## Markdown Page Component
The **MarkdownPage** (`pp-markdown-page`) renders a Markdown document from a source URL using `ngx-markdown`, reporting load failures through a snackbar.

### Features
- **Remote Markdown Rendering**: Loads and renders Markdown from a source path.
- **Flexible Source Input**: Accepts the source via either `markdownSrcInput` or `markdownSrc`.
- **Error Feedback**: Displays a snackbar if the Markdown fails to load.

### Setup and Usage
Ensure `ngx-markdown` is installed and its provider (`provideMarkdown`) is configured in the application:
```
npm install ngx-markdown
```

### Component Configuration
#### Input Properties

| Input | Type | Description |
| --- | --- | --- |
| `markdownSrcInput` | `string` | Source path of the Markdown document. Takes precedence over `markdownSrc`. |
| `markdownSrc` | `string?` | Alternative source path, used when `markdownSrcInput` is empty. |

### Template Example
```xhtml
<pp-markdown-page markdownSrcInput="/assets/docs/help.md"></pp-markdown-page>
```

## Error Snackbar
The **ErrorSnackbar** provides a standardized error notification. `ErrorSnackbarService` implements the `ErrorMessageReporter` contract from `@processpuzzle/util`, opening the `ErrorSnackbarComponent` with a dismissible, accessible (`role="alert"`) message.

### Features
- **Central Error Reporting**: Registers as the application's `ERROR_MESSAGE_REPORTER`.
- **Consistent Presentation**: Bottom-centered snackbar with an error icon, message, and Close button.
- **Auto Dismiss**: Closes automatically after 5 seconds, or manually via Close.

### Setup and Usage
Register the provider in the application configuration:
```typescript
import { provideErrorSnackbar } from '@processpuzzle/widgets';

export const appConfig = {
  providers: [provideErrorSnackbar()],
};
```
Then report errors through the injected `ERROR_MESSAGE_REPORTER` (or `ErrorSnackbarService`):
```typescript
reporter.showErrorMessage('Something went wrong');
```

## Application Property Store
The **ApplicationProperty** domain class and its NgRx Signals store persist simple name/value properties (such as the like count) to Firestore. `provideAppPropertyStore` wires up the store, service, and mapper.

### Features
- **Persistent Properties**: Stores name/value pairs backed by Firestore via `AngularFire`.
- **Signal Store**: Exposes entities, error state, and CRUD operations (`add`, `update`, `resetErrorState`).
- **Configurable Firestore Token**: Accepts a custom `Firestore` provider token for multi-database setups.

### Setup and Usage
Provide the store where it is needed (e.g. in a route or component providers):
```typescript
import { provideAppPropertyStore } from '@processpuzzle/widgets';

providers: [provideAppPropertyStore()];
```
`provideAppPropertyStore(firestoreToken?)` optionally accepts a custom `Firestore` provider token (defaults to `Firestore`).

## Transloco Service Provider
The **provideTranslocoService** helper configures Transloco for a `@processpuzzle` application from a `LanguageConfig`, using the bundled `TranslocoHttpLoader` to load translation files over HTTP.

### Features
- **Config-driven Setup**: Derives available languages, default language, and fallback (`en`) from a `LanguageConfig`.
- **HTTP Loader**: Loads translation JSON via `TranslocoHttpLoader`.
- **Dev/Prod Aware**: Enables Transloco prod mode automatically outside development.

### Setup and Usage
```typescript
import { provideTranslocoService } from '@processpuzzle/widgets';

providers: [
  provideTranslocoService({
    DEFAULT_LANGUAGE: 'en',
    AVAILABLE_LANGUAGES: [
      { code: 'en', label: 'English', flag: '🇬🇧' },
      { code: 'de', label: 'Deutsch', flag: '🇩🇪' },
    ],
  }),
];
```

#### `LanguageConfig`

| Property | Type | Description |
| --- | --- | --- |
| `DEFAULT_LANGUAGE` | `string?` | The default active language code. |
| `AVAILABLE_LANGUAGES` | `LanguageDefinition[]?` | The selectable languages; each has `code`, `label`, and `flag`. |

## Mat Cards Grid Component
The **MatCardsGrid** (`mat-cards-grid`) renders a responsive grid of Angular Material cards from a declarative specification. Each card is described by a `CardsGridSpec` object, and all displayed texts are resolved through Transloco using a per-card translation prefix.

### Features
- **Declarative Cards**: Render any number of cards from a `CardsGridSpec[]` input.
- **Responsive Layout**: Grid layout adapts automatically to the viewport via the `LayoutService` (`handset-layout`, `tablet-layout`, `web-layout`).
- **Localized Content**: Titles, subtitles, content and captions are translated with Transloco using each card's `translocoPrefix`.
- **Optional Header Icon**: Display a Material Symbols icon in the card header.
- **Rich Content**: Show a lead paragraph followed by an optional bullet list.
- **Router Actions**: Render one or more action buttons that navigate via `routerLink`, with a configurable Material button appearance.
- **Configurable Drop-down Menu**: Add a `more_vert` overflow menu with router-linked, optionally icon-decorated menu items.

### Template Example
```xhtml
<mat-cards-grid [cards]="cards"></mat-cards-grid>
```

### Component Configuration
#### Input Properties

| Input | Type | Description |
| --- | --- | --- |
| `cards` | `CardsGridSpec[]` | The list of card specifications to render. Defaults to an empty array. |

#### `CardsGridSpec`

| Property | Type | Description |
| --- | --- | --- |
| `icon` | `string?` | Optional Material Symbols icon shown in the card header. |
| `title` | `string` | Translation key for the card title. |
| `subtitle` | `string` | Translation key for the card subtitle. |
| `content` | `string[]` | Translation keys for the content; the first entry is a paragraph, the rest render as a bullet list. |
| `actions` | `ActionSpec[]` | Action buttons rendered in the card footer. |
| `menuItems` | `MenuItemSpec[]?` | Optional items for the card's `more_vert` drop-down menu. |
| `translocoPrefix` | `string` | Transloco prefix used to resolve all translation keys of the card. |

#### `ActionSpec`

| Property | Type | Description |
| --- | --- | --- |
| `link` | `string` | Router link the button navigates to. |
| `caption` | `string` | Translation key for the button caption. |
| `colour` | `string?` | Optional colour of the button. |
| `buttonType` | `MatButtonAppearance?` | Material button appearance; defaults to `elevated`. |

#### `MenuItemSpec`

| Property | Type | Description |
| --- | --- | --- |
| `icon` | `string?` | Optional Material Symbols icon for the menu item. |
| `label` | `string` | Translation key for the menu item label. |
| `link` | `string` | Router link the menu item navigates to. |

## Version Button Component
The **VersionButton** (`pp-version-button`) displays the running application's version. It reads `BASE_CONFIGURATION.APPLICATION_VERSION` from the injected `RUNTIME_CONFIGURATION`, so the version reflects the deployed runtime configuration and requires no inputs. It is typically placed in an application footer.

### Features
- **Runtime Version Display**: Renders the current application version prefixed with `v` (e.g. `v1.4.0`).
- **Zero Configuration**: No inputs required; the value is resolved from the runtime configuration.

### Setup and Usage
Ensure a `RUNTIME_CONFIGURATION` provider exposing `BASE_CONFIGURATION.APPLICATION_VERSION` is available in the application (from `@processpuzzle/util`).

### Template Example
```xhtml
<pp-version-button></pp-version-button>
```
