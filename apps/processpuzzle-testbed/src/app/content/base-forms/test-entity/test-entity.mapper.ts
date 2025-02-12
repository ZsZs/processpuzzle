import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { TestEntity, TestEnum } from './test-entity';
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class TestEntityMapper implements BaseEntityMapper<TestEntity> {
  fromDto(dto: any): TestEntity {
    return new TestEntity(dto.id, dto.name, dto.description, dto.boolean, dto.number, dto.date, getEnumKeyByEnumValue<TestEnum>(TestEnum, dto.enumValue));
  }

  toDto(entity: TestEntity): any {
    return entity;
  }
}

function getEnumKeyByEnumValue<E>(myEnum: { [key: number]: string }, enumValue: any): E | undefined {
  //  const values = Object.values(myEnum).filter((x: any) => myEnum[x] == enumValue);
  //  return values.length > 0 ? (Object.keys(myEnum)[values[0] as any] as E) : undefined;
  return Object.values(myEnum)[enumValue] as E;
}
