import { describe, expect, it } from 'vitest';
import { RouteDefinition } from './app-definition';
import { ModuleDefinition, moduleTranslocoScope } from './module-definition';

describe('ModuleDefinition', () => {
  it('starts out with the two fields the contract requires and nothing else', () => {
    const module = new ModuleDefinition();

    expect(module.id).toBe('');
    expect(module.name).toBe('');
    expect(module.translocoId).toBeUndefined();
    expect(module.description).toBeUndefined();
    expect(module.translocoScope).toBeUndefined();
    expect(module.routes).toBeUndefined();
  });

  it('keeps the server-assigned fields the designer only reads', () => {
    const module = new ModuleDefinition({ id: 'claims', name: 'Claims', orgKey: 'acme', version: 4, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-02-01T00:00:00Z' });

    expect(module.orgKey).toBe('acme');
    expect(module.version).toBe(4);
    expect(module.createdAt).toBe('2026-01-01T00:00:00Z');
    expect(module.updatedAt).toBe('2026-02-01T00:00:00Z');
  });

  it('carries flat routes, composition stopping at the mount', () => {
    const module = new ModuleDefinition({ id: 'claims', name: 'Claims', routes: [new RouteDefinition({ path: 'open', title: 'Open claims' })] });

    expect(module.routes?.map((route) => route.path)).toEqual(['open']);
    expect(module).not.toHaveProperty('modules');
  });
});

describe('moduleTranslocoScope', () => {
  it('is the authored scope when the designer named one', () => {
    expect(moduleTranslocoScope(new ModuleDefinition({ id: 'claims', translocoScope: 'claims_module' }))).toBe('claims_module');
  });

  // The contract's default, applied here rather than written into the row, so the form field stays empty.
  it('falls back to the key when no scope is authored', () => {
    expect(moduleTranslocoScope(new ModuleDefinition({ id: 'claims' }))).toBe('claims');
    expect(moduleTranslocoScope(new ModuleDefinition({ id: 'claims', translocoScope: '   ' }))).toBe('claims');
  });
});
