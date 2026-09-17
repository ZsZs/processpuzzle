// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { Injectable } from '@angular/core';
import { TrunkDataMapper } from './trunk-data.mapper';
import { TrunkData } from './trunk-data';

@Injectable({ providedIn: 'root' })
export class TrunkDataService extends BaseEntityRestService<TrunkData> {
  constructor(protected override entityMapper: TrunkDataMapper) {
    super(entityMapper, 'THIRD_PARTY_ROOT', 'trunk-data');
  }
}
