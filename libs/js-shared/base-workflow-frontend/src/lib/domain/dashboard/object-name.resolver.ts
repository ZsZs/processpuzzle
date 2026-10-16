import { inject, Injectable, Injector, signal } from '@angular/core';
import { BASE_ENTITY_FACADE_REGISTRY, type BaseEntity, BaseEntityFacade, DynamicEntityRegistry, EntityDefinitionRegistry } from '@processpuzzle/base-entity';
import { firstValueFrom } from 'rxjs';

/**
 * The name of the record a run is actually about — `ORD-0042` rather than the `1` in `entityId`, or the
 * `new_order` the workflow's author called the object.
 *
 * An `ArtifactInstance` holds the record's id and nothing else (its `name` is the artifact definition's), so
 * the name has to come from base-entity: the entity is found from the artifact definition's `artifactTypeId`,
 * the record is read by id through that entity's own facade, and its name is the attribute the descriptor
 * titles records with — the same one a form's status bar shows. Both kinds of entity are found, a
 * compile-time one in `BASE_ENTITY_FACADE_REGISTRY` and a metadata one through `DynamicEntityRegistry`.
 *
 * **Asynchronous, and remembered.** {@link request} starts a lookup once per record; {@link nameOf} reads
 * the answer from a signal, so a computed that calls it redraws when the name arrives. A record that cannot
 * be named — an entity nobody registered, a 404, a descriptor with no title attribute — is remembered as
 * unnamed and not asked about again; the caller keeps whatever it showed before.
 */
@Injectable({ providedIn: 'root' })
export class ObjectNameResolver {
  private readonly facadeRegistry = inject(BASE_ENTITY_FACADE_REGISTRY);
  private readonly definitions = inject(EntityDefinitionRegistry);
  private readonly dynamicEntities = inject(DynamicEntityRegistry);
  private readonly injector = inject(Injector);

  private readonly names = signal<ReadonlyMap<string, string>>(new Map());
  private readonly requested = new Set<string>();

  /** The record's name, once it has been resolved. */
  nameOf(artifactTypeId: string | undefined, entityId: string | undefined): string | undefined {
    return artifactTypeId && entityId ? this.names().get(keyOf(artifactTypeId, entityId)) : undefined;
  }

  /** Starts resolving the record's name, unless that has been asked already. */
  request(artifactTypeId: string | undefined, entityId: string | undefined): void {
    if (!artifactTypeId || !entityId) return;
    const key = keyOf(artifactTypeId, entityId);
    if (this.requested.has(key)) return;
    this.requested.add(key);
    void this.resolve(artifactTypeId, entityId).then((name) => {
      if (name) this.names.update((names) => new Map(names).set(key, name));
    });
  }

  private async resolve(artifactTypeId: string, entityId: string): Promise<string | undefined> {
    try {
      const facade = await this.facadeOf(artifactTypeId);
      if (!facade) return undefined;
      const record = (await firstValueFrom(facade.service.findById(entityId))) as Record<string, unknown> | undefined;
      const attrName = facade.descriptor.titleAttrName();
      const value = record && attrName ? record[attrName] : undefined;
      return nameFrom(value);
    } catch {
      return undefined;
    }
  }

  /**
   * The facade of the entity an `artifactTypeId` names. Matched loosely against the compile-time entities —
   * the seed's `order` is a descriptor's `Order` — and as a code or a name, just as loosely, against the metadata ones.
   */
  private async facadeOf(artifactTypeId: string): Promise<BaseEntityFacade<BaseEntity> | undefined> {
    const wanted = normalized(artifactTypeId);
    const entityName = Object.keys(this.facadeRegistry).find((name) => normalized(name) === wanted);
    if (entityName) return this.injector.get(this.facadeRegistry[entityName], null, { optional: true }) ?? undefined;

    const definition = [...(await this.definitions.load()).values()].find((candidate) => normalized(candidate.code) === wanted || normalized(candidate.name) === wanted);
    return definition ? (await this.dynamicEntities.resolve(definition.name))?.facade : undefined;
  }
}

/**
 * A title attribute's value as a name — a string, number or boolean. Anything else, an embedded object or a
 * list, would stringify as `[object Object]`, which is worse than the label the caller already shows.
 */
function nameFrom(value: unknown): string | undefined {
  if (typeof value === 'string') return value === '' ? undefined : value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return undefined;
}

function keyOf(artifactTypeId: string, entityId: string): string {
  return `${artifactTypeId}/${entityId}`;
}

function normalized(name: string): string {
  return name.toLowerCase().replace(/[\s_-]+/g, '');
}
