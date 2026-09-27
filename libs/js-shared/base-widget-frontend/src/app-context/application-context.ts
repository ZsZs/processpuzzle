import { InjectionToken, Signal } from '@angular/core';

/**
 * One entry of the application's navigation, as the widgets that render it need it. A group node has no
 * `routePath` and only expands its `children`.
 *
 * base-app's `NavItem` has this shape and more; declared here rather than imported because this library
 * sits below base-app — see the layering note on `WIDGET_REGISTRY`.
 */
export interface NavMenuItem {
  id: string;
  label: string;
  translocoId?: string;
  icon?: string;
  routePath?: string;
  children?: NavMenuItem[];
}

/**
 * What a chrome widget may know about the application it is placed in: its name, its logo and its
 * navigation. The *environment* half of the widget contract — see `docs/widget-embedding-spec.md` §2 — so
 * `app-title`, `app-logo` and `nav-menu` need no props when they sit in an application's own header.
 *
 * base-app's shell provides it from the `AppDefinition` it renders; a hand-built application may provide
 * its own. Optional everywhere it is injected: a widget placed where nobody provides it — a document read
 * outside any application — falls back to its props, and renders nothing it has nothing for.
 */
export interface ApplicationContext {
  readonly name: Signal<string>;
  readonly logoUrl: Signal<string | undefined>;
  /** Only entries that navigate somewhere; entries naming no known route are the provider's to drop. */
  readonly navItems: Signal<NavMenuItem[]>;
}

export const APPLICATION_CONTEXT = new InjectionToken<ApplicationContext>('APPLICATION_CONTEXT');
