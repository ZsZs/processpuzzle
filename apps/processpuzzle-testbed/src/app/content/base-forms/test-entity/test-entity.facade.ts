import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { TestEntity } from './test-entity';
import { TestEntityMapper } from './test-entity.mapper';
import { TestEntityService } from './test-entity.service';
import { TestEntityStore } from './test-entity.store';
import { createTestEntityDescriptor } from './test-entity.descriptors';

@Injectable()
export class TestEntityFacade extends BaseEntityFacade<TestEntity> {
  readonly entityType = TestEntity;

  private readonly mapperRef = inject(TestEntityMapper);
  private readonly serviceRef = inject(TestEntityService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return TestEntityStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createTestEntityDescriptor();
  }
}
