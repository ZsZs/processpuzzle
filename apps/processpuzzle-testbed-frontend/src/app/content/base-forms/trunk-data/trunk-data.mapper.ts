// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { Injectable } from '@angular/core';
import { TrunkData } from './trunk-data';

type TrunkDataDto = Partial<TrunkData>;

@Injectable({ providedIn: 'root' })
export class TrunkDataMapper implements BaseEntityMapper<TrunkData> {
  fromDto(dto: unknown): TrunkData {
    const source = dto as TrunkDataDto;
    return new TrunkData(source.id, source.key, source.value, source.description);
  }

  toDto(entity: TrunkData): unknown {
    return entity;
  }
}
