import { Injectable } from '@angular/core';
import { BaseEntityService } from '@processpuzzle/base-entity';
import { TestEntity } from './test-entity';
import { TestEntityMapper } from './test-entity.mapper';

@Injectable({ providedIn: 'root' })
export class TestEntityService extends BaseEntityService<TestEntity> {
  constructor(protected override entityMapper: TestEntityMapper) {
    super(entityMapper, 'TEST_SERVICE_ROOT', 'test-entity');
  }
}
