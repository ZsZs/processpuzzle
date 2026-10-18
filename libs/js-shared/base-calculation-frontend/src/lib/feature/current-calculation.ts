import { computed, effect, inject, type Signal } from '@angular/core';
import { PersistedEntity } from '@processpuzzle/base-entity';
import { CalculationDefinition } from '../domain/calculation-definition';
import { CalculationDefinitionStore } from '../domain/calculation-definition.store';

/**
 * What every extra tab of a calculation does first: select the definition its route names in the store, and save
 * a new source through it. Called from a component's constructor, in an injection context.
 *
 * The selection is an effect, not a one-off call, for the reason `WorkflowModelerTabComponent` gives: on a deep
 * link the store's rows have not arrived yet, and `setCurrentEntity` clears the selection when it finds nothing.
 */
export function currentCalculation(entityId: Signal<string>) {
  const store = inject(CalculationDefinitionStore);
  effect(() => {
    if (store.entities().length > 0) store.setCurrentEntity(entityId());
  });
  const definition = computed(() => {
    const current = store.currentEntity();
    return current?.id === entityId() ? current : undefined;
  });
  return {
    store,
    definition,
    /** Saves the definition with `source`; resolves to the stored definition, or undefined when the save failed. */
    save: (source: string): Promise<PersistedEntity<CalculationDefinition> | undefined> => {
      const current = definition();
      if (!current) return Promise.resolve(undefined);
      return store.update(new CalculationDefinition({ ...current, source }) as PersistedEntity<CalculationDefinition>);
    },
  };
}
