import { InjectionToken } from '@angular/core';
import { BaseEntityFacade } from './base-entity-facade';

export const ACTIVE_ENTITY_FACADE = new InjectionToken<BaseEntityFacade<any>>('ACTIVE_ENTITY_FACADE');
