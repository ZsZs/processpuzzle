import { BaseEntity } from './base-entity/base-entity';
import { v4 as uuidv4 } from 'uuid';

export enum TestEnum {
  VALUE_ONE = 0,
  VALUE_TWO = 1,
  VALUE_THREE = 2,
  VALUE_FOUR = 3,
  VALUE_FIVE = 4,
}

export class TestEntity implements BaseEntity {
  id: string = uuidv4();
  name? = '';
  description?: string = '';
  boolean?: boolean;
  number?: number;
  date?: Date;
  selectable?: number;
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
