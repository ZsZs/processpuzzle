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

  /**
   * The details route of an embedded row, which hangs **below** the form of the entity carrying it —
   * `.../test-entity/1/details/embedded-component/embedded_1_1/details`. An embedded child has no base path
   * of its own: the same child type appears under every owner that carries it, and the owner's segments are
   * what identify the row.
   *
   * `rowId` is the row's id, or `new` for a row being created.
   *
   * The segment matches `baseEntityRoutes()`, which builds it with `snakeCaseName`. That agrees with
   * {@link toRoutePath} for the space-separated entity names a descriptor declares; the two would diverge
   * only for a camelCase name, which is not how entities are named.
   */
  embeddedDetailRoute(ownerDetailUrl: string, embeddedDescriptor: BaseEntityDescriptor, rowId: string): string {
    return `${ownerDetailUrl}/${toRoutePath(embeddedDescriptor.entityName)}/${rowId}/details`;
  }

  private basePath(descriptor: BaseEntityDescriptor): string {
    return descriptor.route ?? `${this.routePrefix}/${toRoutePath(descriptor.entityName)}`;
  }
}
