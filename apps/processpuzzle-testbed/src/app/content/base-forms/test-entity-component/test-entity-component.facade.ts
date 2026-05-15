import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityFacade } from '@processpuzzle/base-entity';
import { TestEntityComponent } from './test-entity-component';
import { TestEntityComponentMapper } from './test-entity-component.mapper';
import { TestEntityComponentService } from './test-entity-component.service';
import { TestEntityComponentStore } from './test-entity-component.store';
import { createTestEntityComponentDescriptor } from './test-entity-component.descriptors';

@Injectable()
export class TestEntityComponentFacade extends BaseEntityFacade<TestEntityComponent> {
  readonly entityType = TestEntityComponent;
  readonly entityName = 'Test Entity Component';
  readonly attrDescriptors = createTestEntityComponentDescriptor().attrDescriptors;

  private readonly mapperRef = inject(TestEntityComponentMapper);
  private readonly serviceRef = inject(TestEntityComponentService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return TestEntityComponentStore;
  }
}
