import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { WidgetInstance } from '@processpuzzle/base-widget';
import { WidgetListComponent } from './widget-list.component';

/**
 * Renders a route whose kind is WIDGETS: every STANDALONE instance in order, via NgComponentOutlet
 * resolved against WIDGET_REGISTRY. REFERENCED instances are skipped here for the same reason
 * DocumentEditorComponent skips them at its top level — they're placed by whichever STANDALONE
 * container widget names them in props.childIds, not by this component.
 *
 * Delegates the common widget rendering rule to WidgetListComponent, which is also used by the
 * header and footer regions in the application preview.
 */
@Component({
  selector: 'pp-route-widgets',
  standalone: true,
  imports: [WidgetListComponent],
  template: `<pp-widget-list [widgets]="widgets" />`,
})
export class RouteWidgetsComponent {
  private readonly route = inject(ActivatedRoute);

  protected readonly widgets = (this.route.snapshot.data['widgets'] as WidgetInstance[] | undefined) ?? [];
}
