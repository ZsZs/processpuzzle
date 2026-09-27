import { Component, computed, inject, input } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { RouterLink } from '@angular/router';
import { EntityLabelPipe } from '@processpuzzle/base-entity';
import { LayoutService } from '@processpuzzle/util';
import { APPLICATION_CONTEXT, NavMenuItem } from '../app-context/application-context';

export const NAV_MENU_VISIBILITIES = ['small-screens', 'always'] as const;
export type NavMenuVisibility = (typeof NAV_MENU_VISIBILITIES)[number];

/** One menu row: an item flattened out of the tree, indented by its depth. */
interface NavMenuRow {
  item: NavMenuItem;
  depth: number;
}

function flatten(items: NavMenuItem[], depth = 0): NavMenuRow[] {
  return items.flatMap((item) => [{ item, depth }, ...flatten(item.children ?? [], depth + 1)]);
}

/**
 * The application's navigation as a drop-down menu — what stands in for the sidenav where there is no room
 * for one. base-app's shell drops the sidenav on a handset layout, so by default this renders on small
 * screens only; `visibility: 'always'` keeps it in every layout.
 *
 * The entries come from the {@link APPLICATION_CONTEXT} unless the `items` prop supplies them. The tree is
 * flattened into one indented list rather than cascading sub-menus, which are hard to hit on a phone; a group
 * entry, which navigates nowhere, is shown as a disabled heading over its children.
 */
@Component({
  selector: 'pp-nav-menu',
  standalone: true,
  imports: [EntityLabelPipe, MatIcon, MatIconButton, MatMenu, MatMenuItem, MatMenuTrigger, RouterLink],
  template: `
    @if (shown() && rows().length > 0) {
      <button mat-icon-button type="button" aria-label="Navigation menu" data-testid="nav-menu-button" [matMenuTriggerFor]="navMenu">
        <mat-icon>menu</mat-icon>
      </button>
      <mat-menu #navMenu="matMenu">
        @for (row of rows(); track row.item.id) {
          @if (row.item.routePath; as routePath) {
            <a mat-menu-item [routerLink]="routePath" [style.padding-inline-start.px]="16 + row.depth * 16" [attr.data-testid]="'nav-menu-' + row.item.id">
              @if (row.item.icon) {
                <mat-icon class="material-symbols-outlined">{{ row.item.icon }}</mat-icon>
              }
              <span>{{ row.item.translocoId | ppLabel: row.item.label }}</span>
            </a>
          } @else {
            <button mat-menu-item type="button" disabled [style.padding-inline-start.px]="16 + row.depth * 16" [attr.data-testid]="'nav-menu-' + row.item.id">
              @if (row.item.icon) {
                <mat-icon class="material-symbols-outlined">{{ row.item.icon }}</mat-icon>
              }
              <span>{{ row.item.translocoId | ppLabel: row.item.label }}</span>
            </button>
          }
        }
      </mat-menu>
    }
  `,
  styles: [
    `
      :host {
        display: inline-flex;
      }
    `,
  ],
})
export class NavMenuComponent {
  readonly items = input<NavMenuItem[] | undefined>(undefined);
  readonly visibility = input<NavMenuVisibility>('small-screens');

  private readonly context = inject(APPLICATION_CONTEXT, { optional: true });
  private readonly layout = inject(LayoutService);

  protected readonly shown = computed(() => this.visibility() === 'always' || this.layout.isSmallDevice());
  protected readonly rows = computed(() => flatten(this.items() ?? this.context?.navItems() ?? []));
}
