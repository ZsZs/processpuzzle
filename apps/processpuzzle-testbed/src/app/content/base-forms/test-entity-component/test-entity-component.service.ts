import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { TestEntityComponent } from './test-entity-component';
import { TestEntityComponentMapper } from './test-entity-component.mapper';

@Injectable({ providedIn: 'root' })
export class TestEntityComponentService extends BaseEntityRestService<TestEntityComponent> {
  constructor(protected override entityMapper: TestEntityComponentMapper) {
    super(entityMapper, 'TEST_SERVICE_ROOT', 'test-entity-component');
  }
}
