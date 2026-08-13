import { v4 as uuidv4 } from 'uuid';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Association target of `Test Entity.relatedEntities`. It is an aggregate root of its own: it is created and
 * deleted from its own list, and detaching it from a Test Entity leaves it untouched. Contrast with
 * `TestEntityComponent` (a component with its own table) and `EmbeddedComponent` (a component in the parent's
 * payload), which both die with their parent.
 */
export class RelatedEntity implements BaseEntity {
  readonly id: string;
  name: string;
  description: string;

  constructor(id?: string, name?: string, description?: string) {
    this.id = id ?? uuidv4();
    this.name = name ?? 'RelatedEntity';
    this.description = description ?? '';
  }
}
