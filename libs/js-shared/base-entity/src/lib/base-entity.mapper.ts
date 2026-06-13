import { BaseEntity } from './base-entity/base-entity';
import { InjectionToken } from '@angular/core';

export const BASE_ENTITY_MAPPER = new InjectionToken<BaseEntityMapper<BaseEntity>>('BASE_ENTITY_MAPPER');

export interface BaseEntityMapper<Entity extends BaseEntity> {
  fromDto(dto: unknown, index?: number): Entity;
  toDto(entity: Entity): unknown;
}

export class SimpleEntityMapper<Entity extends BaseEntity> implements BaseEntityMapper<Entity> {
  constructor(private readonly entityType: new (...args: unknown[]) => Entity) {}

  fromDto(dto: unknown): Entity {
    const entity = new this.entityType();
    Object.assign(entity as object, dto);
    return entity;
  }

  toDto(entity: Entity): unknown {
    return { ...(entity as object) };
  }
}

export function getEnumKeyByValue<E>(myEnum: { [key: number]: string }, enumValue: number): E | undefined {
  return Object.values(myEnum)[enumValue] as E;
}

export function getEnumValueByKey<E>(myEnum: { [key: string]: E | string }, enumKey: string): E | undefined {
  return myEnum[enumKey] as E | undefined;
}
