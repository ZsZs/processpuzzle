import { v4 as uuidv4 } from 'uuid';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntity } from '@processpuzzle/base-entity';
import { EmbeddedDetail } from '../embedded-detail/embedded-detail';

/**
 * Component of `Test Entity` whose payload lives inside the parent's document — there is no
 * `embedded-component` endpoint, so it is created, saved and deleted as part of that document.
 * `TestEntityComponent` is the other containment variation: a component with a table of its own.
 *
 * It carries embedded components of its own, which is what makes the containment two levels deep.
 */
export class EmbeddedComponent implements BaseEntity {
  readonly id: string;
  name: string;
  description: string;
  embeddedDetails: EmbeddedDetail[];

  constructor(id?: string, name?: string, description?: string, embeddedDetails?: EmbeddedDetail[]) {
    this.id = id ?? uuidv4();
    this.name = name ?? 'EmbeddedComponent';
    this.description = description ?? '';
    this.embeddedDetails = embeddedDetails ?? [];
  }
}
