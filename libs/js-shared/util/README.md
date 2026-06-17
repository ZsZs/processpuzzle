# @processpuzzle/util

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-util.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_util&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_util)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Futil?style=flat)](https://www.npmjs.com/package/@processpuzzle/util)

General-purpose utilities used across ProcessPuzzle Angular applications: small data structures, a runtime configuration loader, a central error handler, a logging provider, a layout service and a few helpers.

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

Typical bootstrap (see `apps/processpuzzle-testbed/src/main.ts`):

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

