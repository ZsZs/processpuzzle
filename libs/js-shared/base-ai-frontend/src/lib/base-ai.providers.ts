import type { Provider } from '@angular/core';
import { AI_ASSIST_CLIENT, type BaseEntityFacadeRegistry } from '@processpuzzle/base-entity';
import { AuthoringAssistService } from './domain/assist/authoring-assist.service';
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

/**
 * Puts the AI assist button on every form field that asks for one — a rule's expression, for now. Spread into the
 * application's `providers`: without it no button is shown. The button also stays hidden for an organization without an
 * AI subscription and for a user who may not edit the field's target, so providing it is safe everywhere.
 */
export const BASE_AI_ASSIST_PROVIDERS: Provider[] = [{ provide: AI_ASSIST_CLIENT, useExisting: AuthoringAssistService }];
