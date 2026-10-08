/*
 * Public API Surface of @processpuzzle/base-event
 */
export { BASE_EVENT_TRANSLATION_SOURCE, BASE_EVENT_TRANSLOCO_SCOPE, EVENT_DEFINITION_I18N_SCOPE } from './lib/base-event.i18n';
export { EventAction, EventDefinition, EventKind } from './lib/domain/event-definition';
export { EVENT_DEFINITION_ENTITY_NAME, createEventDefinitionDescriptor } from './lib/domain/event-definition.descriptors';
export { EventDefinitionMapper, type EventDefinitionDto } from './lib/domain/event-definition.mapper';
export { EventDefinitionService } from './lib/domain/event-definition.service';
export { EventDefinitionStore } from './lib/domain/event-definition.store';
export { EventDefinitionFacade } from './lib/feature/event-definition.facade';
export { BASE_EVENT_ENTITY_FACADES, BASE_EVENT_FACADE_PROVIDERS } from './lib/base-event.providers';
export { BASE_EVENT_ROUTES } from './lib/base-event.routes';
