// eslint-disable-next-line @nx/enforce-module-boundaries
import { ArtifactAttr, BaseEntity } from '@processpuzzle/base-entity';
import { v4 as uuidv4 } from 'uuid';
import { EmbeddedComponent } from '../embedded-component/embedded-component';
import { RelatedEntity } from '../related-entity/related-entity';

export enum TestEnum {
  VALUE_ONE,
  VALUE_TWO,
  VALUE_THREE,
  VAlUE_FOUR,
  VALUE_FIVE,
}

export class TestEntity implements BaseEntity {
  readonly id: string;
  name: string;
  description: string | undefined;
  boolean;
  number;
  date;
  lookup: string;
  enumValue: TestEnum;
  artifact?: ArtifactAttr;
  tags: Array<string> | undefined;
  /** Ids of the `Test Entity Component`s owned by this entity; the components themselves live in their own table. */
  components: Array<string> | undefined;
  /** Carried inside this entity's payload — an `Embedded Component` has no endpoint of its own. */
  embeddedComponents: Array<EmbeddedComponent> | undefined;
  relatedEntities: Array<RelatedEntity> | undefined;
  additionalProperties: Record<string, string> | undefined;

  constructor(
    id?: string,
    name?: string,
    description?: string,
    boolean?: boolean,
    number?: number,
    date?: Date,
    lookup?: string,
    enumValue?: TestEnum,
    artifact?: ArtifactAttr,
    tags?: Array<string>,
    components?: Array<string>,
    embeddedComponents?: Array<EmbeddedComponent>,
    relatedEntities?: Array<RelatedEntity>,
    additionalProperties?: Record<string, string>,
  ) {
    this.id = id ?? uuidv4();
    this.name = name ?? 'TestEntity';
    this.description = description ?? '';
    this.boolean = boolean ?? true;
    this.number = number ?? 1;
    this.date = date ?? new Date();
    this.lookup = lookup ?? '';
    this.enumValue = enumValue ?? TestEnum.VALUE_ONE;
    this.artifact = artifact;
    this.tags = tags;
    this.components = components;
    this.embeddedComponents = embeddedComponents;
    this.relatedEntities = relatedEntities;
    this.additionalProperties = additionalProperties;
  }
}
