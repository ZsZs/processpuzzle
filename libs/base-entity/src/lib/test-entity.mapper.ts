import { Injectable } from '@angular/core';
import { BaseEntityMapper } from './base-entity.mapper';
import { TestEntity } from './test-entity';

@Injectable({ providedIn: 'root' })
export class TestEntityMapper implements BaseEntityMapper<TestEntity> {
  fromDto(dto: any): TestEntity {
    return new TestEntity(dto.id, dto.name, dto.description, dto.boolean, dto.number, new Date(dto.date), dto.enumValue);
  }

  toDto(entity: TestEntity): any {
    return entity;
  }
}

// function getEnumKeyByEnumValue<E>(myEnum: { [key: number]: string }, enumValue: any): E | undefined {
//   return Object.values(myEnum)[enumValue] as E;
// }
