import type { Provider } from '@angular/core';
import type { BaseEntityFacadeRegistry } from '@processpuzzle/base-entity';
import { DOCUMENT_ENTITY_NAME, DOCUMENT_INPUT_PORT_ENTITY_NAME, DOCUMENT_OUTPUT_PORT_ENTITY_NAME } from './domain/document-entity-names';
import { DocumentFacade } from './feature/document.facade';
import { DocumentInputPortFacade } from './feature/document-input-port.facade';
import { DocumentOutputPortFacade } from './feature/document-output-port.facade';

/**
 * The facades of the whole document graph, to be spread into the application's `providers`.
 *
 * The two embedded ones are here for the same reason the routable one is: an embedded entity has a facade
 * like any other — that is what gives it a store — and only its repository differs, reading and writing the
 * `Document` payload rather than an endpoint of its own.
 */
export const BASE_DOCUMENT_FACADE_PROVIDERS: Provider[] = [DocumentFacade, DocumentInputPortFacade, DocumentOutputPortFacade];

/**
 * The same facades keyed by entity name, to be spread into the application's `BASE_ENTITY_FACADE_REGISTRY`
 * value.
 *
 * Every entity an `EMBEDDED_COMPONENTS` attribute of this library names has to appear here, or the control
 * throws on first render rather than showing a list whose rows go nowhere on save — the registry is how it
 * reaches the child's store and descriptor. Spread rather than provided separately, because the token holds
 * one value: a second `provide: BASE_ENTITY_FACADE_REGISTRY` would replace the application's own entities
 * instead of adding to them.
 */
export const BASE_DOCUMENT_ENTITY_FACADES: BaseEntityFacadeRegistry = {
  [DOCUMENT_ENTITY_NAME]: DocumentFacade,
  [DOCUMENT_INPUT_PORT_ENTITY_NAME]: DocumentInputPortFacade,
  [DOCUMENT_OUTPUT_PORT_ENTITY_NAME]: DocumentOutputPortFacade,
};
