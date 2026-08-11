import { Injectable } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { TestEntityComponent } from './test-entity-component';

type TestEntityComponentDto = Partial<TestEntityComponent>;

@Injectable({ providedIn: 'root' })
export class TestEntityComponentMapper implements BaseEntityMapper<TestEntityComponent> {
  fromDto(dto: unknown): TestEntityComponent {
    const source = dto as TestEntityComponentDto;
    return new TestEntityComponent(source.id, source.name, source.description, source.testEntityId);
  }

  toDto(entity: TestEntityComponent): unknown {
    return entity;
  }
}
