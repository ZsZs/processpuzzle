// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import {
  CONTEXT_PROPERTY,
  CustomElementWidgetController,
  validateWidgetForLoading,
  type WidgetHostContext,
  type WidgetStatus,
} from './custom-element-widget.controller';
import type { WidgetManifest, WidgetRouteConfig } from './widget-manifest.types';
import type { ScriptLoader } from './widget-script-loader';

let counter = 0;
const nextTag = (): string => `test-widget-${++counter}`;

function makeManifest(tag: string, overrides: Partial<WidgetManifest> = {}): WidgetManifest {
  return {
    apiVersion: 'processpuzzle.io/widget/v1',
    kind: 'WidgetManifest',
    metadata: { id: 'acme.race-timer', version: '1.2.0', vendor: 'acme', tier: 'reviewed', contract: '>=1.0 <2.0' },
    runtime: { type: 'custom-element', entry: 'https://cdn.example.com/main.js', tag, integrity: 'sha384-x' },
    config: { type: 'object', properties: { raceId: { type: 'string' }, showLaps: { type: 'boolean' } } },
    events: [
      { name: 'lap-recorded', default: { action: 'entity.create', entity: 'Lap' } },
      { name: 'navigate', default: { action: 'router.navigate', target: '{{event.route}}' } },
      { name: 'no-default' },
    ],
    permissions: {
      entities: [{ name: 'Lap', access: 'write' }],
      workflows: [{ name: 'record-lap', access: 'invoke' }],
      navigation: true,
    },
    context: ['locale', 'session'],
    theming: { cssVariables: ['--pp-primary', '--pp-radius'] },
    ...overrides,
  };
}

const baseRoute: WidgetRouteConfig = {
  ref: 'acme.race-timer@^1.2',
  config: { raceId: '{{route.id}}', showLaps: true },
};

const defineOnLoad = (tag: string) =>
  vi.fn(async () => {
    if (!customElements.get(tag)) customElements.define(tag, class extends HTMLElement {});
  });

interface Setup {
  controller: CustomElementWidgetController;
  container: HTMLElement;
  execute: ReturnType<typeof vi.fn>;
  loadScript: ScriptLoader;
  statuses: { status: WidgetStatus; errors: string[] }[];
  onActionError: ReturnType<typeof vi.fn>;
  onUnhandled: ReturnType<typeof vi.fn>;
  tag: string;
}

function setup(opts: { manifest?: (tag: string) => WidgetManifest; route?: WidgetRouteConfig; loader?: (tag: string) => ScriptLoader; timeoutMs?: number } = {}): Setup {
  const tag = nextTag();
  const manifest = opts.manifest ? opts.manifest(tag) : makeManifest(tag);
  const execute = vi.fn();
  const loadScript = (opts.loader ?? defineOnLoad)(tag);
  const statuses: Setup['statuses'] = [];
  const onActionError = vi.fn();
  const onUnhandled = vi.fn();
  const controller = new CustomElementWidgetController({
    manifest,
    route: opts.route ?? baseRoute,
    executor: { execute },
    loadScript,
    definitionTimeoutMs: opts.timeoutMs,
    onStatus: (status, errors) => statuses.push({ status, errors }),
    onActionError,
    onUnhandled,
  });
  return { controller, container: document.createElement('div'), execute, loadScript, statuses, onActionError, onUnhandled, tag };
}

const ctx = (overrides: Partial<WidgetHostContext> = {}): WidgetHostContext => ({ routeParams: { id: 42 }, ...overrides });
const lastStatus = (s: Setup) => s.statuses[s.statuses.length - 1];
const asRecord = (el: Element | null) => el as unknown as Record<string, unknown>;

describe('mount', () => {
  it('loads the bundle, creates the element and resolves config templates', async () => {
    const s = setup();
    await s.controller.mount(s.container, ctx());

    expect(s.loadScript).toHaveBeenCalledWith('https://cdn.example.com/main.js', 'sha384-x');
    const el = s.container.querySelector(s.tag);
    expect(el).not.toBeNull();
    expect(asRecord(el).raceId).toBe(42);
    expect(asRecord(el).showLaps).toBe(true);
    expect(s.statuses.map((x) => x.status)).toEqual(['loading', 'ready']);
    expect(lastStatus(s)).toEqual({ status: 'ready', errors: [] });
  });

  it('reports a loader failure as an error status and mounts nothing', async () => {
    const s = setup({ loader: () => vi.fn().mockRejectedValue(new Error('network down')) });
    await s.controller.mount(s.container, ctx());
    expect(lastStatus(s)).toEqual({ status: 'error', errors: ['network down'] });
    expect(s.container.children).toHaveLength(0);
  });

  it('times out when the bundle never defines the tag', async () => {
    const s = setup({ loader: () => vi.fn(async () => undefined), timeoutMs: 20 });
    await s.controller.mount(s.container, ctx());
    expect(lastStatus(s).status).toBe('error');
    expect(lastStatus(s).errors[0]).toContain('was not defined within 20 ms');
  });

  it('does not mount if destroyed while the bundle is loading', async () => {
    let release!: () => void;
    const s = setup({
      loader: (tag) =>
        vi.fn(
          () =>
            new Promise<void>((resolve) => {
              release = () => {
                customElements.define(tag, class extends HTMLElement {});
                resolve();
              };
            }),
        ),
    });
    const mounting = s.controller.mount(s.container, ctx());
    s.controller.destroy();
    release();
    await mounting;
    expect(s.container.children).toHaveLength(0);
    expect(lastStatus(s).status).toBe('idle');
  });
});

describe('pre-load validation', () => {
  const base = (tag = 'valid-tag') => makeManifest(tag);

  it('blocks loading and never calls the loader on validation errors', async () => {
    const s = setup({ manifest: (tag) => makeManifest(tag, { runtime: { type: 'custom-element', entry: 'https://x.test/a.js', tag } }) });
    await s.controller.mount(s.container, ctx());
    expect(s.loadScript).not.toHaveBeenCalled();
    expect(lastStatus(s).status).toBe('error');
    expect(lastStatus(s).errors.join()).toContain('requires runtime.integrity');
  });

  it('allows a missing integrity for the own tier', () => {
    const m = makeManifest('valid-tag', { metadata: { ...base().metadata, tier: 'own' }, runtime: { type: 'custom-element', entry: 'https://x.test/a.js', tag: 'valid-tag' } });
    expect(validateWidgetForLoading(m, baseRoute)).toEqual([]);
  });

  it('rejects invalid and reserved tag names', () => {
    for (const tag of ['nodash', 'Has-Upper', 'font-face', '']) {
      const m = makeManifest('x-y', { runtime: { type: 'custom-element', entry: 'https://x.test/a.js', tag, integrity: 'sha384-x' } });
      expect(validateWidgetForLoading(m, baseRoute).join()).toContain('not a valid custom element name');
    }
  });

  it('rejects non-https entries except http on localhost', () => {
    const withEntry = (entry: string) => makeManifest('x-y', { runtime: { type: 'custom-element', entry, tag: 'x-y', integrity: 'sha384-x' } });
    expect(validateWidgetForLoading(withEntry('http://evil.test/a.js'), baseRoute).join()).toContain('must use https');
    expect(validateWidgetForLoading(withEntry('javascript:alert(1)'), baseRoute).join()).toContain('must use https');
    expect(validateWidgetForLoading(withEntry('http://localhost:4200/a.js'), baseRoute)).toEqual([]);
  });

  it('rejects other runtime types, mismatching ids and undeclared config keys', () => {
    const iframe = makeManifest('x-y', { runtime: { type: 'iframe', entry: 'https://x.test/a.html', tag: 'x-y', integrity: 'sha384-x' } });
    expect(validateWidgetForLoading(iframe, baseRoute).join()).toContain("'iframe' is not supported");
    expect(validateWidgetForLoading(base(), { ...baseRoute, ref: 'other.widget@1' }).join()).toContain("references widget 'other.widget'");
    expect(validateWidgetForLoading(base(), { ...baseRoute, config: { innerHTML: '<b>x</b>' } }).join()).toContain("config 'innerHTML'");
  });

  it('includes permission escalation from the route bindings', () => {
    const route: WidgetRouteConfig = { ...baseRoute, on: { 'lap-recorded': { action: 'workflow.invoke', workflow: 'finish-race' } } };
    expect(validateWidgetForLoading(base(), route).join()).toContain('not covered by the manifest permissions');
  });
});

describe('events', () => {
  it('forwards a declared event to the executor with templates resolved', async () => {
    const s = setup();
    await s.controller.mount(s.container, ctx());
    s.container.querySelector(s.tag)?.dispatchEvent(new CustomEvent('navigate', { detail: { route: '/results' } }));
    await vi.waitFor(() => expect(s.execute).toHaveBeenCalledWith({ action: 'router.navigate', target: '/results' }));
  });

  it('uses the route override instead of the manifest default', async () => {
    const route: WidgetRouteConfig = { ...baseRoute, on: { 'lap-recorded': { action: 'workflow.invoke', workflow: 'record-lap' } } };
    const s = setup({ route });
    await s.controller.mount(s.container, ctx());
    s.container.querySelector(s.tag)?.dispatchEvent(new CustomEvent('lap-recorded', { detail: { boat: 'A' } }));
    await vi.waitFor(() => expect(s.execute).toHaveBeenCalledWith({ action: 'workflow.invoke', workflow: 'record-lap' }));
  });

  it('does nothing for an event disabled with null', async () => {
    const s = setup({ route: { ...baseRoute, on: { navigate: null } } });
    await s.controller.mount(s.container, ctx());
    s.container.querySelector(s.tag)?.dispatchEvent(new CustomEvent('navigate', { detail: { route: '/x' } }));
    await new Promise((r) => setTimeout(r, 10));
    expect(s.execute).not.toHaveBeenCalled();
  });

  it('reports declared events without a binding as unhandled', async () => {
    const s = setup();
    await s.controller.mount(s.container, ctx());
    s.container.querySelector(s.tag)?.dispatchEvent(new CustomEvent('no-default'));
    await vi.waitFor(() => expect(s.onUnhandled).toHaveBeenCalledWith('no-default'));
  });

  it('ignores events the manifest does not declare', async () => {
    const s = setup();
    await s.controller.mount(s.container, ctx());
    s.container.querySelector(s.tag)?.dispatchEvent(new CustomEvent('delete-everything'));
    await new Promise((r) => setTimeout(r, 10));
    expect(s.execute).not.toHaveBeenCalled();
    expect(s.onUnhandled).not.toHaveBeenCalled();
  });

  it('reports executor failures through onActionError', async () => {
    const s = setup();
    s.execute.mockRejectedValue(new Error('backend 500'));
    await s.controller.mount(s.container, ctx());
    s.container.querySelector(s.tag)?.dispatchEvent(new CustomEvent('lap-recorded', { detail: {} }));
    await vi.waitFor(() => expect(s.onActionError).toHaveBeenCalledWith('lap-recorded', expect.any(Error)));
  });
});

describe('update', () => {
  it('re-resolves config when route params change and only touches changed keys', async () => {
    const s = setup();
    await s.controller.mount(s.container, ctx());
    const el = s.container.querySelector(s.tag) as unknown as Record<string, unknown>;
    el.showLaps = 'locally-changed';

    s.controller.update(ctx({ routeParams: { id: 43 } }));
    expect(el.raceId).toBe(43);
    expect(el.showLaps).toBe('locally-changed'); // unchanged key is not overwritten
  });

  it('passes only the SDK keys the manifest declares', async () => {
    const s = setup();
    await s.controller.mount(s.container, ctx({ sdk: { locale: 'de', theme: 'dark', session: { token: 't' } } }));
    expect(asRecord(s.container.querySelector(s.tag))[CONTEXT_PROPERTY]).toEqual({ locale: 'de', session: { token: 't' } });

    s.controller.update(ctx({ sdk: { locale: 'en', session: { token: 't' } } }));
    expect((asRecord(s.container.querySelector(s.tag))[CONTEXT_PROPERTY] as { locale: string }).locale).toBe('en');
  });

  it('applies only declared theme variables and removes ones that disappear', async () => {
    const s = setup();
    await s.controller.mount(s.container, ctx({ themeVariables: { '--pp-primary': '#0a5', '--evil': 'red' } }));
    const el = s.container.querySelector(s.tag) as HTMLElement;
    expect(el.style.getPropertyValue('--pp-primary')).toBe('#0a5');
    expect(el.style.getPropertyValue('--evil')).toBe('');

    s.controller.update(ctx({ themeVariables: {} }));
    expect(el.style.getPropertyValue('--pp-primary')).toBe('');
  });

  it('uses the latest context when update arrives while the bundle is still loading', async () => {
    let release!: () => void;
    const s = setup({
      loader: (tag) =>
        vi.fn(
          () =>
            new Promise<void>((resolve) => {
              release = () => {
                customElements.define(tag, class extends HTMLElement {});
                resolve();
              };
            }),
        ),
    });
    const mounting = s.controller.mount(s.container, ctx());
    s.controller.update(ctx({ routeParams: { id: 99 } }));
    release();
    await mounting;
    expect(asRecord(s.container.querySelector(s.tag)).raceId).toBe(99);
  });
});

describe('destroy', () => {
  it('removes the element and stops forwarding events', async () => {
    const s = setup();
    await s.controller.mount(s.container, ctx());
    const el = s.container.querySelector(s.tag) as HTMLElement;
    s.controller.destroy();
    expect(s.container.children).toHaveLength(0);
    el.dispatchEvent(new CustomEvent('navigate', { detail: { route: '/x' } }));
    await new Promise((r) => setTimeout(r, 10));
    expect(s.execute).not.toHaveBeenCalled();
    expect(lastStatus(s).status).toBe('idle');
  });

  it('a second mount replaces the first element', async () => {
    const s = setup();
    await s.controller.mount(s.container, ctx());
    await s.controller.mount(s.container, ctx({ routeParams: { id: 7 } }));
    const elements = s.container.querySelectorAll(s.tag);
    expect(elements).toHaveLength(1);
    expect(asRecord(elements[0]).raceId).toBe(7);
  });
});
