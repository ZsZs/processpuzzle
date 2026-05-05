import { BaseEntity } from './base-entity/base-entity';
import { InjectionToken } from '@angular/core';

export const BASE_ENTITY_MAPPER = new InjectionToken<BaseEntityMapper<any>>('BASE_ENTITY_MAPPER');

export interface BaseEntityMapper<Entity extends BaseEntity> {
  fromDto(dto: any, index?: number): Entity;
  toDto(entity: Entity): any;
}

export function getEnumKeyByValue<E>(myEnum: { [key: number]: string }, enumValue: any): E | undefined {
  return Object.values(myEnum)[enumValue] as E;
}

export function getEnumValueByKey<E>(myEnum: { [key: string]: E | string }, enumKey: string): E | undefined {
  return myEnum[enumKey] as E | undefined;
}
