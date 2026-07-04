import { Injectable } from '@angular/core';
import { TestEntity } from '../test-entity';
import { TestEntityMapper } from '../test-entity.mapper';
import { BaseEntityFirestoreService } from './base-entity-firestore.service';

@Injectable({ providedIn: 'root' })
export class TestEntityFirestoreService extends BaseEntityFirestoreService<TestEntity> {
  constructor(protected mapper: TestEntityMapper) {
    super(mapper, 'test-entity');
  }
}
