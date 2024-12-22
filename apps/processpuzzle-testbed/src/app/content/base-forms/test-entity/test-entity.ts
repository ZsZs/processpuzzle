import { BaseEntity } from '@processpuzzle/base-entity';
import { v4 as uuidv4 } from 'uuid';

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

  constructor(id?: string, name?: string, description?: string, boolean?: boolean, number?: number, date?: Date, enumValue?: TestEnum) {
    this.id = id ? id : uuidv4();
    this.name = name != undefined ? name : 'TestEntity';
    this.description = description != undefined ? description : '';
    this.boolean = boolean != undefined ? boolean : true;
    this.number = number != undefined ? number : 1;
    this.date = date != undefined ? date : new Date();
    this.enumValue = enumValue != undefined ? enumValue : TestEnum.VALUE_ONE;
  }
}
