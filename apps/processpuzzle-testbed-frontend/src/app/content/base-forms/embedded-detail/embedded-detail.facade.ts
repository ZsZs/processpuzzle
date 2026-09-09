import { Injectable } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { EmbeddedDetail } from './embedded-detail';
import { createEmbeddedDetailDescriptor } from './embedded-detail.descriptors';

/** No service, store or endpoint of its own to declare — see {@link EmbeddedEntityFacade}. */
@Injectable()
export class EmbeddedDetailFacade extends EmbeddedEntityFacade<EmbeddedDetail> {
  readonly entityType = EmbeddedDetail;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createEmbeddedDetailDescriptor();
  }
}
