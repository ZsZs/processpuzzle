import { describe, expect, it } from 'vitest';
import { AppDefinition, AppDefinitionStatus } from './app-definition';

describe('AppDefinition', () => {
  it('is constructible without arguments, as the entity store requires', () => {
    const definition = new AppDefinition();

    expect(definition.id).toBe('');
    expect(definition.name).toBe('');
  });

  it('applies the contract defaults of the layout flags', () => {
    const definition = new AppDefinition();

    expect(definition.sidenavCollapsible).toBe(true);
    expect(definition.sidenavOpenByDefault).toBe(true);
  });

  it('keeps false layout flags instead of falling back to the defaults', () => {
    const definition = new AppDefinition({ sidenavCollapsible: false, sidenavOpenByDefault: false });

    expect(definition.sidenavCollapsible).toBe(false);
    expect(definition.sidenavOpenByDefault).toBe(false);
  });

  it('exposes every property it was created with', () => {
    const definition = new AppDefinition({
      id: 'claims-app',
      name: 'Claims Management',
      translocoId: 'claims.app.name',
      description: 'Claims handling',
      materialTheme: 'rose-red',
      colorScheme: 'dark',
      preset: 'top-nav',
      sidenavMode: 'over',
      contentMaxWidth: '1280px',
      regions: [{ type: 'content' }],
      pages: [{ id: 'claims', title: 'Claims', widgets: [] }],
      orgKey: 'acme',
      status: AppDefinitionStatus.PUBLISHED,
      version: 7,
      publishedVersion: 7,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-02-01T00:00:00Z',
    });

    expect(definition.id).toBe('claims-app');
    expect(definition.name).toBe('Claims Management');
    expect(definition.translocoId).toBe('claims.app.name');
    expect(definition.description).toBe('Claims handling');
    expect(definition.materialTheme).toBe('rose-red');
    expect(definition.colorScheme).toBe('dark');
    expect(definition.preset).toBe('top-nav');
    expect(definition.sidenavMode).toBe('over');
    expect(definition.contentMaxWidth).toBe('1280px');
    expect(definition.regions).toEqual([{ type: 'content' }]);
    expect(definition.pages).toEqual([{ id: 'claims', title: 'Claims', widgets: [] }]);
    expect(definition.orgKey).toBe('acme');
    expect(definition.status).toBe(AppDefinitionStatus.PUBLISHED);
    expect(definition.version).toBe(7);
    expect(definition.publishedVersion).toBe(7);
    expect(definition.createdAt).toBe('2026-01-01T00:00:00Z');
    expect(definition.updatedAt).toBe('2026-02-01T00:00:00Z');
  });
});
