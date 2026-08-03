// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { Injectable } from '@angular/core';
import { RelatedEntityMapper } from './related-entity.mapper';
import { RelatedEntity } from './related-entity';

@Injectable({ providedIn: 'root' })
export class RelatedEntityService extends BaseEntityRestService<RelatedEntity> {
  constructor(protected override entityMapper: RelatedEntityMapper) {
    super(entityMapper, 'BACKEND_SERVICE_ROOT', 'related-entity');
  }
}
