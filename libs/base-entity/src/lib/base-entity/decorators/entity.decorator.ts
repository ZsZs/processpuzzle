import 'reflect-metadata';

/**
 * Defines an entity data transformer capable of transforming data to and from the server.
 */
export interface IEntityTransformer {
  fromServer?: (data: any, criteria?: any) => any;
  toServer?: (entity: any, criteria?: any) => any;
}

export interface EntityOptions {
  modelName: string;
  pluralName: string;
  uriName: string;
}

// Symbol to store entity options on instance
const ENTITY_OPTIONS_KEY = Symbol('entityOptions');

/**
 * Class decorator for entity classes
 * @param options Configuration options for the entity
 * @returns Class decorator function
 */
export function Entity(options: EntityOptions): ClassDecorator {
  return function (target: any) {
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
export function getEntityOptions(instance: any): EntityOptions | undefined {
  if (!instance) return undefined;

  // Try to get options from the instance
  const options = instance[ENTITY_OPTIONS_KEY];
  if (options) return options;

  // Fallback to class metadata (for backward compatibility)
  const constructor = Object.getPrototypeOf(instance)?.constructor;
  if (constructor) {
    return Reflect.getMetadata('entity:options', constructor);
  }

  return undefined;
}
