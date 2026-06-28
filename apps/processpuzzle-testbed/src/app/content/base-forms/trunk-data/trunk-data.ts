import { v4 as uuidv4 } from 'uuid';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { LookupTable } from '@processpuzzle/base-entity';

export class TrunkData implements LookupTable {
  readonly id: string;
  key: string;
  value: string | number;
  description: string;

  constructor(id?: string, key?: string, value?: string | number, description?: string) {
    this.id = id ? id : uuidv4();
    this.key = key != undefined ? key : 'Key';
    this.value = value != undefined ? value : '';
    this.description = description != undefined ? description : '';
  }
}
