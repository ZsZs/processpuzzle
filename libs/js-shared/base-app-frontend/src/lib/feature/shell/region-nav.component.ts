import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { MatListItem, MatListItemIcon, MatListItemTitle, MatNavList } from '@angular/material/list';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { EntityLabelPipe } from '@processpuzzle/base-entity';
import { NavItem } from '../../domain/app-definition';

/** Which way the list runs. The shell decides it from `layout.preset`, not the region definition. */
export type NavOrientation = 'vertical' | 'horizontal';

/**
 * One rendered row. A view model rather than the `NavItem` itself, so that "does this item navigate, and
 * does its target exist" is decided once per definition in a pure function instead of by methods the
 * template would re-run on every change detection pass.
 */
export interface NavRow {
  id: string;
  label: string;
  /**
   * Transloco key for {@link label}, preferred over it when the key resolves. Carried onto the row rather
   * than translated here: `toNavRows` is a pure function and translation depends on the active language
   * and on which scopes have finished loading, both of which are the template's concern.
   */
  translocoId?: string;
  icon?: string;
  /** Present only when the item names a route that something accounts for — see {@link toNavRows}. */
  routePath?: string;
  /** The item names a route path, but nothing in the definition accounts for it. */
  unresolved: boolean;
  children: NavRow[];
}

/**
 * Maps the authored nav tree onto rows, resolving each `routePath` against the paths the definition
 * accounts for.
 *
 * A path counts as resolved if it equals a declared route's path *or* sits below one — below a declared
 * route, or below a mounted module's base path. The prefix rule is what keeps this from crying wolf: the
 * routes inside a module live in the module's own definition, which is fetched lazily and is not knowable
 * here, so `back-office/lines` has to be trusted on the strength of the `back-office` mount alone.
 *
 * A group node — no `routePath`, children instead — is not unresolved. It expands rather than navigates.
 */
export function toNavRows(items: NavItem[] | undefined, knownPaths: string[]): NavRow[] {
  return (items ?? []).map((item) => {
    const routePath = normalize(item.routePath);
    const resolved = routePath !== undefined && knownPaths.some((known) => routePath === known || routePath.startsWith(`${known}/`));
    return {
      id: item.id,
      label: item.label,
      translocoId: item.translocoId,
      icon: item.icon,
      ...(resolved ? { routePath } : {}),
      unresolved: routePath !== undefined && !resolved,
      children: toNavRows(item.children, knownPaths),
    };
  });
}

/**
 * A route path as it is compared and linked: no surrounding slashes, since `RouteDefinition.path` is
 * relative with no leading slash by contract and a nav item may still have been authored with one.
 * Whitespace-only counts as absent — an item mid-authoring is a group node, not a broken link.
 */
function normalize(routePath: string | undefined): string | undefined {
  const trimmed = routePath?.trim().replace(/^\/+|\/+$/g, '').trim();
  return trimmed ? trimmed : undefined;
}

/**
 * Renders the nav tree of the `sidenav` region — in the sidenav for the two sidenav presets, and
 * horizontally in the header row for `top-nav`. One component for both, because the tree and its
 * grouping rules are the same either way and only the axis differs.
 *
 * Links are relative, which is what makes the same component work in the designer's Preview tab and in a
 * standalone deployment: `routerLink="orders"` resolves against whichever route hosts the shell, so it
 * becomes `…/app-definition/demo/preview/orders` under the designer and `/orders` under a runtime host,
 * with nothing here needing to know which.
 *
 * An item whose target nothing accounts for is rendered as an inert row with a title, not as a link that
 * would navigate into a 404. A dangling `routePath` is a *warning* server-side by design — it may name a
 * route of a module authored later — so the shell has to render it as something rather than reject it.
 *
 * `translocoId` is preferred over `label` where it resolves, through the impure `ppLabel` pipe — so a row
 * shows the authored literal until the scope that owns its key has loaded, then re-renders. A nav label is
 * *tenant* content living under a module's own scope, which for an authored module is served by the
 * backend rather than shipped as an asset; `ppLabel` falling back is the normal case, not a defect.
 */
@Component({
  selector: 'pp-region-nav',
  standalone: true,
  imports: [MatNavList, MatListItem, MatListItemIcon, MatListItemTitle, NgTemplateOutlet, RouterLink, RouterLinkActive, EntityLabelPipe],
  template: `
    <mat-nav-list [class.pp-region-nav--horizontal]="orientation() === 'horizontal'">
      <ng-container *ngTemplateOutlet="rowList; context: { $implicit: rows(), depth: 0 }"></ng-container>
    </mat-nav-list>

    <!--
      Recursive through ngTemplateOutlet: a NavItem nests in itself, so the depth is not knowable here.

      The icon and the title are spelled out in both branches rather than shared through one more template.
      A list item sorts its content with an ng-content selector on [matListItemIcon], and a projection
      selector is matched at compile time against the element's own children — so anything arriving through
      ngTemplateOutlet is one opaque node to it, and lands in the unprojected slot beside the title. That is
      what put the icon on a line of its own above every label.
    -->
    <ng-template #rowList let-rows let-depth="depth">
      @for (row of rows; track row.id) {
        @if (row.routePath; as routePath) {
          <!-- An anchor, not a bare list item: RouterLink only writes href onto one, and without it the row
               is not a real link — no middle-click, no open-in-new-tab, no keyboard activation. -->
          <a
            mat-list-item
            class="pp-region-nav__item"
            [routerLink]="routePath"
            routerLinkActive="pp-region-nav__item--active"
            [style.padding-inline-start.px]="depth * 12"
            [attr.data-testid]="'nav-' + row.id"
          >
            @if (row.icon) {
              <span matListItemIcon class="material-symbols-outlined">{{ row.icon }}</span>
            }
            <div matListItemTitle>{{ row.translocoId | ppLabel: row.label }}</div>
          </a>
        } @else {
          <mat-list-item
            class="pp-region-nav__item"
            [class.pp-region-nav__item--unresolved]="row.unresolved"
            [title]="row.unresolved ? 'No route of this application matches this navigation entry yet.' : ''"
            [style.padding-inline-start.px]="depth * 12"
            [attr.data-testid]="'nav-' + row.id"
          >
            @if (row.icon) {
              <span matListItemIcon class="material-symbols-outlined">{{ row.icon }}</span>
            }
            <div matListItemTitle>{{ row.translocoId | ppLabel: row.label }}</div>
          </mat-list-item>
        }
        @if (row.children.length) {
          <ng-container *ngTemplateOutlet="rowList; context: { $implicit: row.children, depth: depth + 1 }"></ng-container>
        }
      }
    </ng-template>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .pp-region-nav--horizontal {
        display: flex;
        padding-top: 0;
      }
      .pp-region-nav--horizontal .pp-region-nav__item {
        width: auto;
      }
      .pp-region-nav__item--unresolved {
        font-style: italic;
        opacity: 0.6;
      }
    `,
  ],
})
export class RegionNavComponent {
  readonly navItems = input<NavItem[]>([]);
  readonly orientation = input<NavOrientation>('vertical');
  /** Route paths the definition accounts for, supplied by `AppRegionRenderer`. See {@link toNavRows}. */
  readonly knownPaths = input<string[]>([]);

  protected readonly rows = computed(() => toNavRows(this.navItems(), this.knownPaths()));
}
