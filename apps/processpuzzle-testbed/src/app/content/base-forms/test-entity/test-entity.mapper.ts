// eslint-disable-next-line @nx/enforce-module-boundaries
import { ArtifactAttr, BaseEntityMapper, getEnumKeyByValue, getEnumValueByKey } from '@processpuzzle/base-entity';
import { TestEntity, TestEnum } from './test-entity';
import { inject, Injectable } from '@angular/core';
import { EmbeddedComponentMapper } from '../embedded-component/embedded-component.mapper';
import { RelatedEntityMapper } from '../related-entity/related-entity.mapper';

interface TestEntityDto {
  id?: string;
  name?: string;
  description?: string;
  boolean?: boolean;
  number?: number;
  date?: Date;
  lookup?: string;
  enumValue?: number;
  artifact?: ArtifactAttr;
  tags?: string[];
  components?: Array<string | { id?: string }>;
  embeddedComponents?: unknown[];
  relatedEntities?: unknown[];
  additionalProperties?: Record<string, string>;
}

@Injectable({ providedIn: 'root' })
export class TestEntityMapper implements BaseEntityMapper<TestEntity> {
  private readonly embeddedComponentMapper = inject(EmbeddedComponentMapper);
  private readonly relatedEntityMapper = inject(RelatedEntityMapper);

  fromDto(dto: unknown): TestEntity {
    const source = dto as TestEntityDto;
    return new TestEntity(
      source.id,
      source.name,
      source.description,
      source.boolean,
      source.number,
      source.date,
      source.lookup,
      source.enumValue === undefined ? undefined : getEnumKeyByValue<TestEnum>(TestEnum, source.enumValue),
      source.artifact,
      source.tags,
      // Components are referenced by id: their payload belongs to the `test-entity-component` endpoint.
      source.components?.map((component) => (typeof component === 'string' ? component : component.id)),
      // Embedded components arrive whole, inside this payload.
      source.embeddedComponents?.map((embeddedComponent) => this.embeddedComponentMapper.fromDto(embeddedComponent)),
      source.relatedEntities?.map((relatedEntity) => this.relatedEntityMapper.fromDto(relatedEntity)),
      source.additionalProperties,
    );
  }

  toDto(entity: TestEntity): unknown {
    const dto = { ...entity };

    return { ...dto, enumValue: getEnumValueByKey<TestEnum>(TestEnum, dto.enumValue) };
  }
}
