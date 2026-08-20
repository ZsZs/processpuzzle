import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { EntityDefinition, isRenderable } from './entity-definition';
import { EntityDefinitionService } from './entity-definition.service';

/**
 * The tenant's entity definitions, fetched once and indexed both ways.
 *
 * **The one place that knows a definition's `code` and its entity `name` are different things.** A
 * definition is keyed by `code` (`order`) — that is what the instance endpoints take as a path segment and
 * what an `EMBEDDED_COMPONENTS` attribute names its child by. A `BaseEntityDescriptor` is keyed by
 * `entityName` (`Order`) — that is what an `AppDefinition` route names, what `BASE_ENTITY_FACADE_REGISTRY`
 * is keyed by, and what `BaseFormNavigatorSingletonStore` builds URLs from. Everything downstream of this
 * class works in names; everything upstream in codes.
 *
 * The fetch is memoized as a **promise**, not as its result: several `ENTITY` routes of one application
 * resolve concurrently while its routes are being built, and awaiting the same in-flight request is what
 * keeps that one request rather than one per route.
 */
@Injectable({ providedIn: 'root' })
export class EntityDefinitionRegistry {
  private readonly service = inject(EntityDefinitionService);
  private definitions?: Promise<Map<string, EntityDefinition>>;

  /** Every renderable definition, by `code`. */
  async load(): Promise<ReadonlyMap<string, EntityDefinition>> {
    this.definitions ??= this.fetch();
    return this.definitions;
  }

  /** The definition named `entityName`, i.e. whose `name` is that. */
  async byName(entityName: string | undefined): Promise<EntityDefinition | undefined> {
    if (!entityName) return undefined;
    const definitions = await this.load();
    for (const definition of definitions.values()) {
      if (definition.name === entityName) return definition;
    }
    return undefined;
  }

  /** The definition with this `code` — how an embedded child and a foreign key name their target. */
  async byCode(code: string | undefined): Promise<EntityDefinition | undefined> {
    if (!code) return undefined;
    return (await this.load()).get(code);
  }

  /**
   * Discards the cache, so the next read fetches again.
   *
   * For the designer: a definition edited through its own CRUD screens changes what the previewed
   * application should render, and nothing else would ever invalidate a session-long cache.
   */
  reset(): void {
    this.definitions = undefined;
  }

  private async fetch(): Promise<Map<string, EntityDefinition>> {
    const definitions = await firstValueFrom(this.service.findAll());
    // A failed fetch resolves to `[]` (see EntityDefinitionService), so an empty map is a legitimate
    // answer and is cached as such — retrying on every route recognition would turn one missing backend
    // into a request per navigation.
    return new Map(definitions.filter(isRenderable).map((definition) => [definition.code, definition]));
  }
}
