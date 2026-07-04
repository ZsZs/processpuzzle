import { Entity, Id, IEntityTransformer } from './entity.decorator';

const testTransformer: IEntityTransformer = {
  fromServer: (data: unknown) => {
    const source = (data ?? {}) as { name?: string };
    return { ...source, name: source.name ? source.name.toUpperCase() : '' };
  },
  toServer: (entity: unknown) => {
    const source = (entity ?? {}) as { name?: string };
    return { ...source, name: source.name ? source.name.toLowerCase() : '' };
  }
};

@Entity({ 
  modelName: 'TestEntity', 
  pluralName: 'TestEntities', 
  uriName: 'test-entity',
  transform: testTransformer
})
export class TestEntity {
  @Id()
  id = '';
  
  name = '';
  
  @Id()
  alternateId = '';
}
