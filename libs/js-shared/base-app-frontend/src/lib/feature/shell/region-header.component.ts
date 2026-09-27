import { Component, input } from '@angular/core';
import { WidgetInstance } from '@processpuzzle/widgets';
import { WidgetListComponent } from '../widget-list.component';

/**
 * The `header` region: the widgets the region declares, laid out in one row.
 *
 * The brand is widgets too — `app-logo` and `app-title` — which read the application's name and logo from
 * the shell's `APPLICATION_CONTEXT`. So a designer orders, omits or restyles them like any other widget, and
 * the header has no second, built-in way of showing the same thing. `--pp-app-title-color` is set to the
 * on-header colour here, so the title stays legible on the header surface whatever the theme.
 *
 * Nav items are *not* handled here. Under the `top-nav` preset the shell places the `sidenav` region's
 * nav beside this component in the same row, so that a `top-nav` app with no header region is still
 * navigable — see {@link AppShellComponent}.
 */
@Component({
  selector: 'pp-region-header',
  standalone: true,
  imports: [WidgetListComponent],
  template: `<pp-widget-list [widgets]="widgets()" />`,
  styles: [
    `
      :host {
        --pp-app-title-color: var(--pp-on-header, inherit);
        background-color: var(--pp-surface-header);
        color: var(--pp-on-header);
        align-items: center;
        display: flex;
        flex: 1;
        padding: 8px 16px;
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
        flex: 1;
        gap: 8px;
        min-width: 0;
      }
    `,
  ],
})
export class RegionHeaderComponent {
  readonly widgets = input<WidgetInstance[]>([]);
}
