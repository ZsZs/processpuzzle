import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { TestEntityComponent } from './test-entity-component';

@Injectable({ providedIn: 'root' })
export class TestEntityComponentMapper implements BaseEntityMapper<TestEntityComponent> {
  fromDto(dto: any): TestEntityComponent {
    return new TestEntityComponent(dto.id, dto.name, dto.description, dto.testEntityId);
  }

  toDto(entity: TestEntityComponent): any {
    return entity;
  }
}
