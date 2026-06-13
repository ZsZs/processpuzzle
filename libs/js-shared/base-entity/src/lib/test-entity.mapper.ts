import { Injectable } from '@angular/core';
import { BaseEntityMapper } from './base-entity.mapper';
import { TestEntity, TestEnum } from './test-entity';

interface TestEntityDto {
  id?: string;
  name?: string;
  description?: string;
  boolean?: boolean;
  number?: number;
  date?: string | number | Date;
  enumValue?: TestEnum;
}

@Injectable({ providedIn: 'root' })
export class TestEntityMapper implements BaseEntityMapper<TestEntity> {
  fromDto(dto: unknown): TestEntity {
    const source = dto as TestEntityDto;
    return new TestEntity(source.id, source.name, source.description, source.boolean, source.number, source.date == null ? undefined : new Date(source.date), source.enumValue);
  }

  toDto(entity: TestEntity): unknown {
    return entity;
  }
}
