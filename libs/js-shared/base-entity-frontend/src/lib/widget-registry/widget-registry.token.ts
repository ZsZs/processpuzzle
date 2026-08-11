import { InjectionToken, Optional, Provider, SkipSelf, Type } from '@angular/core';

/**
 * PLACEMENT: this lives in base-entity-frontend rather than in either consumer because
 * base-app-frontend and base-document-frontend both need it and neither depends on the other,
 * while base-entity-frontend is the one lib both already depend on — the same reasoning that
 * puts FormControlType and EMBEDDED_COMPONENTS here rather than in base-app. The cost is that
 * base-entity-frontend picks up a second responsibility (runtime widget resolution) beyond
 * form/list rendering; the alternative was a new minimal lib with its own project.json, more
 * correct in isolation but a lot of ceremony for one token and one function.
 *
 * Still open, and deliberately not solved here: libs/js-shared/widgets already exists and holds
 * unrelated general-purpose UI components (like-button, share-button, language-selector) — a
 * second, different meaning of "widget" in the same workspace. (The other name collision this
 * comment used to flag — ArtifactAttr/FormControlType.ARTIFACT against base-artifact's own
 * "Artifact" — is resolved: that feature is now base-document and its noun is Document, so
 * ARTIFACT unambiguously means the file-attachment control.)
 */

/**
 * Maps a widget registry key (WidgetRef.type / DocumentBlock.type) to the Angular component
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
