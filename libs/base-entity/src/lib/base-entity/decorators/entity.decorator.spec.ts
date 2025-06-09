import 'reflect-metadata';
import { EntityOptions, getEntityOptions } from './entity.decorator';
import { TestEntity } from './test-entity';

describe('Entity Decorator', () => {

  it('should store modelName, pluralName, and uriName as metadata on the class', () => {
    // Get the metadata from the decorated class
    const metadata = Reflect.getMetadata('entity:options', TestEntity) as EntityOptions;

    // Verify that the metadata exists
    expect(metadata).toBeDefined();

    // Verify that the modelName is correctly stored
    expect(metadata.modelName).toBe('TestEntity');

    // Verify that the pluralName is correctly stored
    expect(metadata.pluralName).toBe('TestEntities');

    // Verify that the uriName is correctly stored
    expect(metadata.uriName).toBe('test-entity');
  });

  it('should allow accessing entity options from an instance', () => {
    // Create an instance of the decorated class
    const instance = new TestEntity();
    instance.name = 'Test Instance';

    // Get the metadata from the instance using the utility function
    const metadata = getEntityOptions(instance);

    // Verify that the metadata exists
    expect(metadata).toBeDefined();

    // Verify that the modelName is correctly accessible
    expect(metadata?.modelName).toBe('TestEntity');

    // Verify that the pluralName is correctly accessible
    expect(metadata?.pluralName).toBe('TestEntities');

    // Verify that the uriName is correctly accessible
    expect(metadata?.uriName).toBe('test-entity');
  });
});
