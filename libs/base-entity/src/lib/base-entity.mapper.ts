import { BaseEntity } from './base-entity/base-entity';
import { InjectionToken } from '@angular/core';

export const BASE_ENTITY_MAPPER = new InjectionToken<BaseEntityMapper<any>>('BASE_ENTITY_MAPPER');

export interface BaseEntityMapper<Entity extends BaseEntity> {
  fromDto(dto: any, index?: number): Entity;
  toDto(entity: Entity): any;
}
