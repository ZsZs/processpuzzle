import { BaseEntity } from '@processpuzzle/base-entity';
import { RouteDefinition } from './app-definition';

/**
 * Frontend model of the `ModuleDefinition` schema of `base-app-api.yaml`: a lazily-loadable slice of an
 * application, with its own flat route list and its own transloco scope. An {@link ModuleMount} of an
 * `AppDefinition` names one of these by key and mounts its routes under a base path.
 *
 * **What "lazy" honestly means.** A module lazy-loads *metadata* — this definition, its routes, its
 * translations, and the documents and descriptors they name. It does **not** lazy-load widget code:
 * widget components are bundled at compile time and resolved through the frontend registry, so a
 * metadata module cannot split a bundle on its own. The wins are that `AppDefinition` stops being one
 * aggregate that has to be loaded, locked and published atomically, and that a module is the unit of
 * authoring permission and versioning. Nothing here should be justified on bundle size.
 *
 * Unlike the region / route / mount classes this one is a **routable** entity: it has endpoints of its
 * own, so it comes with a service, a store and a facade rather than travelling inside another payload.
 */
export class ModuleDefinition implements BaseEntity {
  /**
   * The contract calls this field `key`; base-entity keys every store, URL and reference on `id`, so
   * {@link ModuleDefinitionMapper} renames it on the way in and out rather than teaching the framework a
   * second identity field. One name for one thing on this side of the mapper — a `key` property beside
   * `id` could only ever disagree with it.
   *
   * Immutable by contract: `ModuleMount.moduleKey` resolves against it, and a rename would silently
   * unmount the module from every app that mounts it.
   */
  id: string;
  name: string;
  translocoId: string | undefined;
  description: string | undefined;
  /**
   * Transloco scope the module's translations load under. Absent means {@link id}, which is the default
   * the contract states; {@link moduleTranslocoScope} is the one place that default is applied.
   */
  translocoScope: string | undefined;
  /**
   * The module's routes, flat and relative to the base path it is mounted at — the same shape and the
   * same derived nesting as `AppDefinition.routes`, and flattened by the same mapper. A module does not
   * mount modules: this is where composition stops.
   */
  routes: RouteDefinition[] | undefined;
  // region server-assigned
  orgKey: string | undefined;
  version: number | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;
  // endregion

  constructor(init: Partial<ModuleDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name ?? '';
    this.translocoId = init.translocoId;
    this.description = init.description;
    this.translocoScope = init.translocoScope;
    this.routes = init.routes;
    this.orgKey = init.orgKey;
    this.version = init.version;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}

/**
 * The scope a module's translations load under, applying the contract's default. Kept as a function
 * rather than resolved in the mapper, so a definition never carries a `translocoScope` the designer did
 * not author — the field stays empty in the form, and the fallback is a rendering decision.
 */
export function moduleTranslocoScope(definition: ModuleDefinition): string {
  return definition.translocoScope?.trim() || definition.id;
}
