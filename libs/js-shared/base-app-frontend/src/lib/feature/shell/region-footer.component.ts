import { Component, input } from '@angular/core';
import { WidgetInstance } from '@processpuzzle/base-widget';
import { WidgetListComponent } from '../widget-list.component';

/**
 * The `footer` region: the widgets it declares, and nothing else. The contract gives a footer no field
 * beyond `widgets`, so this stays a thin styled host over {@link WidgetListComponent} — separate from
 * the header only because the two are different slots with different styling, not because they share
 * no logic.
 */
@Component({
  selector: 'pp-region-footer',
  standalone: true,
  imports: [WidgetListComponent],
  template: `<pp-widget-list [widgets]="widgets()" />`,
  styles: [
    `
      :host {
        align-items: center;
        background-color: var(--pp-surface-header);
        display: flex;
        gap: 16px;
        justify-content: space-between;
        padding: 8px 16px;
      }
    `,
  ],
})
export class RegionFooterComponent {
  readonly widgets = input<WidgetInstance[]>([]);
}
