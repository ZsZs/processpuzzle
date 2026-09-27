import { computed, Injectable, signal } from '@angular/core';
import { ApplicationContext, NavMenuItem } from '@processpuzzle/widgets';
import { AppDefinition } from '../../domain/app-definition';
import { knownRoutePathsOf } from './app-shell.model';
import { NavRow, toNavRows } from './region-nav.component';

/**
 * The shell's answer to base-widget's `APPLICATION_CONTEXT`: the name, logo and navigation of the definition
 * it renders, for `app-title`, `app-logo` and `nav-menu` to read without props.
 *
 * Provided on `AppShellComponent`, so every widget below it — in a region or in routed content — sees the
 * application it is part of. The shell writes {@link definition}; everything else derives from it.
 */
@Injectable()
export class ShellApplicationContext implements ApplicationContext {
  readonly definition = signal<AppDefinition | undefined>(undefined);

  readonly name = computed(() => this.definition()?.name ?? '');
  readonly logoUrl = computed(() => this.definition()?.logoUrl ?? this.definition()?.theme?.logoUrl);
  readonly navItems = computed(() => navMenuItemsOf(this.definition()));
}

/**
 * The sidenav region's entries as menu items, resolved the way the sidenav resolves them: an entry naming a
 * route the application does not account for keeps its place but loses its link, so the menu never offers
 * a navigation the router would reject.
 */
export function navMenuItemsOf(definition: AppDefinition | undefined): NavMenuItem[] {
  const sidenav = definition?.regions?.find((region) => region.type === 'sidenav');
  return toMenuItems(toNavRows(sidenav?.navItems, knownRoutePathsOf(definition)));
}

function toMenuItems(rows: NavRow[]): NavMenuItem[] {
  return rows.map((row) => ({
    id: row.id,
    label: row.label,
    ...(row.translocoId ? { translocoId: row.translocoId } : {}),
    ...(row.icon ? { icon: row.icon } : {}),
    ...(row.routePath ? { routePath: row.routePath } : {}),
    ...(row.children.length ? { children: toMenuItems(row.children) } : {}),
  }));
}
