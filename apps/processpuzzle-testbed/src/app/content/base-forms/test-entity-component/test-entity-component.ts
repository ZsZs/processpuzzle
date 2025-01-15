import { v4 as uuidv4 } from 'uuid';
import { BaseEntity } from '@processpuzzle/base-entity';

export class TestEntityComponent implements BaseEntity {
  readonly id: string;
  private name: string;
  private description: string | undefined;
  private testEntityId: string;

  constructor(id?: string, name?: string, description?: string, testEntityId?: string) {
    this.id = id ? id : uuidv4();
    this.name = name != undefined ? name : 'TestEntityComponent';
    this.description = description != undefined ? description : '';
    this.testEntityId = testEntityId != undefined ? testEntityId : uuidv4();
  }
}
