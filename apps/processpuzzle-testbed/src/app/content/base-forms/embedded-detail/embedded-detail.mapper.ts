// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { Injectable } from '@angular/core';
import { EmbeddedDetail } from './embedded-detail';

/** Mapped by its owner's mapper: an embedded detail arrives inside the `Test Entity` payload. */
@Injectable({ providedIn: 'root' })
export class EmbeddedDetailMapper implements BaseEntityMapper<EmbeddedDetail> {
  fromDto(dto: any): EmbeddedDetail {
    return new EmbeddedDetail(dto.id, dto.name, dto.note);
  }

  toDto(entity: EmbeddedDetail): any {
    return entity;
  }
}
