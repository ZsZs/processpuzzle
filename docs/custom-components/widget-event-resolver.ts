import type {
  AccessLevel,
  EventAction,
  ResolvedEvent,
  WidgetManifest,
  WidgetPermissions,
  WidgetRouteConfig,
} from './widget-manifest.types';

// ---------------------------------------------------------------------------
// Widget reference
// ---------------------------------------------------------------------------

export interface WidgetRef {
  id: string;
  /** Semver range, '*' when the ref has none. */
  range: string;
}

/** Parses `id@range`. The id itself may not contain '@'. */
export function parseWidgetRef(ref: string): WidgetRef {
  const at = ref.indexOf('@');
  if (at === -1) return { id: ref, range: '*' };
  const id = ref.slice(0, at);
  const range = ref.slice(at + 1);
  if (!id || !range) throw new Error(`Invalid widget ref '${ref}', expected 'id@range'`);
  return { id, range };
}

// ---------------------------------------------------------------------------
// Event resolution
// ---------------------------------------------------------------------------

const has = (obj: object, key: string): boolean => Object.prototype.hasOwnProperty.call(obj, key);

/**
 * Resolution rules:
 * 1. Route defines a binding        -> it replaces the manifest default entirely
 * 2. Route binding is null          -> event is disabled
 * 3. Route has no entry             -> manifest default applies
 * 4. Neither exists                 -> unhandled (host ignores, logs in dev mode)
 */
export function resolveEventAction(
  manifest: WidgetManifest,
  route: WidgetRouteConfig,
  eventName: string,
): ResolvedEvent {
  if (route.on && has(route.on, eventName)) {
    const binding = route.on[eventName];
    return binding === null || binding === undefined
      ? { kind: 'disabled' }
      : { kind: 'action', action: binding, source: 'route' };
  }
  const declared = manifest.events?.find((e) => e.name === eventName);
  if (declared?.default) {
    return { kind: 'action', action: declared.default, source: 'manifest' };
  }
  return { kind: 'unhandled' };
}

// ---------------------------------------------------------------------------
// Permissions
// ---------------------------------------------------------------------------

const satisfies = (granted: AccessLevel, needed: AccessLevel): boolean =>
  granted === 'write' || granted === needed;

/** True if the manifest's declared permissions cover the action. */
export function isActionPermitted(
  permissions: WidgetPermissions | undefined,
  action: EventAction,
): boolean {
  const p = permissions ?? {};
  switch (action.action) {
    case 'entity.create':
    case 'entity.update':
      return (p.entities ?? []).some(
        (e) => e.name === action.entity && satisfies(e.access, 'write'),
      );
    case 'workflow.invoke':
      return (p.workflows ?? []).some((w) => w.name === action.workflow);
    case 'router.navigate':
      return p.navigation === true;
  }
}

// ---------------------------------------------------------------------------
// Load-time validation
// ---------------------------------------------------------------------------

/**
 * Validates a route's widget usage against its manifest. Meant to run when the
 * route is loaded, so permission escalation is rejected before any event fires.
 * Returns a list of error messages, empty when valid.
 */
export function validateRouteBindings(
  manifest: WidgetManifest,
  route: WidgetRouteConfig,
): string[] {
  const errors: string[] = [];
  const declared = new Set((manifest.events ?? []).map((e) => e.name));

  for (const [eventName, binding] of Object.entries(route.on ?? {})) {
    if (!declared.has(eventName)) {
      errors.push(`Route binds unknown event '${eventName}' (not declared in manifest)`);
      continue;
    }
    if (binding && !isActionPermitted(manifest.permissions, binding)) {
      errors.push(
        `Route binding for '${eventName}' uses '${binding.action}' which is not covered by the manifest permissions`,
      );
    }
  }

  // Manifest defaults must be covered too, otherwise the manifest is inconsistent.
  for (const e of manifest.events ?? []) {
    if (e.default && !isActionPermitted(manifest.permissions, e.default)) {
      errors.push(
        `Manifest default for '${e.name}' uses '${e.default.action}' which is not covered by its own permissions`,
      );
    }
  }
  return errors;
}

// ---------------------------------------------------------------------------
// Templates: {{route.id}}, {{event.route}}
// ---------------------------------------------------------------------------

const TEMPLATE = /\{\{\s*([\w.]+)\s*\}\}/g;
const SINGLE_TEMPLATE = /^\{\{\s*([\w.]+)\s*\}\}$/;
const FORBIDDEN = new Set(['__proto__', 'constructor', 'prototype']);

function lookup(scope: Record<string, unknown>, path: string): unknown {
  let current: unknown = scope;
  for (const key of path.split('.')) {
    if (FORBIDDEN.has(key)) return undefined;
    if (current === null || typeof current !== 'object' || !has(current, key)) return undefined;
    current = (current as Record<string, unknown>)[key];
  }
  return current;
}

/**
 * Resolves templates in a value, recursively for objects and arrays.
 * A string that is exactly one template keeps the original type of the value
 * (e.g. a number stays a number); templates inside longer strings are stringified.
 * Unknown paths resolve to undefined (single template) or an empty string (embedded).
 */
export function resolveTemplates<T>(value: T, scope: Record<string, unknown>): T {
  return resolveAny(value, scope) as T;
}

function resolveAny(value: unknown, scope: Record<string, unknown>): unknown {
  if (typeof value === 'string') {
    const single = SINGLE_TEMPLATE.exec(value);
    if (single) return lookup(scope, single[1]);
    return value.replace(TEMPLATE, (_m, path: string) => {
      const found = lookup(scope, path);
      return found === undefined || found === null ? '' : String(found);
    });
  }
  if (Array.isArray(value)) return value.map((v) => resolveAny(v, scope));
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([k, v]) => [k, resolveAny(v, scope)]),
    );
  }
  return value;
}

// ---------------------------------------------------------------------------
// Dispatcher: framework-free glue the Angular host can wrap
// ---------------------------------------------------------------------------

/** Implemented by the host; executes an action against the platform. */
export interface ActionExecutor {
  execute(action: EventAction): void | Promise<void>;
}

export interface DispatchContext {
  route: Record<string, unknown>;
  [key: string]: unknown;
}

export class WidgetEventDispatcher {
  constructor(
    private readonly manifest: WidgetManifest,
    private readonly route: WidgetRouteConfig,
    private readonly executor: ActionExecutor,
    private readonly onUnhandled: (eventName: string) => void = () => undefined,
  ) {}

  /** Call from the widget's event listener (custom element, postMessage, ...). */
  async dispatch(
    eventName: string,
    payload: unknown,
    context: DispatchContext,
  ): Promise<ResolvedEvent> {
    const resolved = resolveEventAction(this.manifest, this.route, eventName);
    if (resolved.kind === 'unhandled') {
      this.onUnhandled(eventName);
      return resolved;
    }
    if (resolved.kind === 'disabled') return resolved;

    // Permissions are checked again at runtime as defence in depth.
    if (!isActionPermitted(this.manifest.permissions, resolved.action)) {
      throw new Error(
        `Action '${resolved.action.action}' for event '${eventName}' is not permitted for widget '${this.manifest.metadata.id}'`,
      );
    }
    const action = resolveTemplates(resolved.action, { ...context, event: payload });
    await this.executor.execute(action);
    return { ...resolved, action };
  }
}
