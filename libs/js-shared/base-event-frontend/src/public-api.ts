/*
 * Public API Surface of @processpuzzle/base-event
 */
export { BASE_EVENT_TRANSLATION_SOURCE, BASE_EVENT_TRANSLOCO_SCOPE, EVENT_DEFINITION_I18N_SCOPE, EVENT_PARAMETER_I18N_SCOPE } from './lib/base-event.i18n';
export { EventAction, EventDefinition, EventKind, EventParameter, EventParameterType } from './lib/domain/event-definition';
export { EVENT_DEFINITION_ENTITY_NAME, createEventDefinitionDescriptor } from './lib/domain/event-definition.descriptors';
export { EVENT_PARAMETER_ENTITY_NAME, EVENT_PARAMETER_ID_FIELD, createEventParameterDescriptor } from './lib/domain/event-parameter.descriptors';
export { EventDefinitionMapper, type EventDefinitionDto, type EventParameterDto } from './lib/domain/event-definition.mapper';
export { EventDefinitionService } from './lib/domain/event-definition.service';
export { EventDefinitionStore } from './lib/domain/event-definition.store';
export { EventDefinitionFacade } from './lib/feature/event-definition.facade';
export { EventParameterFacade } from './lib/feature/event-parameter.facade';
export { BASE_EVENT_ENTITY_FACADES, BASE_EVENT_FACADE_PROVIDERS } from './lib/base-event.providers';
export { BASE_EVENT_ROUTES } from './lib/base-event.routes';
