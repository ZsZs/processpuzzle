import { NgTemplateOutlet } from '@angular/common';
import { Component, input } from '@angular/core';
import { MatListItem, MatListItemIcon, MatListItemTitle, MatNavList } from '@angular/material/list';
import { NavItem } from '../../domain/app-definition';

/** Which way the list runs. The shell decides it from `layout.preset`, not the region definition. */
export type NavOrientation = 'vertical' | 'horizontal';

/**
 * Renders the nav tree of the `sidenav` region — in the sidenav for the two sidenav presets, and
 * horizontally in the header row for `top-nav`. One component for both, because the tree and its
 * grouping rules are the same either way and only the axis differs.
 *
 * **No `routerLink` yet.** The routes an `AppDefinition` declares are not registered until the shell
 * gains its nested router context, so a link here would navigate to a URL nothing matches and raise a
 * router error on the first click. The items render as inert rows until then.
 *
 * `label` is rendered rather than `translocoId`: a nav label is *tenant* content, translated under the
 * scope of the module that owns it, and the shell registers no such scope. Resolving it belongs with
 * the module-scope work, and rendering the authored literal is the honest reading until then.
 */
@Component({
  selector: 'pp-region-nav',
  standalone: true,
  imports: [MatNavList, MatListItem, MatListItemIcon, MatListItemTitle, NgTemplateOutlet],
  template: `
    <mat-nav-list [class.pp-region-nav--horizontal]="orientation() === 'horizontal'">
      <ng-container *ngTemplateOutlet="itemList; context: { $implicit: navItems(), depth: 0 }"></ng-container>
    </mat-nav-list>

    <!-- Recursive through ngTemplateOutlet: a NavItem nests in itself, so the depth is not knowable here. -->
    <ng-template #itemList let-items let-depth="depth">
      @for (item of items; track item.id) {
        <mat-list-item class="pp-region-nav__item" [style.padding-inline-start.px]="depth * 12">
          @if (item.icon) {
            <span matListItemIcon class="material-symbols-outlined">{{ item.icon }}</span>
          }
          <div matListItemTitle>{{ item.label }}</div>
        </mat-list-item>
        @if (item.children?.length) {
          <ng-container *ngTemplateOutlet="itemList; context: { $implicit: item.children, depth: depth + 1 }"></ng-container>
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
    `,
  ],
})
export class RegionNavComponent {
  readonly navItems = input<NavItem[]>([]);
  readonly orientation = input<NavOrientation>('vertical');
}
