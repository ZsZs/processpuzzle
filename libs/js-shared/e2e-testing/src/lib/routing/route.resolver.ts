import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';

/** "Test Entity Component" → "test-entity-component" */
export function toRoutePath(entityName: string): string {
  return entityName.toLowerCase().replace(/\s+/g, '-');
}

/**
 * The id in a `.../<entity>/<id>/details` URL — how a screen the test navigated to names the row it is showing.
 *
 * The inverse of {@link RouteResolver.detailRoute}, and the only way to learn an id the application assigned:
 * a row created through a form is identified by nothing the test chose. Callers reach it having already waited
 * for a details URL, so the segment is there; a URL short of one yields `''`.
 */
export function entityIdFromDetailUrl(url: string): string {
  return new URL(url).pathname.split('/').at(-2) ?? '';
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
