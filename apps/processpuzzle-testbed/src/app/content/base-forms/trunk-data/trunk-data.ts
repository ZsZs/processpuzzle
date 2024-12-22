import { BaseEntity } from '@processpuzzle/base-entity';
import { v4 as uuidv4 } from 'uuid';

export class TrunkData implements BaseEntity {
  readonly id: string;
  name: string;
  description: string;
  value: any;

  constructor(id?: string, name?: string, value?: any, description?: string) {
    this.id = id ? id : uuidv4();
    this.name = name != undefined ? name : 'TestEntity';
    this.description = description != undefined ? description : '';
    this.value = value;
  }
}
