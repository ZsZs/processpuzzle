import { BaseEntity } from '@processpuzzle/base-entity';
import { v4 as uuidv4 } from 'uuid';

export class ApplicationProperty implements BaseEntity {
  readonly id: string;
  private readonly propertyName: string;
  private propertyValue: string;

  constructor(id?: string, name?: string, value?: string) {
    this.id = id ?? uuidv4();
    this.propertyName = name ?? '';
    this.propertyValue = value ?? '';
  }

  // region properties
  public get name() {
    return this.propertyName;
  }

  public get value() {
    return this.propertyValue;
  }

  public set value(newValue: string) {
    this.propertyValue = newValue;
  }

  // endregion
}
