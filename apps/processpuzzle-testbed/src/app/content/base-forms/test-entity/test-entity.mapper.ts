import { BaseEntityMapper, getEnumKeyByValue } from '@processpuzzle/base-entity';
import { TestEntity, TestEnum } from './test-entity';
import { inject, Injectable } from '@angular/core';
import { TestEntityComponentMapper } from '../test-entity-component/test-entity-component.mapper';

@Injectable({ providedIn: 'root' })
export class TestEntityMapper implements BaseEntityMapper<TestEntity> {
  private readonly componentMapper = inject(TestEntityComponentMapper);

  fromDto(dto: any): TestEntity {
    return new TestEntity(
      dto.id,
      dto.name,
      dto.description,
      dto.boolean,
      dto.number,
      dto.date,
      getEnumKeyByValue<TestEnum>(TestEnum, dto.enumValue),
      dto.artifact,
      dto.tags,
      dto.components?.map((component: any) => this.componentMapper.fromDto(component)),
    );
  }

  toDto(entity: TestEntity): any {
    return entity;
  }
}
