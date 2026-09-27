import { Component, ComponentRef, computed, effect, inject, input, output, untracked, viewChild, ViewContainerRef } from '@angular/core';
import { WIDGET_REGISTRY, WidgetRegistration } from '../widget-registry/widget-registry.token';
import { HostedWidget, resolveWidgetInputs, WidgetBindingResolver, widgetOutputBindings, WidgetPortEvent } from './widget-bindings';

/**
 * Renders one widget placement — the single host every container uses, so a widget behaves the same in a
 * base-app route, a shell region and a base-document block.
 *
 * A `WidgetInstance.type` is an open string on the contract and the server checks it for blankness alone —
 * it cannot know which components the hosting application registered. So an unregistered type is a state
 * this component has to render, not one it can rule out: it shows a placeholder naming the type, where
 * throwing would take the whole shell down over one mistyped widget and leave the designer with a blank
 * page instead of a pointer to the row at fault.
 *
 * Created from code rather than through `NgComponentOutlet`, because the outlet cannot bind outputs — and
 * a placement's `outputBindings` are part of the contract. The component is re-created only when what it is
 * bound *to* changes (its type, the names of its inputs, its output bindings); a changed value is pushed
 * through `setInput`, so editing a prop in the neighbouring form does not reset the widget's own state.
 *
 * `display: contents`, so wrapping a widget in its host leaves the container's layout exactly as if the
 * widget sat there directly — a header row lays out its buttons, not their hosts.
 */
@Component({
  selector: 'pp-widget-host',
  standalone: true,
  template: `
    @if (registration()) {
      <ng-container #outlet />
    } @else {
      <span class="pp-widget-host__unregistered" [attr.data-testid]="'unregistered-' + widget().id" [title]="explanation()">{{ widget().type }}</span>
    }
  `,
  styles: [
    `
      :host {
        display: contents;
      }
      .pp-widget-host__unregistered {
        font-style: italic;
        opacity: 0.6;
      }
    `,
  ],
})
export class WidgetHostComponent {
  readonly widget = input.required<HostedWidget>();
  /** Resolves the container ports `inputBindings` name. Absent for a container that declares no ports. */
  readonly bindingResolver = input<WidgetBindingResolver>();
  /** Every event of an output the placement binds, addressed to the container port it is bound to. */
  readonly portEmit = output<WidgetPortEvent>();

  /** Optional: an application that registers no widget at all renders every placement as a placeholder. */
  private readonly registry = inject(WIDGET_REGISTRY, { optional: true }) ?? new Map<string, WidgetRegistration>();
  private readonly outlet = viewChild('outlet', { read: ViewContainerRef });
  private componentRef: ComponentRef<unknown> | undefined;

  protected readonly registration = computed(() => {
    const type = this.widget().type;
    return type ? this.registry.get(type) : undefined;
  });

  private readonly inputs = computed(() => {
    const registration = this.registration();
    return registration ? resolveWidgetInputs(this.widget(), registration.component, this.bindingResolver()) : {};
  });

  /** What forces a re-creation. A string, so that an equal shape recomputed from a new object is still equal. */
  private readonly shape = computed(() => {
    const widget = this.widget();
    return `${widget.type}|${Object.keys(this.inputs()).sort().join(',')}|${JSON.stringify(widget.outputBindings ?? {})}`;
  });

  protected readonly explanation = computed(() => `No component is registered for widget type '${this.widget().type}' — check the provideWidget() calls of this application.`);

  constructor() {
    effect(() => {
      const outlet = this.outlet();
      this.shape();
      untracked(() => this.mount(outlet));
    });
    effect(() => {
      const inputs = this.inputs();
      const componentRef = this.componentRef;
      if (!componentRef) return;
      for (const [name, value] of Object.entries(inputs)) componentRef.setInput(name, value);
    });
  }

  private mount(outlet: ViewContainerRef | undefined): void {
    outlet?.clear();
    this.componentRef = undefined;
    const registration = this.registration();
    if (!outlet || !registration) return;
    const bindings = widgetOutputBindings(this.widget(), registration.component, (event) => this.portEmit.emit(event));
    const componentRef = outlet.createComponent(registration.component, { bindings });
    // Set before the widget's first check, so a required input is never read unset.
    for (const [name, value] of Object.entries(this.inputs())) componentRef.setInput(name, value);
    this.componentRef = componentRef;
  }
}
