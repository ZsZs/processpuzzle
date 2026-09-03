import { inject, Injectable, Type } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { RelatedEntity } from './related-entity';
import { RelatedEntityMapper } from './related-entity.mapper';
import { RelatedEntityService } from './related-entity.service';
import { RelatedEntityStore } from './related-entity.store';
import { createRelatedEntityDescriptor } from './related-entity.descriptors';

@Injectable()
export class RelatedEntityFacade extends BaseEntityFacade<RelatedEntity> {
  readonly entityType = RelatedEntity;

  private readonly mapperRef = inject(RelatedEntityMapper);
  private readonly serviceRef = inject(RelatedEntityService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return RelatedEntityStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createRelatedEntityDescriptor();
  }
}
