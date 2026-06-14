import { v4 as uuidv4 } from 'uuid';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntity } from '@processpuzzle/base-entity';

export class TestEntityComponent implements BaseEntity {
  readonly id: string;
  name: string;
  description: string | undefined;
  testEntityId: string;

  constructor(id?: string, name?: string, description?: string, testEntityId?: string) {
    this.id = id ? id : uuidv4();
    this.name = name != undefined ? name : 'TestEntityComponent';
    this.description = description != undefined ? description : '';
    this.testEntityId = testEntityId ?? '';
  }
}
