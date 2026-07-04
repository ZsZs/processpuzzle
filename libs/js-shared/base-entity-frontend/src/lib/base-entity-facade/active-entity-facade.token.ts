import { InjectionToken } from '@angular/core';
import { BaseEntityFacade } from './base-entity-facade';
import { BaseEntity } from '../base-entity/base-entity';

export const ACTIVE_ENTITY_FACADE = new InjectionToken<BaseEntityFacade<BaseEntity>>('ACTIVE_ENTITY_FACADE');
