import type { WidgetManifest, WidgetRouteConfig } from './widget-manifest.types';
import {
  WidgetEventDispatcher,
  parseWidgetRef,
  resolveTemplates,
  validateRouteBindings,
} from './widget-event-resolver';
import type { ActionExecutor } from './widget-event-resolver';
import type { ScriptLoader } from './widget-script-loader';

/**
 * Framework-free controller that mounts one custom-element widget.
 * The Angular component is a thin wrapper around this class, so all of the
 * behaviour below can be tested without Angular.
 */

export type WidgetStatus = 'idle' | 'loading' | 'ready' | 'error';
export type SdkContextKey = 'locale' | 'theme' | 'session';

export interface WidgetHostContext {
  /** Values available to config templates as `{{route.<name>}}`. */
  routeParams: Record<string, unknown>;
  /** Values offered to the widget. Only keys the manifest lists under `context` are passed on. */
  sdk?: Partial<Record<SdkContextKey, unknown>>;
  /** CSS custom properties from the PP theme. Only names listed in `theming.cssVariables` are applied. */
  themeVariables?: Record<string, string>;
}

export interface CustomElementControllerDeps {
  manifest: WidgetManifest;
  route: WidgetRouteConfig;
  executor: ActionExecutor;
  loadScript: ScriptLoader;
  document?: Document;
  /** How long to wait for the bundle to define its tag after the script has loaded. */
  definitionTimeoutMs?: number;
  onStatus?: (status: WidgetStatus, errors: string[]) => void;
  onUnhandled?: (eventName: string) => void;
  onActionError?: (eventName: string, error: unknown) => void;
}

/** Property on the element that receives the filtered SDK context object. */
export const CONTEXT_PROPERTY = 'ppContext';

const TAG_NAME = /^[a-z][a-z0-9]*(-[a-z0-9]+)+$/;
const RESERVED_TAGS = new Set([
  'annotation-xml',
  'color-profile',
  'font-face',
  'font-face-src',
  'font-face-uri',
  'font-face-format',
  'font-face-name',
  'missing-glyph',
]);
const SKIPPED_CONFIG_KEYS = new Set(['__proto__', 'constructor', 'prototype']);
const CSS_VARIABLE = /^--[\w-]+$/;
const DEFAULT_DEFINITION_TIMEOUT_MS = 10_000;

// ---------------------------------------------------------------------------
// Load-time validation
// ---------------------------------------------------------------------------

function entryUrlErrors(entry: string, baseUri: string): string[] {
  let url: URL;
  try {
    url = new URL(entry, baseUri);
  } catch {
    return [`Runtime entry '${entry}' is not a valid URL`];
  }
  const isLocalhost = url.hostname === 'localhost' || url.hostname === '127.0.0.1';
  if (url.protocol === 'https:' || (url.protocol === 'http:' && isLocalhost)) return [];
  return [`Runtime entry '${entry}' must use https (http is only allowed for localhost)`];
}

function configKeyErrors(manifest: WidgetManifest, route: WidgetRouteConfig): string[] {
  const keys = Object.keys(route.config ?? {});
  if (keys.length === 0) return [];
  const schema = manifest.config as { properties?: Record<string, unknown>; additionalProperties?: unknown };
  if (schema.additionalProperties === true) return [];
  const allowed = new Set(Object.keys(schema.properties ?? {}));
  return keys
    .filter((k) => !allowed.has(k))
    .map((k) => `Route sets config '${k}' which the manifest schema does not declare`);
}

/**
 * Everything that can be checked before a single byte of widget code is loaded.
 * Returns error messages, empty when the widget may be mounted.
 */
export function validateWidgetForLoading(
  manifest: WidgetManifest,
  route: WidgetRouteConfig,
  baseUri = 'https://localhost/',
): string[] {
  const errors: string[] = [];
  const { runtime, metadata } = manifest;

  try {
    const ref = parseWidgetRef(route.ref);
    if (ref.id !== metadata.id) {
      errors.push(`Route references widget '${ref.id}' but the manifest is for '${metadata.id}'`);
    }
  } catch (e) {
    errors.push((e as Error).message);
  }

  if (runtime.type !== 'custom-element') {
    errors.push(`Runtime '${runtime.type}' is not supported by the custom-element loader`);
  }
  if (!runtime.tag || !TAG_NAME.test(runtime.tag) || RESERVED_TAGS.has(runtime.tag)) {
    errors.push(`Runtime tag '${runtime.tag ?? ''}' is not a valid custom element name`);
  }
  errors.push(...entryUrlErrors(runtime.entry, baseUri));
  if (metadata.tier !== 'own' && !runtime.integrity) {
    errors.push(`Tier '${metadata.tier}' requires runtime.integrity`);
  }

  errors.push(...configKeyErrors(manifest, route));
  errors.push(...validateRouteBindings(manifest, route));
  return errors;
}

// ---------------------------------------------------------------------------
// Controller
// ---------------------------------------------------------------------------

export class CustomElementWidgetController {
  private readonly doc: Document;
  private readonly dispatcher: WidgetEventDispatcher;
  private readonly listeners: { name: string; fn: (e: Event) => void }[] = [];
  private readonly appliedCssVariables = new Set<string>();
  private readonly lastConfig = new Map<string, string>();

  private element: HTMLElement | null = null;
  private context: WidgetHostContext = { routeParams: {} };
  private lastSdk: Record<string, unknown> | null = null;
  private generation = 0;

  constructor(private readonly deps: CustomElementControllerDeps) {
    this.doc = deps.document ?? document;
    this.dispatcher = new WidgetEventDispatcher(
      deps.manifest,
      deps.route,
      deps.executor,
      deps.onUnhandled,
    );
  }

  /** Validates, loads the bundle, creates the element and wires events. Never throws; see onStatus. */
  async mount(container: HTMLElement, context: WidgetHostContext): Promise<void> {
    this.teardown();
    const generation = ++this.generation;
    this.context = context;
    this.setStatus('loading');

    const { manifest, route } = this.deps;
    const errors = validateWidgetForLoading(manifest, route, this.doc.baseURI);
    if (errors.length > 0) {
      this.setStatus('error', errors);
      return;
    }

    const tag = manifest.runtime.tag as string;
    try {
      await this.deps.loadScript(manifest.runtime.entry, manifest.runtime.integrity);
      await this.whenDefined(tag);
    } catch (e) {
      if (generation === this.generation) this.setStatus('error', [(e as Error).message]);
      return;
    }
    // destroyed or re-mounted while the bundle was loading
    if (generation !== this.generation) return;

    const el = this.doc.createElement(tag);
    this.element = el;
    this.applyConfig();
    this.applySdkContext();
    this.applyTheme();
    this.attachListeners(el);
    container.appendChild(el);
    this.setStatus('ready');
  }

  /** Pushes changed route params, SDK context and theme variables to the mounted widget. */
  update(context: WidgetHostContext): void {
    this.context = context;
    if (!this.element) return; // still loading: mount() picks up the latest context
    this.applyConfig();
    this.applySdkContext();
    this.applyTheme();
  }

  destroy(): void {
    this.generation++;
    this.teardown();
    this.setStatus('idle');
  }

  // -- internals -------------------------------------------------------------

  private teardown(): void {
    const el = this.element;
    if (el) {
      for (const { name, fn } of this.listeners) el.removeEventListener(name, fn);
      el.remove();
    }
    this.listeners.length = 0;
    this.appliedCssVariables.clear();
    this.lastConfig.clear();
    this.lastSdk = null;
    this.element = null;
  }

  private setStatus(status: WidgetStatus, errors: string[] = []): void {
    this.deps.onStatus?.(status, errors);
  }

  private async whenDefined(tag: string): Promise<void> {
    const registry = this.doc.defaultView?.customElements ?? customElements;
    const ms = this.deps.definitionTimeoutMs ?? DEFAULT_DEFINITION_TIMEOUT_MS;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(
        () => reject(new Error(`Custom element '${tag}' was not defined within ${ms} ms`)),
        ms,
      );
    });
    try {
      await Promise.race([registry.whenDefined(tag), timeout]);
    } finally {
      clearTimeout(timer);
    }
  }

  private attachListeners(el: HTMLElement): void {
    for (const declared of this.deps.manifest.events ?? []) {
      const name = declared.name;
      const fn = (event: Event): void => {
        const payload = (event as CustomEvent).detail;
        this.dispatcher
          .dispatch(name, payload, { route: this.context.routeParams })
          .catch((error: unknown) => this.deps.onActionError?.(name, error));
      };
      el.addEventListener(name, fn);
      this.listeners.push({ name, fn });
    }
  }

  private applyConfig(): void {
    const el = this.element as unknown as Record<string, unknown> | null;
    if (!el) return;
    const resolved = resolveTemplates(this.deps.route.config ?? {}, { route: this.context.routeParams });
    for (const [key, value] of Object.entries(resolved)) {
      if (SKIPPED_CONFIG_KEYS.has(key)) continue;
      const serialized = String(JSON.stringify(value));
      if (this.lastConfig.get(key) === serialized) continue;
      this.lastConfig.set(key, serialized);
      el[key] = value;
    }
  }

  private applySdkContext(): void {
    const el = this.element as unknown as Record<string, unknown> | null;
    if (!el) return;
    const declared = this.deps.manifest.context ?? [];
    const filtered: Record<string, unknown> = {};
    for (const key of declared) {
      if (this.context.sdk && key in this.context.sdk) filtered[key] = this.context.sdk[key];
    }
    const previous = this.lastSdk;
    const changed =
      previous === null ||
      Object.keys(filtered).length !== Object.keys(previous).length ||
      Object.keys(filtered).some((k) => !Object.is(filtered[k], previous[k]));
    if (!changed) return;
    this.lastSdk = filtered;
    el[CONTEXT_PROPERTY] = filtered;
  }

  private applyTheme(): void {
    const el = this.element;
    if (!el) return;
    const vars = this.context.themeVariables ?? {};
    for (const name of this.deps.manifest.theming?.cssVariables ?? []) {
      if (!CSS_VARIABLE.test(name)) continue;
      const value = vars[name];
      if (value !== undefined) {
        el.style.setProperty(name, value);
        this.appliedCssVariables.add(name);
      } else if (this.appliedCssVariables.delete(name)) {
        el.style.removeProperty(name);
      }
    }
  }
}
