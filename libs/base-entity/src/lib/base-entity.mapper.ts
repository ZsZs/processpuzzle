import { BaseEntity } from './base-entity/base-entity';
import { InjectionToken } from '@angular/core';

export const BASE_ENTITY_MAPPER = new InjectionToken<BaseEntityMapper<any>>('BASE_ENTITY_MAPPER');

export interface BaseEntityMapper<Entity extends BaseEntity> {
  fromDto(dto: any, index?: number): Entity;
  toDto(entity: Entity): any;
}

export class SimpleEntityMapper<Entity extends BaseEntity> implements BaseEntityMapper<Entity> {
  constructor(private readonly entityType: new (...args: any[]) => Entity) {}

  fromDto(dto: any): Entity {
    const entity = new this.entityType();
    Object.assign(entity as object, dto);
    return entity;
  }

  toDto(entity: Entity): any {
    return { ...(entity as object) };
  }
}

export function getEnumKeyByValue<E>(myEnum: { [key: number]: string }, enumValue: any): E | undefined {
  return Object.values(myEnum)[enumValue] as E;
}

export function getEnumValueByKey<E>(myEnum: { [key: string]: E | string }, enumKey: string): E | undefined {
  return myEnum[enumKey] as E | undefined;
}
