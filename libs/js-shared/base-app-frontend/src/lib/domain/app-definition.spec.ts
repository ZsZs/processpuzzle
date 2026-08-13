import { describe, expect, it } from 'vitest';
import { AppDefinition, AppDefinitionStatus, NavItem, PageDefinition, RegionDefinition, WidgetInstance } from './app-definition';

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

/**
 * The nested definitions are entities in their own right, and their store mints a blank row with
 * `new <type>()` — so what an argument-less construction produces is what the `Add` button opens the
 * child's form on, and what a save would append to the aggregate if the form were submitted untouched.
 */
describe('RegionDefinition', () => {
  it('starts without a slot, the required dropdown being what fills it', () => {
    const region = new RegionDefinition();

    expect(region.type).toBeUndefined();
    expect(region.navItems).toBeUndefined();
    expect(region.widgets).toBeUndefined();
  });

  it('carries no id, a region being identified by the slot it fills', () => {
    expect(Object.keys(new RegionDefinition())).toEqual(['type', 'navItems', 'widgets']);
  });

  it('exposes every property it was created with', () => {
    const region = new RegionDefinition({ type: 'sidenav', navItems: [new NavItem({ id: 'nav-claims', label: 'Claims' })], widgets: [] });

    expect(region.type).toBe('sidenav');
    expect(region.navItems?.[0].id).toBe('nav-claims');
    expect(region.widgets).toEqual([]);
  });
});

describe('PageDefinition', () => {
  it('starts with the empty widget list the contract requires', () => {
    const page = new PageDefinition();

    expect(page.id).toBe('');
    expect(page.title).toBe('');
    expect(page.translocoId).toBeUndefined();
    expect(page.widgets).toEqual([]);
  });

  it('exposes every property it was created with', () => {
    const page = new PageDefinition({ id: 'claims-list', title: 'Claims', translocoId: 'claims.page.list.title', widgets: [new WidgetInstance({ id: 'grid', type: 'entity-grid' })] });

    expect(page.id).toBe('claims-list');
    expect(page.title).toBe('Claims');
    expect(page.translocoId).toBe('claims.page.list.title');
    expect(page.widgets[0].type).toBe('entity-grid');
  });
});

describe('NavItem', () => {
  it('starts as a blank entry with neither a target nor children', () => {
    const navItem = new NavItem();

    expect(navItem.id).toBe('');
    expect(navItem.label).toBe('');
    expect(navItem.translocoId).toBeUndefined();
    expect(navItem.icon).toBeUndefined();
    expect(navItem.pageId).toBeUndefined();
    expect(navItem.roles).toBeUndefined();
    expect(navItem.children).toBeUndefined();
  });

  it('exposes every property it was created with', () => {
    const navItem = new NavItem({ id: 'nav-claims', label: 'Claims', translocoId: 'claims.nav.list', icon: 'description', pageId: 'page-claims-list', roles: ['CLAIMS_ADJUSTER'], children: [new NavItem({ id: 'nav-open' })] });

    expect(navItem.id).toBe('nav-claims');
    expect(navItem.label).toBe('Claims');
    expect(navItem.translocoId).toBe('claims.nav.list');
    expect(navItem.icon).toBe('description');
    expect(navItem.pageId).toBe('page-claims-list');
    expect(navItem.roles).toEqual(['CLAIMS_ADJUSTER']);
    expect(navItem.children?.[0].id).toBe('nav-open');
  });
});

describe('WidgetInstance', () => {
  it('leaves placement unset, so a new widget renders where it sits without saying so', () => {
    const widget = new WidgetInstance();

    expect(widget.id).toBe('');
    expect(widget.type).toBe('');
    expect(widget.props).toBeUndefined();
    expect(widget.placement).toBeUndefined();
  });

  it('exposes every property it was created with', () => {
    const widget = new WidgetInstance({ id: 'claims-grid', type: 'entity-grid', props: { entityName: 'Claim' }, placement: 'REFERENCED' });

    expect(widget.id).toBe('claims-grid');
    expect(widget.type).toBe('entity-grid');
    expect(widget.props).toEqual({ entityName: 'Claim' });
    expect(widget.placement).toBe('REFERENCED');
  });

  it('composes a container through sibling ids rather than a child collection', () => {
    const container = new WidgetInstance({ id: 'claims-tabs', type: 'tab-group', props: { childIds: ['claims-grid'] } });

    expect(container.props?.['childIds']).toEqual(['claims-grid']);
    expect('children' in container).toBe(false);
  });
});
