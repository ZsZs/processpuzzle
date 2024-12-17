import { Injectable } from '@angular/core';
import { BaseEntityService } from './base-entity.service';
import { TestEntity } from './test-entity';
import { TestEntityMapper } from './test-entity.mapper';

@Injectable({ providedIn: 'root' })
export class TestEntityService extends BaseEntityService<TestEntity> {
  constructor(protected mapper: TestEntityMapper) {
    super(mapper, 'TEST_SERVICE_ROOT', 'message/%{messageId}/node');
  }
}
