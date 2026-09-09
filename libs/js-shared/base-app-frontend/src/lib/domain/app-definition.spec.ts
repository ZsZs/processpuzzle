import { describe, expect, it } from 'vitest';
import { AppDefinition, AppDefinitionStatus, ModuleMount, NavItem, RegionDefinition, RouteDefinition, WidgetInstance } from './app-definition';

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
      regions: [{ type: 'sidenav' }],
      routes: [new RouteDefinition({ path: 'claims', title: 'Claims', kind: 'WIDGETS' })],
      modules: [new ModuleMount({ moduleKey: 'billing', basePath: 'billing' })],
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
    expect(definition.regions).toEqual([{ type: 'sidenav' }]);
    expect(definition.routes?.[0].path).toBe('claims');
    expect(definition.modules?.[0].basePath).toBe('billing');
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

describe('RouteDefinition', () => {
  it('starts without a kind, the required dropdown being what fills it', () => {
    const route = new RouteDefinition();

    expect(route.path).toBe('');
    expect(route.title).toBe('');
    expect(route.translocoId).toBeUndefined();
    expect(route.kind).toBeUndefined();
    expect(route.widgets).toEqual([]);
  });

  it('carries no id, a route being identified by its path', () => {
    expect(Object.keys(new RouteDefinition())).not.toContain('id');
  });

  it('has no children, nesting being derived from the paths at registration', () => {
    expect('children' in new RouteDefinition()).toBe(false);
  });

  it('exposes every property it was created with, target fields included', () => {
    const route = new RouteDefinition({
      path: 'claims/:id',
      title: 'Claim',
      translocoId: 'claims.route.details.title',
      icon: 'description',
      roles: ['CLAIMS_ADJUSTER'],
      kind: 'ENTITY',
      entityName: 'Claim',
      entityMode: 'DETAILS',
      rsqlFilter: 'status==OPEN',
      documentSlug: 'claims-help',
      widgets: [new WidgetInstance({ id: 'grid', type: 'entity-grid' })],
    });

    expect(route.path).toBe('claims/:id');
    expect(route.title).toBe('Claim');
    expect(route.translocoId).toBe('claims.route.details.title');
    expect(route.icon).toBe('description');
    expect(route.roles).toEqual(['CLAIMS_ADJUSTER']);
    expect(route.kind).toBe('ENTITY');
    expect(route.entityName).toBe('Claim');
    expect(route.entityMode).toBe('DETAILS');
    expect(route.rsqlFilter).toBe('status==OPEN');
    expect(route.documentSlug).toBe('claims-help');
    expect(route.widgets[0].type).toBe('entity-grid');
  });
});

describe('ModuleMount', () => {
  it('starts blank, so the Add button opens an empty mount form', () => {
    const mount = new ModuleMount();

    expect(mount.moduleKey).toBe('');
    expect(mount.basePath).toBe('');
  });

  it('carries no id, the module key identifying the mount', () => {
    expect(Object.keys(new ModuleMount())).toEqual(['moduleKey', 'basePath']);
  });

  it('exposes every property it was created with', () => {
    const mount = new ModuleMount({ moduleKey: 'claims', basePath: 'claims-handling' });

    expect(mount.moduleKey).toBe('claims');
    expect(mount.basePath).toBe('claims-handling');
  });
});

describe('NavItem', () => {
  it('starts as a blank entry with neither a target nor children', () => {
    const navItem = new NavItem();

    expect(navItem.id).toBe('');
    expect(navItem.label).toBe('');
    expect(navItem.translocoId).toBeUndefined();
    expect(navItem.icon).toBeUndefined();
    expect(navItem.routePath).toBeUndefined();
    expect(navItem.roles).toBeUndefined();
    expect(navItem.children).toBeUndefined();
  });

  it('exposes every property it was created with', () => {
    const navItem = new NavItem({ id: 'nav-claims', label: 'Claims', translocoId: 'claims.nav.list', icon: 'description', routePath: 'claims', roles: ['CLAIMS_ADJUSTER'], children: [new NavItem({ id: 'nav-open' })] });

    expect(navItem.id).toBe('nav-claims');
    expect(navItem.label).toBe('Claims');
    expect(navItem.translocoId).toBe('claims.nav.list');
    expect(navItem.icon).toBe('description');
    expect(navItem.routePath).toBe('claims');
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
