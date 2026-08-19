import { CommonModule } from '@angular/common';
import { Component, Type, computed, inject, input } from '@angular/core';
import { WIDGET_REGISTRY, WidgetInstance, WidgetPlacement } from '@processpuzzle/base-widget';

/**
 * One widget as it is rendered: the component the registry answered with, or none.
 *
 * Resolved once per definition into a computed rather than by a template method, so the lookup does not
 * re-run on every change detection pass and `ngComponentOutlet` is not handed a fresh value each time.
 */
export interface WidgetRow {
  id: string;
  type: string;
  props?: Record<string, unknown>;
  /** Absent when no `provideWidget()` in the hosting application answers `type`. */
  component?: Type<unknown>;
}

/** Maps widget instances onto rows, dropping the ones a container places by id rather than in sequence. */
export function toWidgetRows(widgets: WidgetInstance[] | undefined, registry: ReadonlyMap<string, Type<unknown>>): WidgetRow[] {
  return (widgets ?? [])
    .filter((widget) => widget.placement !== WidgetPlacement.REFERENCED)
    .map((widget) => {
      const component = registry.get(widget.type);
      return { id: widget.id, type: widget.type, props: widget.props, ...(component ? { component } : {}) };
    });
}

/**
 * Renders the top-level widgets shared by routed content and static shell regions.
 *
 * A `WidgetInstance.type` is an open string on the contract and the server checks it for blankness alone —
 * it cannot know which components the hosting application registered. So an unregistered type is a state
 * this component has to render, not one it can rule out: it shows a placeholder naming the type, where
 * throwing would take the whole shell down over one mistyped widget in one region and leave the designer
 * with a blank page instead of a pointer to the row at fault.
 */
@Component({
  selector: 'pp-widget-list',
  standalone: true,
  imports: [CommonModule],
  template: `
    @for (row of rows(); track row.id) {
      @if (row.component; as component) {
        <ng-container *ngComponentOutlet="component; inputs: row.props"></ng-container>
      } @else {
        <span class="pp-widget-list__unregistered" [attr.data-testid]="'unregistered-' + row.id" [title]="explain(row.type)">{{ row.type }}</span>
      }
    }
  `,
  styles: [
    `
      .pp-widget-list__unregistered {
        font-style: italic;
        opacity: 0.6;
      }
    `,
  ],
})
export class WidgetListComponent {
  readonly widgets = input<WidgetInstance[]>([]);

  /** Optional: an application that registers no widget at all renders every instance as a placeholder. */
  private readonly registry = inject(WIDGET_REGISTRY, { optional: true }) ?? new Map<string, Type<unknown>>();

  protected readonly rows = computed(() => toWidgetRows(this.widgets(), this.registry));

  protected explain(type: string): string {
    return `No component is registered for widget type '${type}' — check the provideWidget() calls of this application.`;
  }
}
