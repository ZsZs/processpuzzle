import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';

/**
 * The path segment an entity's routes are mounted at: `"Test Entity Component"` → `test-entity-component`,
 * `"DocumentInputPort"` → `document-input-port`.
 *
 * A transcription of `snakeCaseName` in base-entity's `BaseFormNavigatorStore`, which is what actually builds
 * these URLs — `baseEntityRoutes` mounts each branch under it and the navigator store re-appends it when
 * moving between list and details. The two have to agree exactly: where they don't, the suites navigate to a
 * URL the router answers with NG04002 and the failure reads as a missing button on a blank page.
 *
 * The camelCase clause is the part that is easy to get wrong and was: lower-casing alone maps
 * `DocumentInputPort` to `documentinputport`, which is not where the application mounted it. Entity names in
 * this platform are usually space-separated, for which both spellings agree — so the divergence stays
 * invisible until a library names one camelCase, as base-document names its two port types.
 */
export function toRoutePath(entityName: string): string {
  return entityName
    .replace(/\s+/g, '')
    .replace(/([A-Z]+)(?=[A-Z][a-z])/g, '$1-')
    .replace(/([a-z\d])([A-Z])/g, '$1-$2')
    .toLowerCase();
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
   * The segment matches `baseEntityRoutes()`, which builds it with `snakeCaseName` — see {@link toRoutePath},
   * which transcribes that function for exactly this reason.
   */
  embeddedDetailRoute(ownerDetailUrl: string, embeddedDescriptor: BaseEntityDescriptor, rowId: string): string {
    return `${ownerDetailUrl}/${toRoutePath(embeddedDescriptor.entityName)}/${rowId}/details`;
  }

  private basePath(descriptor: BaseEntityDescriptor): string {
    return descriptor.route ?? `${this.routePrefix}/${toRoutePath(descriptor.entityName)}`;
  }
}
