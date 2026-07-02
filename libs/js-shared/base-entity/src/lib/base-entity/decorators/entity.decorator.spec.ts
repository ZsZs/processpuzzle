import 'reflect-metadata';
import { EntityOptions, getEntityIds, getEntityOptions, IEntityTransformer } from './entity.decorator';
import { TestEntity } from './test-entity';
import { describe, expect, it } from 'vitest';

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

  it('should store and allow accessing the transform property', () => {
    // Get the metadata from the decorated class
    const metadata = Reflect.getMetadata('entity:options', TestEntity) as EntityOptions;

    // Verify that the transform property exists
    expect(metadata.transform).toBeDefined();

    // Create an instance of the decorated class
    const instance = new TestEntity();
    instance.name = 'Test Instance';

    // Get the metadata from the instance using the utility function
    const instanceMetadata = getEntityOptions(instance);

    // Verify that the transform property is accessible from the instance
    expect(instanceMetadata?.transform).toBeDefined();

    // Verify that the transformer functions work as expected
    const transformer = instanceMetadata?.transform as IEntityTransformer;

    // Test fromServer transformation
    const serverData = { name: 'server data' };
    const transformedFromServer = transformer.fromServer?.(serverData) as { name?: string } | undefined;
    expect(transformedFromServer?.name).toBe('SERVER DATA');

    // Test toServer transformation
    const entityData = { name: 'ENTITY DATA' };
    const transformedToServer = transformer.toServer?.(entityData) as { name?: string } | undefined;
    expect(transformedToServer?.name).toBe('entity data');
  });
  describe('Id Decorator', () => {
    it('should mark properties as ID fields', () => {
      // Create an instance of the decorated class
      const instance = new TestEntity();

      // Get the ID properties using the utility function
      const idProperties = getEntityIds(instance);

      // Verify that the ID properties exist
      expect(idProperties).toBeDefined();
      expect(Array.isArray(idProperties)).toBe(true);

      // Verify that both properties are correctly marked as IDs
      expect(idProperties).toContain('id');
      expect(idProperties).toContain('alternateId');

      // Verify that the total count is correct (2 properties)
      expect(idProperties).toHaveLength(2);
    });

    it('should handle multiple ID properties correctly', () => {
      // Create an instance of the decorated class
      const instance = new TestEntity();
      instance.id = 'primary-id';
      instance.alternateId = 'secondary-id';

      // Get the ID properties using the utility function
      const idProperties = getEntityIds(instance);

      // Verify that both properties are included in the result
      expect(idProperties).toEqual(['id', 'alternateId']);
    });

    it('should return an empty array for null or undefined instances', () => {
      // Test with null
      expect(getEntityIds(null)).toEqual([]);

      // Test with undefined
      expect(getEntityIds(undefined)).toEqual([]);
    });
  });
});
