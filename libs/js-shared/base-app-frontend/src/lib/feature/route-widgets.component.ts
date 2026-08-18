import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { WIDGET_REGISTRY, WidgetInstance, WidgetPlacement } from '@processpuzzle/base-widget';

/**
 * Renders a route whose kind is WIDGETS: every STANDALONE instance in order, via NgComponentOutlet
 * resolved against WIDGET_REGISTRY. REFERENCED instances are skipped here for the same reason
 * DocumentEditorComponent skips them at its top level — they're placed by whichever STANDALONE
 * container widget names them in props.childIds, not by this component.
 *
 * This is the same rendering rule a region's header/footer widgets need — worth factoring the
 * `@for` + NgComponentOutlet block out into a small shared `WidgetListComponent` once a second
 * caller needs it verbatim, rather than guessing at the shared shape before AppShellComponent exists.
 */
@Component({
  selector: 'pp-route-widgets',
  standalone: true,
  imports: [CommonModule],
  template: `
    @for (widget of standaloneWidgets; track widget.id) {
      <ng-container *ngComponentOutlet="componentOf(widget.type); inputs: widget.props"></ng-container>
    }
  `,
})
export class RouteWidgetsComponent {
  private readonly registry = inject(WIDGET_REGISTRY);
  private readonly route = inject(ActivatedRoute);

  protected readonly standaloneWidgets: WidgetInstance[] =
    (this.route.snapshot.data['widgets'] as WidgetInstance[] | undefined)?.filter((w) => w.placement !== WidgetPlacement.REFERENCED) ?? [];

  protected componentOf(type: string) {
    const component = this.registry.get(type);
    if (!component) throw new Error(`No widget registered for type '${type}' — check provideWidget() calls`);
    return component;
  }
}
