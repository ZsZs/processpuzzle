import { Injectable } from '@angular/core';
import { BaseEntityRestService } from './base-entity-rest.service';
import { TestEntity } from '../test-entity';
import { TestEntityMapper } from '../test-entity.mapper';

@Injectable({ providedIn: 'root' })
export class TestEntityService extends BaseEntityRestService<TestEntity> {
  constructor(protected mapper: TestEntityMapper) {
    super(mapper, 'THIRD_PARTY_ROOT', 'message/%{messageId}/node');
  }
}
