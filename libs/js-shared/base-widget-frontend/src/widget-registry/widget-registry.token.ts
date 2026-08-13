import { InjectionToken, Optional, Provider, SkipSelf, Type } from '@angular/core';

/**
 * PLACEMENT: this lives in base-widget-frontend, the library of widget building blocks. It was
 * parked in base-entity-frontend for a while — base-app-frontend and base-document-frontend both
 * need it, neither depends on the other, and base-entity-frontend was the one lib both already
 * had — at the cost of giving base-entity a second responsibility (runtime widget resolution)
 * beyond form/list rendering. That is resolved: this library exists now and both consumers
 * depend on it, so base-entity-frontend has no widget responsibility left.
 *
 * The layering rule it follows: **widgets are building blocks, apps and documents are
 * aggregators.** Both aggregators may embed widgets; neither is embeddable into a widget. So this
 * library sits below base-app-frontend and base-document-frontend and may never depend on either.
 * A widget that surfaces an aggregator's content belongs to that aggregator — `document-viewer`
 * belongs in base-document-frontend and registers itself through {@link provideWidget}, because
 * it is what embeds a document *into an app*, not something you embed into a document.
 *
 * The old name collision went away with the rename: libs/js-shared/widgets, which held
 * general-purpose UI components under a second, different meaning of "widget", *is* this library.
 * (The other collision this comment used to flag — ArtifactAttr/FormControlType.ARTIFACT against
 * base-artifact's own "Artifact" — is likewise resolved: that feature is now base-document and its
 * noun is Document, so ARTIFACT unambiguously means the file-attachment control.)
 */

/**
 * Maps a widget registry key (WidgetInstance.type / DocumentBlock.type) to the Angular component
 * that renders it. Populated by each feature lib's own provider function — this file never
 * imports a concrete widget component itself, so app-shell, base-document, and any future
 * consumer share one registry without depending on each other.
 */
export const WIDGET_REGISTRY = new InjectionToken<ReadonlyMap<string, Type<unknown>>>('WIDGET_REGISTRY');

/**
 * Registers one widget type. Call once per widget component, in that component's own feature
 * lib — e.g. `provideEntityGridWidget()` in the entity-grid lib's `provide*` barrel — never in
 * app-shell or base-document directly, which would put them in the business of knowing about
 * every widget that exists.
 *
 * Multiple calls compose: each provider merges onto whatever the token already resolved to via
 * Angular's own multi-provider resolution (`@Optional() @SkipSelf()`), so registration order
 * across lazily-loaded feature libs doesn't matter.
 */
export function provideWidget(type: string, component: Type<unknown>): Provider {
  return {
    provide: WIDGET_REGISTRY,
    useFactory: (existing: ReadonlyMap<string, Type<unknown>> | null) => {
      const map = new Map(existing ?? []);
      if (map.has(type) && map.get(type) !== component) {
        throw new Error(`WIDGET_REGISTRY: '${type}' is already registered to a different component.`);
      }
      map.set(type, component);
      return map;
    },
    deps: [[new Optional(), new SkipSelf(), WIDGET_REGISTRY]],
  };
}
