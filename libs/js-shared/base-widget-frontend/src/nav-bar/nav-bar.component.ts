import { Component, computed, inject, input } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { EntityLabelPipe } from '@processpuzzle/base-entity';
import { LayoutService } from '@processpuzzle/util';
import { APPLICATION_CONTEXT, NavMenuItem } from '../app-context/application-context';

export const NAV_BAR_VISIBILITIES = ['large-screens', 'always'] as const;
export type NavBarVisibility = (typeof NAV_BAR_VISIBILITIES)[number];

/** One entry of a group's drop-down: an item flattened out of the subtree, indented by its depth. */
interface NavBarMenuRow {
  item: NavMenuItem;
  depth: number;
}

function flatten(items: NavMenuItem[], depth = 0): NavBarMenuRow[] {
  return items.flatMap((item) => [{ item, depth }, ...flatten(item.children ?? [], depth + 1)]);
}

/** A top-level entry, with its subtree already flattened for the drop-down a group opens. */
interface NavBarEntry {
  item: NavMenuItem;
  menuRows: NavBarMenuRow[];
}

/**
 * The application's navigation as a horizontal row of links, placeable anywhere in a header or footer.
 *
 * The counterpart of `nav-menu`: that one stands in for the sidenav on a handset, this one lays the same
 * entries out in the row on a larger screen. So by default it hides on small screens, where there is no
 * room for a row of links and a `nav-menu` takes over; `visibility: 'always'` keeps it on every layout.
 *
 * Top-level entries are links. A group entry, which navigates nowhere itself, opens a drop-down of its
 * subtree, flattened and indented the way `nav-menu` shows it. The entries come from the
 * {@link APPLICATION_CONTEXT} unless the `items` prop supplies them.
 *
 * Links are relative, like the shell's own nav, so the same definition navigates correctly in the
 * designer's Preview tab and in a standalone deployment.
 */
@Component({
  selector: 'pp-nav-bar',
  standalone: true,
  imports: [EntityLabelPipe, MatButton, MatIcon, MatMenu, MatMenuItem, MatMenuTrigger, RouterLink, RouterLinkActive],
  template: `
    @if (shown() && entries().length > 0) {
      <nav class="pp-nav-bar" data-testid="nav-bar">
        @for (entry of entries(); track entry.item.id) {
          @if (entry.item.routePath; as routePath) {
            <a mat-button class="pp-nav-bar__item" [routerLink]="routePath" routerLinkActive="pp-nav-bar__item--active" [attr.data-testid]="'nav-bar-' + entry.item.id">
              @if (entry.item.icon) {
                <mat-icon class="material-symbols-outlined">{{ entry.item.icon }}</mat-icon>
              }
              {{ entry.item.translocoId | ppLabel: entry.item.label }}
            </a>
          } @else {
            <button mat-button type="button" class="pp-nav-bar__item" [matMenuTriggerFor]="groupMenu" [attr.data-testid]="'nav-bar-' + entry.item.id">
              @if (entry.item.icon) {
                <mat-icon class="material-symbols-outlined">{{ entry.item.icon }}</mat-icon>
              }
              {{ entry.item.translocoId | ppLabel: entry.item.label }}
              <mat-icon iconPositionEnd>arrow_drop_down</mat-icon>
            </button>
            <mat-menu #groupMenu="matMenu">
              @for (row of entry.menuRows; track row.item.id) {
                @if (row.item.routePath; as routePath) {
                  <a mat-menu-item [routerLink]="routePath" [style.padding-inline-start.px]="16 + row.depth * 16" [attr.data-testid]="'nav-bar-' + row.item.id">
                    @if (row.item.icon) {
                      <mat-icon class="material-symbols-outlined">{{ row.item.icon }}</mat-icon>
                    }
                    <span>{{ row.item.translocoId | ppLabel: row.item.label }}</span>
                  </a>
                } @else {
                  <button mat-menu-item type="button" disabled [style.padding-inline-start.px]="16 + row.depth * 16" [attr.data-testid]="'nav-bar-' + row.item.id">
                    <span>{{ row.item.translocoId | ppLabel: row.item.label }}</span>
                  </button>
                }
              }
            </mat-menu>
          }
        }
      </nav>
    }
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        min-width: 0;
      }
      .pp-nav-bar {
        align-items: center;
        display: flex;
        gap: 4px;
        overflow-x: auto;
      }
      /* The label follows the surface the bar sits on, header or footer, rather than Material's primary. */
      .pp-nav-bar__item {
        --mat-button-text-label-text-color: currentColor;
        color: inherit;
      }
      .pp-nav-bar__item--active {
        background-color: color-mix(in srgb, currentColor 12%, transparent);
      }
    `,
  ],
})
export class NavBarComponent {
  readonly items = input<NavMenuItem[] | undefined>(undefined);
  readonly visibility = input<NavBarVisibility>('large-screens');

  private readonly context = inject(APPLICATION_CONTEXT, { optional: true });
  private readonly layout = inject(LayoutService);

  protected readonly shown = computed(() => this.visibility() === 'always' || !this.layout.isSmallDevice());
  protected readonly entries = computed<NavBarEntry[]>(() =>
    (this.items() ?? this.context?.navItems() ?? []).map((item) => ({ item, menuRows: item.routePath ? [] : flatten(item.children ?? []) })),
  );
}
