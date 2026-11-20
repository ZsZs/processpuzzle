import { Component, ComponentRef, computed, effect, inject, input, output, signal, untracked, viewChild, ViewContainerRef } from '@angular/core';
import { loadedWidgetComponent, loadWidgetComponent } from '../widget-registry/widget-component-loader';
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
    }
    @if (!componentType()) {
      @if (loadFailed()) {
        <span class="pp-widget-host__unregistered" [attr.data-testid]="'load-failed-' + widget().id" [title]="loadFailure()">{{ widget().type }}</span>
      } @else if (registration()) {
        <span class="pp-widget-host__loading" [attr.data-testid]="'loading-' + widget().id" aria-busy="true"></span>
      } @else {
        <span class="pp-widget-host__unregistered" [attr.data-testid]="'unregistered-' + widget().id" [title]="explanation()">{{ widget().type }}</span>
      }
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
      .pp-widget-host__loading {
        display: block;
        min-height: 1px;
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

  /** The widget's class once known. A lazy widget's class arrives later; `loadedTick` is bumped when it does. */
  private readonly loadedTick = signal(0);
  private readonly failedRegistration = signal<WidgetRegistration | undefined>(undefined);
  protected readonly componentType = computed(() => {
    this.loadedTick();
    const registration = this.registration();
    return registration ? loadedWidgetComponent(registration) : undefined;
  });
  protected readonly loadFailed = computed(() => !!this.registration() && this.failedRegistration() === this.registration());

  private readonly inputs = computed(() => {
    const type = this.componentType();
    return type ? resolveWidgetInputs(this.widget(), type, this.bindingResolver()) : {};
  });

  /** What forces a re-creation. A string, so that an equal shape recomputed from a new object is still equal. */
  private readonly shape = computed(() => {
    const widget = this.widget();
    // Whether the class is known is part of the shape: a lazy widget with no inputs must still mount once it arrives.
    return `${widget.type}|${this.componentType() ? 'loaded' : 'pending'}|${Object.keys(this.inputs())
      .sort((left, right) => left.localeCompare(right))
      .join(',')}|${JSON.stringify(widget.outputBindings ?? {})}`;
  });

  protected readonly explanation = computed(() => `No component is registered for widget type '${this.widget().type}' — check the provideWidget() calls of this application.`);
  protected readonly loadFailure = computed(() => `The component for widget type '${this.widget().type}' could not be loaded. It is tried again the next time the page shows it.`);

  constructor() {
    // Starts the fetch of a lazy widget. A result that arrives after the placement moved on is cached by the loader and
    // simply not rendered: `componentType` only reads the registration it is given.
    effect(() => {
      const registration = this.registration();
      if (!registration || loadedWidgetComponent(registration)) return;
      loadWidgetComponent(registration).then(
        () => this.loadedTick.update((tick) => tick + 1),
        () => this.failedRegistration.set(registration),
      );
    });
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
    const type = this.componentType();
    if (!outlet || !type) return;
    const bindings = widgetOutputBindings(this.widget(), type, (event) => this.portEmit.emit(event));
    const componentRef = outlet.createComponent(type, { bindings });
    // Set before the widget's first check, so a required input is never read unset.
    for (const [name, value] of Object.entries(this.inputs())) componentRef.setInput(name, value);
    this.componentRef = componentRef;
  }
}
