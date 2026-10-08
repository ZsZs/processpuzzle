import { describe, expect, it, vi } from 'vitest';
import {
  WidgetEventDispatcher,
  isActionPermitted,
  parseWidgetRef,
  resolveEventAction,
  resolveTemplates,
  validateRouteBindings,
} from './widget-event-resolver';
import type { WidgetManifest, WidgetRouteConfig } from './widget-manifest.types';

const manifest: WidgetManifest = {
  apiVersion: 'processpuzzle.io/widget/v1',
  kind: 'WidgetManifest',
  metadata: { id: 'acme.race-timer', version: '1.2.0', vendor: 'acme', tier: 'reviewed', contract: '>=1.0 <2.0' },
  runtime: { type: 'custom-element', entry: 'https://cdn.example.com/main.js', tag: 'acme-race-timer', integrity: 'sha384-x' },
  config: { type: 'object' },
  events: [
    { name: 'lap-recorded', default: { action: 'entity.create', entity: 'Lap' } },
    { name: 'navigate', default: { action: 'router.navigate', target: '{{event.route}}' } },
    { name: 'no-default' },
  ],
  permissions: {
    entities: [{ name: 'Race', access: 'read' }, { name: 'Lap', access: 'write' }],
    workflows: [{ name: 'record-lap', access: 'invoke' }],
    navigation: true,
  },
};

describe('parseWidgetRef', () => {
  it('splits id and range', () => {
    expect(parseWidgetRef('acme.race-timer@^1.2')).toEqual({ id: 'acme.race-timer', range: '^1.2' });
  });
  it('defaults the range to *', () => {
    expect(parseWidgetRef('acme.race-timer')).toEqual({ id: 'acme.race-timer', range: '*' });
  });
  it('rejects empty parts', () => {
    expect(() => parseWidgetRef('@^1')).toThrow();
    expect(() => parseWidgetRef('a@')).toThrow();
  });
});

describe('resolveEventAction', () => {
  const route = (on?: WidgetRouteConfig['on']): WidgetRouteConfig => ({ ref: 'acme.race-timer@^1.2', on });

  it('uses the manifest default when the route has no entry', () => {
    const r = resolveEventAction(manifest, route(), 'lap-recorded');
    expect(r).toEqual({ kind: 'action', action: { action: 'entity.create', entity: 'Lap' }, source: 'manifest' });
  });
  it('route binding replaces the default entirely', () => {
    const r = resolveEventAction(manifest, route({ 'lap-recorded': { action: 'workflow.invoke', workflow: 'record-lap' } }), 'lap-recorded');
    expect(r).toEqual({ kind: 'action', action: { action: 'workflow.invoke', workflow: 'record-lap' }, source: 'route' });
  });
  it('null binding disables the event', () => {
    expect(resolveEventAction(manifest, route({ navigate: null }), 'navigate')).toEqual({ kind: 'disabled' });
  });
  it('is unhandled without default and without route entry', () => {
    expect(resolveEventAction(manifest, route(), 'no-default')).toEqual({ kind: 'unhandled' });
    expect(resolveEventAction(manifest, route(), 'unknown')).toEqual({ kind: 'unhandled' });
  });
});

describe('isActionPermitted', () => {
  it('write permission covers entity.create and entity.update', () => {
    expect(isActionPermitted(manifest.permissions, { action: 'entity.create', entity: 'Lap' })).toBe(true);
    expect(isActionPermitted(manifest.permissions, { action: 'entity.update', entity: 'Lap' })).toBe(true);
  });
  it('read-only permission does not cover writes', () => {
    expect(isActionPermitted(manifest.permissions, { action: 'entity.create', entity: 'Race' })).toBe(false);
  });
  it('undeclared workflow and missing permissions are rejected', () => {
    expect(isActionPermitted(manifest.permissions, { action: 'workflow.invoke', workflow: 'finish-race' })).toBe(false);
    expect(isActionPermitted(undefined, { action: 'router.navigate', target: '/x' })).toBe(false);
  });
});

describe('validateRouteBindings', () => {
  it('accepts a valid route', () => {
    const route: WidgetRouteConfig = { ref: 'a@1', on: { 'lap-recorded': { action: 'workflow.invoke', workflow: 'record-lap' }, navigate: null } };
    expect(validateRouteBindings(manifest, route)).toEqual([]);
  });
  it('rejects permission escalation', () => {
    const route: WidgetRouteConfig = { ref: 'a@1', on: { 'lap-recorded': { action: 'workflow.invoke', workflow: 'finish-race' } } };
    const errors = validateRouteBindings(manifest, route);
    expect(errors).toHaveLength(1);
    expect(errors[0]).toContain('not covered');
  });
  it('rejects unknown events', () => {
    const errors = validateRouteBindings(manifest, { ref: 'a@1', on: { bogus: null } });
    expect(errors[0]).toContain("unknown event 'bogus'");
  });
  it('flags a manifest whose own default exceeds its permissions', () => {
    const bad: WidgetManifest = { ...manifest, permissions: { navigation: false } };
    expect(validateRouteBindings(bad, { ref: 'a@1' }).length).toBeGreaterThan(0);
  });
});

describe('resolveTemplates', () => {
  const scope = { route: { id: 42, name: 'Cup' }, event: { route: '/done' } };
  it('keeps the type for a single template', () => {
    expect(resolveTemplates({ raceId: '{{route.id}}' }, scope)).toEqual({ raceId: 42 });
  });
  it('stringifies embedded templates', () => {
    expect(resolveTemplates('/races/{{route.id}}/{{route.name}}', scope)).toBe('/races/42/Cup');
  });
  it('resolves nested objects and arrays', () => {
    expect(resolveTemplates({ a: [{ b: '{{event.route}}' }] }, scope)).toEqual({ a: [{ b: '/done' }] });
  });
  it('unknown paths give undefined or empty string', () => {
    expect(resolveTemplates('{{route.missing}}', scope)).toBeUndefined();
    expect(resolveTemplates('x{{route.missing}}y', scope)).toBe('xy');
  });
  it('does not traverse prototype keys', () => {
    expect(resolveTemplates('{{route.constructor}}', scope)).toBeUndefined();
    expect(resolveTemplates('{{__proto__.polluted}}', scope)).toBeUndefined();
  });
});

describe('WidgetEventDispatcher', () => {
  it('executes the resolved action with templates filled in', async () => {
    const execute = vi.fn();
    const d = new WidgetEventDispatcher(manifest, { ref: 'a@1' }, { execute });
    await d.dispatch('navigate', { route: '/results' }, { route: { id: 1 } });
    expect(execute).toHaveBeenCalledWith({ action: 'router.navigate', target: '/results' });
  });
  it('does nothing for disabled events', async () => {
    const execute = vi.fn();
    const d = new WidgetEventDispatcher(manifest, { ref: 'a@1', on: { navigate: null } }, { execute });
    await d.dispatch('navigate', {}, { route: {} });
    expect(execute).not.toHaveBeenCalled();
  });
  it('reports unhandled events', async () => {
    const onUnhandled = vi.fn();
    const d = new WidgetEventDispatcher(manifest, { ref: 'a@1' }, { execute: vi.fn() }, onUnhandled);
    await d.dispatch('no-default', {}, { route: {} });
    expect(onUnhandled).toHaveBeenCalledWith('no-default');
  });
  it('refuses an escalated action at runtime', async () => {
    const execute = vi.fn();
    const route: WidgetRouteConfig = { ref: 'a@1', on: { 'lap-recorded': { action: 'workflow.invoke', workflow: 'finish-race' } } };
    const d = new WidgetEventDispatcher(manifest, route, { execute });
    await expect(d.dispatch('lap-recorded', {}, { route: {} })).rejects.toThrow('not permitted');
    expect(execute).not.toHaveBeenCalled();
  });
});
