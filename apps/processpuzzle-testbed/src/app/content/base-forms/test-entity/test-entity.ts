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

export interface TestEntityOptions {
  id?: string;
  name?: string;
  description?: string;
  boolean?: boolean;
  number?: number;
  date?: Date;
  lookup?: string;
  enumValue?: TestEnum;
  artifact?: ArtifactAttr;
  tags?: string[];
  components?: string[];
  embeddedComponents?: EmbeddedComponent[];
  relatedEntities?: RelatedEntity[];
  additionalProperties?: Record<string, string>;
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

  constructor(options: TestEntityOptions = {}) {
    this.id = options.id ?? uuidv4();
    this.name = options.name ?? 'TestEntity';
    this.description = options.description ?? '';
    this.boolean = options.boolean ?? true;
    this.number = options.number ?? 1;
    this.date = options.date ?? new Date();
    this.lookup = options.lookup ?? '';
    this.enumValue = options.enumValue ?? TestEnum.VALUE_ONE;
    this.artifact = options.artifact;
    this.tags = options.tags;
    this.components = options.components;
    this.embeddedComponents = options.embeddedComponents;
    this.relatedEntities = options.relatedEntities;
    this.additionalProperties = options.additionalProperties;
  }
}
