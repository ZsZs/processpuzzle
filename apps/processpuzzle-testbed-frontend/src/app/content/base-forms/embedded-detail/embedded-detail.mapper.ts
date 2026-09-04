// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { Injectable } from '@angular/core';
import { EmbeddedDetail } from './embedded-detail';

type EmbeddedDetailDto = Partial<EmbeddedDetail>;

/** Mapped by its owner's mapper: an embedded detail arrives inside the `Test Entity` payload. */
@Injectable({ providedIn: 'root' })
export class EmbeddedDetailMapper implements BaseEntityMapper<EmbeddedDetail> {
  fromDto(dto: unknown): EmbeddedDetail {
    const source = dto as EmbeddedDetailDto;
    return new EmbeddedDetail(source.id, source.name, source.note);
  }

  toDto(entity: EmbeddedDetail): unknown {
    return entity;
  }
}
