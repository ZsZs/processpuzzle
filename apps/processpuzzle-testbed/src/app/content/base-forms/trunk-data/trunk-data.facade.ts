import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityFacade } from '@processpuzzle/base-entity';
import { TrunkData } from './trunk-data';
import { TrunkDataMapper } from './trunk-data.mapper';
import { TrunkDataService } from './trunk-data.service';
import { TrunkDataStore } from './trunk-data.store';
import { createTrunkDataDescriptor } from './trunk-data.descriptors';

@Injectable()
export class TrunkDataFacade extends BaseEntityFacade<TrunkData> {
  readonly entityType = TrunkData;
  readonly entityName = 'Trunk Data';
  readonly attrDescriptors = createTrunkDataDescriptor().attrDescriptors;

  private readonly mapperRef = inject(TrunkDataMapper);
  private readonly serviceRef = inject(TrunkDataService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return TrunkDataStore;
  }
}
