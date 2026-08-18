import { CommonModule } from '@angular/common';
import { Component, Type, computed, inject, input } from '@angular/core';
import { WIDGET_REGISTRY, WidgetInstance, WidgetPlacement } from '@processpuzzle/base-widget';

/** Renders the top-level widgets shared by routed content and static shell regions. */
@Component({
  selector: 'pp-widget-list',
  standalone: true,
  imports: [CommonModule],
  template: `
    @for (widget of standaloneWidgets(); track widget.id) {
      <ng-container *ngComponentOutlet="componentOf(widget.type); inputs: widget.props"></ng-container>
    }
  `,
})
export class WidgetListComponent {
  readonly widgets = input<WidgetInstance[]>([]);

  private readonly registry = inject(WIDGET_REGISTRY, { optional: true }) ?? new Map<string, Type<unknown>>();

  protected readonly standaloneWidgets = computed(() => this.widgets().filter((widget) => widget.placement !== WidgetPlacement.REFERENCED));

  protected componentOf(type: string): Type<unknown> {
    const component = this.registry.get(type);
    if (!component) throw new Error(`No widget registered for type '${type}' — check provideWidget() calls`);
    return component;
  }
}
