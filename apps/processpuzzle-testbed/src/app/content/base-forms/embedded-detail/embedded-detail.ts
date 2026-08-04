import { v4 as uuidv4 } from 'uuid';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Second level of containment: an `Embedded Detail` lives inside an `Embedded Component`, which itself lives
 * inside a `Test Entity`. Its point is to exercise the nested case — the row is addressed by its position in
 * a document two levels up, and saving it rewrites that whole document.
 */
export class EmbeddedDetail implements BaseEntity {
  readonly id: string;
  name: string;
  note: string;

  constructor(id?: string, name?: string, note?: string) {
    this.id = id ? id : uuidv4();
    this.name = name != undefined ? name : 'EmbeddedDetail';
    this.note = note != undefined ? note : '';
  }
}
