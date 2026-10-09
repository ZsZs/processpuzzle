/**
 * Types for external widget manifests and route-level widget usage.
 * Plain TypeScript, no Angular dependency, so the model can be shared
 * between the frontend host, the backend validator and tooling.
 */

export type WidgetTier = 'own' | 'reviewed' | 'unreviewed';
export type WidgetRuntimeType = 'custom-element' | 'federated' | 'iframe';
export type AccessLevel = 'read' | 'write';

// ---------------------------------------------------------------------------
// Actions: what a widget event can be mapped to
// ---------------------------------------------------------------------------

export type EventAction =
  | { action: 'entity.create'; entity: string }
  | { action: 'entity.update'; entity: string }
  | { action: 'workflow.invoke'; workflow: string }
  | { action: 'router.navigate'; target: string };

export type ActionName = EventAction['action'];

// ---------------------------------------------------------------------------
// Manifest
// ---------------------------------------------------------------------------

export interface WidgetPermissions {
  entities?: { name: string; access: AccessLevel }[];
  workflows?: { name: string; access: 'invoke' }[];
  navigation?: boolean;
}

export interface WidgetEventDeclaration {
  name: string;
  /** JSON Schema of the event payload. */
  payload?: Record<string, unknown>;
  /** Action used when the route does not define a binding for this event. */
  default?: EventAction;
}

export interface WidgetRuntime {
  type: WidgetRuntimeType;
  entry: string;
  /** Subresource integrity hash. Required for 'reviewed' and 'unreviewed'. */
  integrity?: string;
  /** Custom element tag name (type 'custom-element' only). */
  tag?: string;
  /** iframe options (type 'iframe' only). */
  sandbox?: string[];
  minHeight?: number;
}

export interface WidgetManifest {
  apiVersion: 'processpuzzle.io/widget/v1';
  kind: 'WidgetManifest';
  metadata: {
    id: string;
    version: string;
    vendor: string;
    tier: WidgetTier;
    /** Semver range of the PP widget contract this widget supports. */
    contract: string;
  };
  runtime: WidgetRuntime;
  /** JSON Schema describing the widget configuration. */
  config: Record<string, unknown>;
  events?: WidgetEventDeclaration[];
  permissions?: WidgetPermissions;
  context?: ('locale' | 'theme' | 'session')[];
  theming?: { cssVariables?: string[] };
}

// ---------------------------------------------------------------------------
// Route usage
// ---------------------------------------------------------------------------

/**
 * Binding of one widget event in a route.
 * - an EventAction replaces the manifest default entirely (no merging)
 * - null disables the event (YAML: `~`)
 * An absent key means: use the manifest default.
 */
export type RouteEventBinding = EventAction | null;

export interface WidgetRouteConfig {
  /** `id@range`, e.g. `acme.race-timer@^1.2`. */
  ref: string;
  /** Config values may contain templates like `{{route.id}}`. */
  config?: Record<string, unknown>;
  on?: Record<string, RouteEventBinding>;
}

// ---------------------------------------------------------------------------
// Resolution results
// ---------------------------------------------------------------------------

export type ResolvedEvent =
  | { kind: 'action'; action: EventAction; source: 'route' | 'manifest' }
  | { kind: 'disabled' }
  | { kind: 'unhandled' };
