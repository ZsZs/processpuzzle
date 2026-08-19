import { Injectable, Type } from '@angular/core';
import { AppDefinition, RegionDefinition, RegionType } from '../../domain/app-definition';
import { RegionFooterComponent } from './region-footer.component';
import { RegionHeaderComponent } from './region-header.component';
import { RegionNavComponent } from './region-nav.component';

/**
 * One region resolved to something renderable: which slot it fills, the component that fills it and
 * the inputs to bind. **Data, not a view** — deliberately the same shape of answer
 * {@link AppRouteRenderer} gives for a route, and for the same reasons: it is assertable from a spec
 * without a `TestBed`, and the shell can bind it declaratively so that editing the definition in the
 * neighbouring form re-renders through ordinary signal propagation.
 *
 * `slot` is where the region *asks* to go. The shell may place it elsewhere — the `top-nav` preset puts
 * the `sidenav` slot's view in the header row — and may override {@link inputs} when it does, which is
 * the second reason this carries inputs rather than a constructed component.
 */
export interface RegionView {
  slot: RegionType;
  component: Type<unknown>;
  inputs: Record<string, unknown>;
}

/**
 * Turns one authored `RegionDefinition` into the {@link RegionView} that renders it.
 *
 * An injectable rather than a free function, though stateless today, for the reason `AppRouteRenderer`
 * is one: the Preview tab and the eventual standalone runtime host must resolve regions *identically*,
 * so there has to be exactly one of these, and a constructor parameter is a cheaper way to grow it than
 * a rewrite.
 */
@Injectable({ providedIn: 'root' })
export class AppRegionRenderer {
  render = (region: RegionDefinition, definition: AppDefinition | undefined): RegionView | undefined => {
    switch (region.type) {
      case 'header':
        return {
          slot: 'header',
          component: RegionHeaderComponent,
          // Brand from the definition, widgets from the region: the two halves of a header row come
          // from different levels of the document, and only this function knows both.
          inputs: { title: definition?.name ?? '', logoUrl: definition?.logoUrl ?? definition?.theme?.logoUrl, widgets: region.widgets ?? [] },
        };
      case 'footer':
        return { slot: 'footer', component: RegionFooterComponent, inputs: { widgets: region.widgets ?? [] } };
      case 'sidenav':
        // `orientation` is left at the component's own default; the shell overrides it for `top-nav`,
        // because the axis follows the layout preset rather than anything authored on the region.
        return { slot: 'sidenav', component: RegionNavComponent, inputs: { navItems: region.navItems ?? [] } };
      default:
        // A region whose `type` the designer has not chosen yet. `RegionDefinition.type` is
        // `RegionType | undefined` precisely because a row exists before its dropdown is touched, and
        // the preview renders it *while* that is happening — so this is the normal path, not an error
        // one. Omitting it is quieter than a placeholder, and the form already shows the empty row.
        return undefined;
    }
  };
}
