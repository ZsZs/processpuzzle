import { describe, expect, it } from 'vitest';
import { AppDefinition, RegionDefinition, RegionType, RouteDefinition } from '../../domain/app-definition';
import { AppRegionRenderer } from './app-region.renderer';
import { RegionFooterComponent } from './region-footer.component';
import { RegionHeaderComponent } from './region-header.component';
import { RegionNavComponent } from './region-nav.component';

describe('AppRegionRenderer', () => {
  // No TestBed: the renderer answers with data, which is the property that makes it worth extracting.
  const renderer = new AppRegionRenderer();
  const app = new AppDefinition({ id: 'demo', name: 'Demo Application', logoUrl: '/demo-logo.svg' });

  // The brand is widgets too — app-logo and app-title read it through the APPLICATION_CONTEXT.
  it('renders a header from its widgets alone', () => {
    const widgets = [{ id: 'language', type: 'language-selector' }];

    expect(renderer.render(new RegionDefinition({ type: 'header', widgets }), app)).toEqual({
      slot: 'header',
      component: RegionHeaderComponent,
      inputs: { widgets },
    });
  });

  it('renders a footer from its widgets alone', () => {
    const widgets = [{ id: 'version', type: 'version-button' }];

    expect(renderer.render(new RegionDefinition({ type: 'footer', widgets }), app)).toEqual({
      slot: 'footer',
      component: RegionFooterComponent,
      inputs: { widgets },
    });
  });

  it('renders a sidenav from its nav items, leaving the orientation to the shell', () => {
    const navItems = [{ id: 'nav-orders', label: 'Orders', routePath: 'orders' }];

    expect(renderer.render(new RegionDefinition({ type: 'sidenav', navItems }), app)).toEqual({
      slot: 'sidenav',
      component: RegionNavComponent,
      inputs: { navItems, knownPaths: [] },
    });
  });

  it('tells the sidenav which paths the application accounts for, routes and module mounts alike', () => {
    const definition = new AppDefinition({
      name: 'Demo',
      routes: [new RouteDefinition({ path: 'orders', title: 'Orders', kind: 'WIDGETS' })],
      modules: [{ moduleKey: 'order-admin', basePath: 'back-office' }],
    });

    expect(renderer.render(new RegionDefinition({ type: 'sidenav' }), definition)?.inputs['knownPaths']).toEqual(['orders', 'back-office']);
  });

  it.each(['header', 'footer', 'sidenav'] as RegionType[])('substitutes an empty collection for an absent one on %s', (type) => {
    const view = renderer.render(new RegionDefinition({ type }), app);

    expect(view?.inputs['widgets'] ?? view?.inputs['navItems']).toEqual([]);
  });

  it('omits a region whose type the designer has not chosen yet', () => {
    // The normal path while the dropdown is untouched, not an error path.
    expect(renderer.render(new RegionDefinition(), app)).toBeUndefined();
  });

  it('renders a header before a definition has loaded', () => {
    expect(renderer.render(new RegionDefinition({ type: 'header' }), undefined)?.inputs).toEqual({ widgets: [] });
  });
});
