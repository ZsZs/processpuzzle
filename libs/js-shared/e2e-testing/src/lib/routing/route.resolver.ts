import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';

/** "Test Entity Component" → "test-entity-component" */
export function toRoutePath(entityName: string): string {
  return entityName.toLowerCase().replace(/\s+/g, '-');
}

export class RouteResolver {
  constructor(private readonly routePrefix: string) {}

  listRoute(descriptor: BaseEntityDescriptor): string {
    return `${this.basePath(descriptor)}/list`;
  }

  detailRoute(descriptor: BaseEntityDescriptor, entityId: string): string {
    return `${this.basePath(descriptor)}/${entityId}/details`;
  }

  private basePath(descriptor: BaseEntityDescriptor): string {
    return descriptor.route ?? `${this.routePrefix}/${toRoutePath(descriptor.entityName)}`;
  }
}
