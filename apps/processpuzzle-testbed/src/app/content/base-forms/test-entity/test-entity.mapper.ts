// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper, getEnumKeyByValue, getEnumValueByKey } from '@processpuzzle/base-entity';
import { TestEntity, TestEnum } from './test-entity';
import { inject, Injectable } from '@angular/core';
import { EmbeddedComponentMapper } from '../embedded-component/embedded-component.mapper';
import { RelatedEntityMapper } from '../related-entity/related-entity.mapper';

@Injectable({ providedIn: 'root' })
export class TestEntityMapper implements BaseEntityMapper<TestEntity> {
  private readonly embeddedComponentMapper = inject(EmbeddedComponentMapper);
  private readonly relatedEntityMapper = inject(RelatedEntityMapper);

  fromDto(dto: any): TestEntity {
    return new TestEntity(
      dto.id,
      dto.name,
      dto.description,
      dto.boolean,
      dto.number,
      dto.date,
      dto.lookup,
      getEnumKeyByValue<TestEnum>(TestEnum, dto.enumValue),
      dto.artifact,
      dto.tags,
      // Components are referenced by id: their payload belongs to the `test-entity-component` endpoint.
      dto.components?.map((component: any) => (typeof component === 'string' ? component : component?.id)),
      // Embedded components arrive whole, inside this payload.
      dto.embeddedComponents?.map((embeddedComponent: any) => this.embeddedComponentMapper.fromDto(embeddedComponent)),
      dto.relatedEntities?.map((relatedEntity: any) => this.relatedEntityMapper.fromDto(relatedEntity)),
      dto.additionalProperties,
    );
  }

  toDto(entity: TestEntity): any {
    const dto = { ...entity } as any;

    return { ...dto, enumValue: getEnumValueByKey<TestEnum>(TestEnum, dto.enumValue) };
  }
}
