import { Entity, Id, IEntityTransformer } from './entity.decorator';

const testTransformer: IEntityTransformer = {
  fromServer: (data: any) => {
    return { ...data, name: data.name ? data.name.toUpperCase() : '' };
  },
  toServer: (entity: any) => {
    return { ...entity, name: entity.name ? entity.name.toLowerCase() : '' };
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
