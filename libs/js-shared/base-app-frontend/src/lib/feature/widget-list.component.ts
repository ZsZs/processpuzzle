import { Component, computed, input } from '@angular/core';
import { WidgetHostComponent, WidgetInstance, WidgetPlacement } from '@processpuzzle/widgets';

/** The widgets rendered in sequence — every one but those a container widget places by id. */
export function standaloneWidgets(widgets: WidgetInstance[] | undefined): WidgetInstance[] {
  return (widgets ?? []).filter((widget) => widget.placement !== WidgetPlacement.REFERENCED);
}

/**
 * Renders the top-level widgets shared by routed content and static shell regions, each through
 * base-widget's {@link WidgetHostComponent} — which resolves the component, binds the props and shows a
 * placeholder for a type the application did not register. This component only decides *which* widgets
 * render here, and in what order.
 *
 * base-app passes no binding resolver: a page or region declares no ports for `inputBindings` to resolve
 * against, so a placement here is configured by its props alone.
 */
@Component({
  selector: 'pp-widget-list',
  standalone: true,
  imports: [WidgetHostComponent],
  template: `
    @for (widget of rows(); track widget.id) {
      <pp-widget-host [widget]="widget" />
    }
  `,
})
export class WidgetListComponent {
  readonly widgets = input<WidgetInstance[]>([]);

  protected readonly rows = computed(() => standaloneWidgets(this.widgets()));
}
