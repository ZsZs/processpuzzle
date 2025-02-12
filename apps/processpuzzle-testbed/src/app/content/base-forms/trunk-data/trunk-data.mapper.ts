import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { Injectable } from '@angular/core';
import { TrunkData } from './trunk-data';

@Injectable({ providedIn: 'root' })
export class TrunkDataMapper implements BaseEntityMapper<TrunkData> {
  fromDto(dto: any): TrunkData {
    return new TrunkData(dto.id, dto.name, dto.value, dto.description);
  }

  toDto(entity: TrunkData): any {
    return entity;
  }
}
