// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { Injectable } from '@angular/core';
import { RelatedEntity } from './related-entity';

type RelatedEntityDto = Partial<RelatedEntity>;

@Injectable({ providedIn: 'root' })
export class RelatedEntityMapper implements BaseEntityMapper<RelatedEntity> {
  fromDto(dto: unknown): RelatedEntity {
    const source = dto as RelatedEntityDto;
    return new RelatedEntity(source.id, source.name, source.description);
  }

  toDto(entity: RelatedEntity): unknown {
    return entity;
  }
}
