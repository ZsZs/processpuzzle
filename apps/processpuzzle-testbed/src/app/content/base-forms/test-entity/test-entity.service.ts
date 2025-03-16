import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { TestEntity } from './test-entity';
import { TestEntityMapper } from './test-entity.mapper';

@Injectable({ providedIn: 'root' })
export class TestEntityService extends BaseEntityRestService<TestEntity> {
  constructor(protected override entityMapper: TestEntityMapper) {
    super(entityMapper, 'TEST_SERVICE_ROOT', 'test-entity');
  }
}
