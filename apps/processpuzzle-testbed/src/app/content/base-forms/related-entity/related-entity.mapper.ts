// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { Injectable } from '@angular/core';
import { RelatedEntity } from './related-entity';

@Injectable({ providedIn: 'root' })
export class RelatedEntityMapper implements BaseEntityMapper<RelatedEntity> {
  fromDto(dto: any): RelatedEntity {
    return new RelatedEntity(dto.id, dto.name, dto.description);
  }

  toDto(entity: RelatedEntity): any {
    return entity;
  }
}
