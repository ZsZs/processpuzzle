import { Component, input } from '@angular/core';
import { WidgetInstance } from '@processpuzzle/base-widget';
import { WidgetListComponent } from '../widget-list.component';

/**
 * The `header` region: the application's brand block and the widgets the region declares.
 *
 * The brand is the shell's contribution rather than the region's — `logoUrl` and the title come from
 * the definition's theme and name, not from anything authored on the region — which is why they are
 * inputs the renderer fills instead of something this component reaches into a store for. That keeps
 * it renderable from a spec with three inputs and no providers.
 *
 * Nav items are *not* handled here. Under the `top-nav` preset the shell places the `sidenav` region's
 * nav beside this component in the same row, so that a `top-nav` app with no header region is still
 * navigable — see {@link AppShellComponent}.
 */
@Component({
  selector: 'pp-region-header',
  standalone: true,
  imports: [WidgetListComponent],
  template: `
    <div class="pp-region-header__brand">
      @if (logoUrl(); as logo) {
        <img class="pp-region-header__logo" [src]="logo" [alt]="title()" />
      }
      <h2 class="pp-region-header__title">{{ title() }}</h2>
    </div>
    <pp-widget-list [widgets]="widgets()" />
  `,
  styles: [
    `
      :host {
        align-items: center;
        background-color: var(--pp-surface-header);
        display: flex;
        flex: 1;
        gap: 16px;
        justify-content: space-between;
        padding: 8px 16px;
      }
      .pp-region-header__brand {
        align-items: center;
        display: flex;
        gap: 12px;
        min-width: 0;
      }
      .pp-region-header__logo {
        display: block;
        max-height: 48px;
        max-width: 160px;
        object-fit: contain;
      }
      .pp-region-header__title {
        margin: 0;
      }
      /*
       * A row, because a header's widgets sit beside one another. The list component itself stays
       * unopinionated — the same component fills a routed content area, where a landing page's widgets
       * belong *under* one another — so the axis is the region's to choose, and a chrome row is the one
       * place it is not the default.
       */
      pp-widget-list {
        align-items: center;
        display: flex;
        gap: 8px;
      }
    `,
  ],
})
export class RegionHeaderComponent {
  readonly title = input('');
  readonly logoUrl = input<string | undefined>(undefined);
  readonly widgets = input<WidgetInstance[]>([]);
}
