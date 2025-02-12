import { BaseEntityService } from '@processpuzzle/base-entity';
import { Injectable } from '@angular/core';
import { TrunkDataMapper } from './trunk-data.mapper';
import { TrunkData } from './trunk-data';

@Injectable({ providedIn: 'root' })
export class TrunkDataService extends BaseEntityService<TrunkData> {
  constructor(protected override entityMapper: TrunkDataMapper) {
    super(entityMapper, 'TEST_SERVICE_ROOT', 'trunk-data');
  }
}
