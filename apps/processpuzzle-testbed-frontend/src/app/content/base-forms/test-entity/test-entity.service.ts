import { Injectable } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { TestEntity } from './test-entity';
import { TestEntityMapper } from './test-entity.mapper';

@Injectable({ providedIn: 'root' })
export class TestEntityService extends BaseEntityRestService<TestEntity> {
  constructor(protected override entityMapper: TestEntityMapper) {
    super(entityMapper, 'THIRD_PARTY_ROOT', 'test-entity');
  }
}
