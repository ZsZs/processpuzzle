import type { Provider } from '@angular/core';
import type { BaseEntityFacadeRegistry } from '@processpuzzle/base-entity';
import { RECOGNITION_PROFILE_ENTITY_NAME } from './domain/profile/recognition-profile';
import { RecognitionProfileFacade } from './feature/profile/recognition-profile.facade';

/** The facades of this library, to be spread into the application's `providers`. */
export const BASE_AI_FACADE_PROVIDERS: Provider[] = [RecognitionProfileFacade];

/**
 * The same facades keyed by entity name, to be spread into the application's `BASE_ENTITY_FACADE_REGISTRY`
 * value — spread rather than provided, because the token holds one value and a second provider would
 * replace the application's own entities.
 */
export const BASE_AI_ENTITY_FACADES: BaseEntityFacadeRegistry = {
  [RECOGNITION_PROFILE_ENTITY_NAME]: RecognitionProfileFacade,
};
