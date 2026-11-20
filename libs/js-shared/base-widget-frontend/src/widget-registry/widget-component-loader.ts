import type { Type } from '@angular/core';
import type { WidgetRegistration } from './widget-registry.token';

/** The class of each lazy registration once it has loaded. Keyed by the registration, so a host never waits twice. */
const loaded = new WeakMap<WidgetRegistration, Type<unknown>>();
/** The load still in flight, so two placements of one lazy widget share a single fetch. */
const pending = new WeakMap<WidgetRegistration, Promise<Type<unknown>>>();

/**
 * The component class of a registration, once it is known: at once for an eager one, and for a lazy one once
 * {@link loadWidgetComponent} has resolved. Synchronous, so a second placement of a loaded widget renders without a pause.
 */
export function loadedWidgetComponent(registration: WidgetRegistration): Type<unknown> | undefined {
  return registration.component ?? loaded.get(registration);
}

/**
 * The component class of a registration, loading it if the widget is lazy. Resolves at once for an eager registration.
 *
 * A failed load is not remembered: the next call tries the import again, so a chunk that failed to arrive is not lost
 * for the rest of the session.
 */
export function loadWidgetComponent(registration: WidgetRegistration): Promise<Type<unknown>> {
  const ready = loadedWidgetComponent(registration);
  if (ready) return Promise.resolve(ready);
  const load = registration.loadComponent;
  if (!load) return Promise.reject(new Error(`WIDGET_REGISTRY: '${registration.type}' has neither a component nor a loader.`));

  let inFlight = pending.get(registration);
  if (!inFlight) {
    inFlight = load().then(
      (type) => {
        loaded.set(registration, type);
        pending.delete(registration);
        return type;
      },
      (error: unknown) => {
        pending.delete(registration);
        throw error;
      },
    );
    pending.set(registration, inFlight);
  }
  return inFlight;
}
