import { InjectionToken, Optional, Provider, SkipSelf, Type } from '@angular/core';
import type { WidgetDefinition } from '../widget-definition/widget-definition';

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
 * The description half of a {@link WidgetRegistration}: the fields of a `WidgetDefinition` a widget's author
 * knows at build time, as opposed to the ones the server assigns (org, status, versions, timestamps).
 *
 * It is the frontend twin of the catalogue entry the backend seeds, and `widget-contract.spec.ts` holds the
 * two — and the component's own inputs and outputs — to each other. `name` is the only required field, so an
 * aggregator's spec can register a stand-in component without describing it in full.
 */
export type WidgetDescription = Pick<WidgetDefinition, 'name'> & Partial<Pick<WidgetDefinition, 'translocoId' | 'description' | 'category' | 'icon' | 'propsSchema' | 'inputPorts' | 'outputPorts'>>;

/**
 * One widget type: the registry key, the component that renders it and the description of its interface.
 * Declared beside the component, in the widget's own `<name>.widget.ts`, so the three cannot drift apart
 * unnoticed.
 */
export interface WidgetRegistration {
  /** The registry key — what `WidgetInstance.type` names, and the `key` of the matching `WidgetDefinition`. */
  type: string;
  component: Type<unknown>;
  definition: WidgetDescription;
}

/**
 * Maps a widget registry key (WidgetInstance.type / DocumentBlock.type) to its registration. Populated by
 * each feature lib's own provider function — this file never imports a concrete widget component itself,
 * so app-shell, base-document, and any future consumer share one registry without depending on each other.
 */
export const WIDGET_REGISTRY = new InjectionToken<ReadonlyMap<string, WidgetRegistration>>('WIDGET_REGISTRY');

const WIDGET_REGISTRATIONS = new InjectionToken<WidgetRegistration[]>('WIDGET_REGISTRATIONS');

/**
 * Registers one widget type. Call once per widget component, in that component's own feature
 * lib — e.g. `provideCardsGridWidget()` in this lib's `base-widget.providers.ts`, or
 * `provideDocumentViewerWidget()` in base-document-frontend — never in app-shell directly, which
 * would put it in the business of knowing about every widget that exists.
 *
 * Composes in both directions, and needs two mechanisms to do it:
 *
 * - **Within one injector** — `multi: true` on {@link WIDGET_REGISTRATIONS} collects every call.
 *   `@SkipSelf()` alone cannot: it skips the *injector*, not the sibling provider, so N calls in
 *   the same injector would each see an empty parent and the last would win. That was a real
 *   defect — six widgets registered, one survived — and it stayed invisible while nothing was
 *   registered at all.
 * - **Across injectors** — `@Optional() @SkipSelf()` on {@link WIDGET_REGISTRY} chains to the
 *   parent's assembled map, so a lazily-loaded route's widgets add to the root's rather than
 *   replacing them.
 *
 * Every call also (re)provides WIDGET_REGISTRY. Later ones override earlier, which is harmless:
 * each factory reads the whole multi array, so they all assemble the same map. Registration order
 * therefore does not matter.
 */
export function provideWidget(registration: WidgetRegistration): Provider[] {
  return [
    { provide: WIDGET_REGISTRATIONS, useValue: registration, multi: true },
    {
      provide: WIDGET_REGISTRY,
      useFactory: (registrations: WidgetRegistration[], inherited: ReadonlyMap<string, WidgetRegistration> | null) => {
        const map = new Map(inherited ?? []);
        for (const registration of registrations) {
          const existing = map.get(registration.type);
          if (existing && existing.component !== registration.component) {
            throw new Error(`WIDGET_REGISTRY: '${registration.type}' is already registered to a different component.`);
          }
          map.set(registration.type, registration);
        }
        return map;
      },
      deps: [WIDGET_REGISTRATIONS, [new Optional(), new SkipSelf(), WIDGET_REGISTRY]],
    },
  ];
}
