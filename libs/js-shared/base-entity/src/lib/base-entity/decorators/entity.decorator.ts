import 'reflect-metadata';

/**
 * Defines an entity data transformer capable of transforming data to and from the server.
 */
export interface IEntityTransformer {
  fromServer?: (data: unknown, criteria?: unknown) => unknown;
  toServer?: (entity: unknown, criteria?: unknown) => unknown;
}

export interface EntityOptions {
  modelName: string;
  pluralName: string;
  uriName: string;
  transform?: IEntityTransformer;
}

// Symbol to store entity options on instance
const ENTITY_OPTIONS_KEY = Symbol('entityOptions');

// Metadata key for storing ID properties
const ENTITY_ID_PROPERTIES_KEY = 'entity:idProperties';

/**
 * Class decorator for entity classes
 * @param options Configuration options for the entity
 * @returns Class decorator function
 */
export function Entity(options: EntityOptions): ClassDecorator {
  return function (target) {
    // Store metadata on the class (for backward compatibility)
    Reflect.defineMetadata('entity:options', options, target);

    // Add a property to the prototype to make options accessible from instances
    Object.defineProperty(target.prototype, ENTITY_OPTIONS_KEY, {
      value: options,
      writable: false,
      enumerable: false,
      configurable: false
    });

    return target;
  };
}

/**
 * Gets entity options from an entity instance
 * @param instance An instance of a class decorated with @Entity
 * @returns The entity options or undefined if not found
 */
export function getEntityOptions(instance: object | null | undefined): EntityOptions | undefined {
  if (!instance) return undefined;

  // Try to get options from the instance
  const options = (instance as Record<symbol, EntityOptions | undefined>)[ENTITY_OPTIONS_KEY];
  if (options) return options;

  // Fallback to class metadata (for backward compatibility)
  const constructor = Object.getPrototypeOf(instance)?.constructor;
  if (constructor) {
    return Reflect.getMetadata('entity:options', constructor);
  }

  return undefined;
}

/**
 * Property decorator for marking entity properties as ID fields
 * @returns PropertyDecorator function
 */
export function Id(): PropertyDecorator {
  return function(target: object, propertyKey: string | symbol) {
    // Get the constructor of the class
    const constructor = target.constructor;
    
    // Get existing ID properties or initialize empty array
    const existingIdProps: string[] = Reflect.getMetadata(ENTITY_ID_PROPERTIES_KEY, constructor) || [];
    
    // Add the current property to the list if it's not already there
    if (!existingIdProps.includes(propertyKey.toString())) {
      existingIdProps.push(propertyKey.toString());
    }
    
    // Update the metadata with the new list
    Reflect.defineMetadata(ENTITY_ID_PROPERTIES_KEY, existingIdProps, constructor);
  };
}

/**
 * Gets all property names marked with @Id decorator from an entity instance
 * @param instance An instance of a class with properties decorated with @Id
 * @returns Array of property names marked as IDs or empty array if none found
 */
export function getEntityIds(instance: object | null | undefined): string[] {
  if (!instance) return [];

  // Get the constructor from the instance
  const constructor = Object.getPrototypeOf(instance)?.constructor;
  if (!constructor) return [];
  
  // Return the ID properties from metadata or empty array if none found
  return Reflect.getMetadata(ENTITY_ID_PROPERTIES_KEY, constructor) || [];
}
