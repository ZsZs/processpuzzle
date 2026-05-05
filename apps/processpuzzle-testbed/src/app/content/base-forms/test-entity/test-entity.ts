import { ArtifactAttr, BaseEntity } from '@processpuzzle/base-entity';
import { v4 as uuidv4 } from 'uuid';
import { TestEntityComponent } from '../test-entity-component/test-entity-component';

export enum TestEnum {
  VALUE_ONE,
  VALUE_TWO,
  VALUE_THREE,
  VAlUE_FOUR,
  VALUE_FIVE,
}

export class TestEntity implements BaseEntity {
  readonly id: string;
  private name: string;
  private description: string | undefined;
  private boolean;
  private number;
  private date;
  private enumValue: TestEnum;
  private artifact?: ArtifactAttr | undefined;
  private tags: Array<string> | undefined;
  private components: Array<TestEntityComponent> | undefined;

  constructor(
    id?: string,
    name?: string,
    description?: string,
    boolean?: boolean,
    number?: number,
    date?: Date,
    enumValue?: TestEnum,
    artifact?: ArtifactAttr,
    tags?: Array<string>,
    components?: Array<TestEntityComponent>,
  ) {
    this.id = id ? id : uuidv4();
    this.name = name != undefined ? name : 'TestEntity';
    this.description = description != undefined ? description : '';
    this.boolean = boolean != undefined ? boolean : true;
    this.number = number != undefined ? number : 1;
    this.date = date != undefined ? date : new Date();
    this.enumValue = enumValue != undefined ? enumValue : TestEnum.VALUE_ONE;
    this.artifact = artifact;
    this.tags = tags;
    this.components = components;
  }
}
