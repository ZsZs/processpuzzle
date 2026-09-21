# @processpuzzle/util

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-util.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_util&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_util)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Futil?style=flat)](https://www.npmjs.com/package/@processpuzzle/util)

General-purpose utilities used across ProcessPuzzle Angular applications: small data structures, a runtime configuration loader, a central error handler, a logging provider, a layout service, locale-aware routing and a few helpers.

## Installation

```bash
npm install @processpuzzle/util
```

## Public API overview

| Symbol | Kind | Purpose |
| --- | --- | --- |
| `wildcardTextMatcher` | function | Match a string against a wildcard pattern. |
| `getEnvironment` | function | Map the current origin to an environment key. |
| `Stack<T>` | class | LIFO stack data structure. |
| `SubstringPipe` | Angular pipe | `substring` pipe for templates. |
| `LayoutService` | Angular service | Reactive breakpoint/sidenav state. |
| `ConfigurationService` | Angular service | Loads and merges JSON config files at startup. |
| `RUNTIME_CONFIGURATION` | injection token | Provides the merged runtime configuration. |
| `CONFIGURATION_OPTIONS`, `CONFIGURATION_TYPE`, `CONFIGURATION_APP_INITIALIZER` | injection tokens | Configuration hooks. |
| `BaseConfiguration`, `FirebaseConfig` | types | Shared configuration shapes. |
| `provideLocaleRouting` | provider | Puts the active language in the URL (`/hu/base-entity`). |
| `LocaleUrlSerializer` | Angular service | The `UrlSerializer` behind it; parses and emits the locale prefix. |
| `splitLocaleFromUrl`, `withLocale` / `LocalizedUrl` | functions / type | Take the locale prefix off a URL and put it back. |
| `provideLoggingService` / `LoggingConfiguration` | provider | Configures `ngx-logging-kit`. |
| `provideCentralErrorHandler`, `CentralErrorHandler` | provider / class | Global Angular `ErrorHandler`. |
| `centralHttpErrorInterceptor` | HTTP interceptor | Forwards HTTP errors to the central handler. |
| `ERROR_MESSAGE_REPORTER` / `ErrorMessageReporter` | injection token / interface | Optional UI reporter for the central handler. |

## `wildcardTextMatcher(str, rule)`

Returns `true` when `str` matches `rule`. `*` in the rule matches any sequence of characters.

```typescript
wildcardTextMatcher('Hello World', 'Hello*'); // true
wildcardTextMatcher('Hello World', '*World'); // true
wildcardTextMatcher('Hello World', 'Hello'); // true (rule is implicitly suffixed with .*)
```

## `getEnvironment(originUrl?)`

Returns the environment key that corresponds to the application's origin. Falls back to `location.origin` when no argument is given.

| Origin | Returns |
| --- | --- |
| `http://localhost:8080*` | `'docker'` |
| `http://localhost*` | `'local'` |
| `http://*.elasticbeanstalk.com` | `'aws'` |
| anything else | throws `Error` |

## `Stack<T>`

```typescript
const stack = new Stack<number>();
stack.push(10);
stack.push(20);
stack.peek(); // 20
stack.pop();  // 20
stack.size(); // 1
stack.isEmpty(); // false
stack.toArray(); // [10]
stack.clear();
```

The constructor optionally accepts an initial array: `new Stack([1, 2, 3])`.

## `SubstringPipe`

Standalone Angular pipe registered under the name `substring`. The arguments are forwarded to `String.prototype.substring`.

```html
<div>{{ item.title | substring: 0 : 10 }}</div>
```

## `LayoutService`

Tracks the current `BreakpointObserver` state as signals.

```typescript
private readonly layout = inject(LayoutService);

readonly isCompact = computed(() => this.layout.isSmallDevice());
readonly sidenavMode = this.layout.sidenavMode; // SidenavStatus.EXPAND | CLOSE | SHRINK
```

Exposed signals: `layoutClass`, `isSmallDevice`, `isMediumDevice`, `isLargeDevice`, `sidenavMode`.

## Runtime configuration

`ConfigurationService<TEnv, TConfig>` loads one or more JSON files at bootstrap, merges them in order (later files override earlier ones) and returns the result. The list of URLs is derived from the environment:

```
run-time-conf/config.common.json
run-time-conf/config.${PIPELINE_STAGE}.json
...CONFIGURATION_OVERRIDES (optional)
```

Typical bootstrap (see `apps/processpuzzle-testbed-frontend/src/main.ts`):

```typescript
async function bootstrap() {
  const env = environment as EnvironmentVariables;
  const service = new ConfigurationService<EnvironmentVariables, RuntimeConfiguration>();
  const runtimeConfig = await service.init(env);

  await bootstrapApplication(AppComponent, createAppConfig(runtimeConfig));
}
```

The loaded value is then exposed to the rest of the app through the `RUNTIME_CONFIGURATION` token:

```typescript
{ provide: RUNTIME_CONFIGURATION, useValue: runtimeConfig }
```

`BaseConfiguration` describes the minimum shape every application's configuration must satisfy (pipeline stage, backend provider, backend / object-store URLs and Firebase config). Extend it in your own `RuntimeConfiguration` type.

The other tokens — `CONFIGURATION_OPTIONS`, `CONFIGURATION_TYPE`, `CONFIGURATION_APP_INITIALIZER` — are reserved for downstream libraries that wire configuration into their own bootstrap hooks.

## Language in the URL

`provideLocaleRouting()` makes the active language part of every URL — `/hu/base-entity` rather than `/base-entity` — so a page has one address per language and a shared link reproduces the sender's.

It exists to complete a country-domain redirect. The edge sends `*.processpuzzle.hu/foo` to `*.processpuzzle.com/hu/foo` (see `tools/docker/processpuzzle-testbed-frontend/nginx.conf`); without this provider the prefix would fall into the index.html rewrite and the application would come up in its default language — a `.com` URL that says `hu` and renders English.

```typescript
providers: [
  provideRouter(appRoutes, withComponentInputBinding()),
  provideTranslocoService(runtimeConfiguration.LANGUAGE_CONFIGURATION),
  provideLocaleRouting(), // after provideRouter — it overrides UrlSerializer
];
```

**Order is load-bearing.** `provideRouter` binds `UrlSerializer` to `DefaultUrlSerializer`; the later provider wins, so moving this line above it turns the feature off without any error.

### What it does

| Direction | Mechanism |
| --- | --- |
| URL → language | `LocaleUrlSerializer.parse` strips a leading segment matching one of transloco's `availableLangs` and makes it the active language, at the *start* of the navigation so the incoming route's first render is already translated. |
| language → URL | An app initializer watches `langChanges$` and rewrites the address bar with `Location.replaceState` — a language switch is not a navigation, so it adds no history entry. |

Nothing above the serializer sees the prefix. Route configs, `routerLink`s and `router.navigate` calls keep using prefix-free paths, which is what lets base-app's run-time route builder work unchanged — there is no single route array a `:lang` parameter could have wrapped.

### Consequences worth knowing

- **The default language is prefixed too** (`/en/home`). Leaving it bare would make a page's canonical URL depend on which language it is in.
- **Old, unprefixed URLs still resolve.** `parse` leaves a URL naming no language alone; it simply gains a prefix the next time the router writes the address bar.
- **A top-level route must not be named after a language code.** `splitLocaleFromUrl` matches on membership in `availableLangs`, so a literal `/de` route would be swallowed. The platform's own top-level paths are words (`base-entity`, `design`, `ci-cd`), so this is avoidable rather than merely unlikely.
- **OIDC redirect URIs gain the prefix**, because `KeycloakAuthService` builds them from `document.baseURI` plus the current route. The testbed realm registers `http://localhost:4200/*` and friends, whose `/*` already covers it; a client registered with exact paths would need updating.

`splitLocaleFromUrl` and `withLocale` are exported for callers that need the same string surgery outside the router.

## Logging

`provideLoggingService` wires `ngx-logging-kit` based on a `LoggingConfiguration`:

```typescript
export interface LoggingConfiguration {
  level: 'none' | 'trace' | 'debug' | 'info' | 'log' | 'warn' | 'error' | 'fatal';
  serverLogLevel: 'none' | 'trace' | 'debug' | 'info' | 'log' | 'warn' | 'error' | 'fatal';
  serverLoggingUrl?: string;
}
```

```typescript
providers: [
  provideLoggingService(runtimeConfig.LOGGING_CONFIGURATION),
]
```

When `serverLogLevel` is `'none'`, no server URL is registered even if one is provided.

## Central error handling

`provideCentralErrorHandler()` registers `CentralErrorHandler` as Angular's global `ErrorHandler`. It:

- unwraps `rejection` / `ngOriginalError` / `originalError`,
- reloads the page on chunk-load errors,
- logs `HttpErrorResponse` with status, statusText and URL,
- logs everything else as `fatal`,
- optionally forwards a display message to an `ErrorMessageReporter`.

```typescript
providers: [
  provideHttpClient(withInterceptors([centralHttpErrorInterceptor])),
  provideCentralErrorHandler(),
  { provide: ERROR_MESSAGE_REPORTER, useExisting: MyToastReporterService }, // optional
]
```

Implement `ErrorMessageReporter.showErrorMessage(message, error)` to surface errors in the UI (snackbar, toast, dialog, etc.).

`centralHttpErrorInterceptor` simply pipes any HTTP error through the registered `ErrorHandler` and rethrows, so failures still propagate to caller `subscribe`/`catchError` blocks.
