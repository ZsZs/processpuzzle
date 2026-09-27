import { Binding, outputBinding, reflectComponentType, Type } from '@angular/core';
import { WidgetInstance } from '../widget-registry/widget-instance';

/**
 * The part of a placement the host reads. A `Pick` rather than `WidgetInstance` itself because base-document's
 * `DocumentBlock` carries the widget fields as `Partial<WidgetInstance>` — a TEXT block has no `type` — and it
 * must be hostable without a cast.
 */
export type HostedWidget = Pick<WidgetInstance, 'id'> & Partial<Pick<WidgetInstance, 'type' | 'props' | 'inputBindings' | 'outputBindings'>>;

/** Resolves a container port to its current value. What a port *means* is the container's concern. */
export type WidgetBindingResolver = (portName: string) => unknown;

/** One value a hosted widget emitted through an output its placement binds to a container port. */
export interface WidgetPortEvent {
  widgetId: string;
  port: string;
  value: unknown;
}

const warned = new Set<string>();

/**
 * The inputs to set on a widget's component: its `props`, overlaid by every `inputBindings` entry resolved
 * through `resolveBinding` — a bound value is the author saying "this one comes from the container", so it
 * wins over a static prop of the same name — and filtered to the inputs the component actually declares.
 *
 * The filter is what makes a stale placement harmless: props outlive the widget version they were authored
 * against, and setting an input a component does not have is a runtime error. A dropped prop is reported
 * once per widget type, so the author has a pointer without the console filling up on every edit.
 *
 * The one resolution rule of every container — base-app's host and both of base-document's render paths.
 */
export function resolveWidgetInputs(widget: HostedWidget, component: Type<unknown>, resolveBinding?: WidgetBindingResolver): Record<string, unknown> {
  const resolved: Record<string, unknown> = { ...widget.props };
  for (const [propName, portName] of Object.entries(widget.inputBindings ?? {})) {
    resolved[propName] = resolveBinding?.(portName);
  }
  const declared = declaredInputsOf(component);
  const inputs: Record<string, unknown> = {};
  for (const [name, value] of Object.entries(resolved)) {
    if (declared.has(name)) inputs[name] = value;
    else warnOnce(`${widget.type}.${name}`, `Widget '${widget.type}' (${widget.id}) has no input '${name}'; the prop is ignored.`);
  }
  return inputs;
}

/**
 * One output binding per `outputBindings` entry whose event the component declares, each re-emitting the
 * event as a {@link WidgetPortEvent} addressed to the container port it is bound to. For
 * `createComponent({ bindings })` — outputs cannot be attached any other way to a component created from code.
 */
export function widgetOutputBindings(widget: HostedWidget, component: Type<unknown>, emit: (event: WidgetPortEvent) => void): Binding[] {
  const declared = new Set((reflectComponentType(component)?.outputs ?? []).map((output) => output.templateName));
  return Object.entries(widget.outputBindings ?? {}).flatMap(([eventName, port]) => {
    if (!declared.has(eventName)) {
      warnOnce(`${widget.type}.${eventName}`, `Widget '${widget.type}' (${widget.id}) has no output '${eventName}'; the binding is ignored.`);
      return [];
    }
    return [outputBinding<unknown>(eventName, (value) => emit({ widgetId: widget.id, port, value }))];
  });
}

function declaredInputsOf(component: Type<unknown>): Set<string> {
  return new Set((reflectComponentType(component)?.inputs ?? []).map((input) => input.templateName));
}

function warnOnce(key: string, message: string): void {
  if (warned.has(key)) return;
  warned.add(key);
  console.warn(message);
}
