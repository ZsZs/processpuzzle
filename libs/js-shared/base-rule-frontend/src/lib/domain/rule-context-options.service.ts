import { inject, Injectable, signal, Signal } from '@angular/core';
import { BASE_ENTITY_FACADE_REGISTRY, EntityDefinitionRegistry, type Selectable } from '@processpuzzle/base-entity';

/**
 * The entity types a rule can name as its `context` — **both** kinds of entity there are.
 *
 * A rule's context is an entity *name*: `BaseEntityFormComponent` loads rules for
 * `entityDescriptor().entityName`, so the value stored on a rule has to be exactly that. Two registries
 * answer for names, and the Context dropdown has to offer both or a whole class of entity becomes
 * un-ruleable through the UI:
 *
 * - `BASE_ENTITY_FACADE_REGISTRY` — entities compiled into the host application, keyed by name already.
 * - `EntityDefinitionRegistry` — entities that exist only as `BaseEntityDefinition` rows. Their descriptors
 *   are synthesized at run-time and never appear in the facade registry, which is why listing that token
 *   alone left `Order` unselectable while three seeded rules were already written against it.
 *
 * The definitions have to be **fetched**, and `getSelectables()` is synchronous — it is called from the
 * dropdown's template on every change detection. So the fetch is kicked off once, here, and its result
 * lands in a signal: the dropdown renders the compiled names immediately and gains the authored ones on the
 * change detection that follows the response, with no `await` anywhere in the descriptor.
 */
@Injectable({ providedIn: 'root' })
export class RuleContextOptions {
  private readonly facadeRegistry = inject(BASE_ENTITY_FACADE_REGISTRY);
  private readonly definitions = inject(EntityDefinitionRegistry);
  private readonly definedEntityNames = signal<readonly string[]>([]);

  constructor() {
    void this.loadDefinitionNames();
  }

  /**
   * Every context name, alphabetically, without duplicates.
   *
   * Deduplicated because a definition may share a name with a compiled facade — `EntityScreenResolver`
   * resolves such a name to the compiled one, so offering it twice would be offering the same context
   * twice. Sorted because the two sources are unrelated and their concatenation order is not meaningful to
   * anyone reading the dropdown.
   */
  options(): Array<Selectable> {
    const names = new Set([...Object.keys(this.facadeRegistry), ...this.definedEntityNames()]);
    return [...names].sort((left, right) => left.localeCompare(right)).map((name) => ({ key: name, value: name }));
  }

  /** For a caller that wants to react to the load rather than poll it. */
  asSignal(): Signal<readonly string[]> {
    return this.definedEntityNames.asReadonly();
  }

  /**
   * A failed fetch leaves the compiled names in place rather than clearing them. `EntityDefinitionService`
   * already resolves a failure to `[]`, so there is nothing to catch here beyond the case of no backend at
   * all — and in that case a dropdown holding the compiled entities is strictly better than an empty one.
   */
  private async loadDefinitionNames(): Promise<void> {
    const definitions = await this.definitions.load();
    this.definedEntityNames.set([...definitions.values()].map((definition) => definition.name));
  }
}
