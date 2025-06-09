import { Entity } from './entity.decorator';

@Entity({ modelName: 'TestEntity', pluralName: 'TestEntities', uriName: 'test-entity' })
export class TestEntity {
  name = '';
}
