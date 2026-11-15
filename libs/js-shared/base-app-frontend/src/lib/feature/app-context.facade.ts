import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { WorkingContextRow } from '../domain/app-definition';
import { createWorkingContextDescriptor } from '../domain/working-context.descriptors';

/** A context is a row of the `App Definition` document, edited through it like a route or a mount. */
@Injectable()
export class AppContextFacade extends EmbeddedEntityFacade<WorkingContextRow> {
  readonly entityType = WorkingContextRow;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWorkingContextDescriptor();
  }
}
