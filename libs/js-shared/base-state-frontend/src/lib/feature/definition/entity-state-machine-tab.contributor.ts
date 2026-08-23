import { inject, Injectable, Provider } from '@angular/core';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ENTITY_TAB_CONTRIBUTORS, type BaseEntityDescriptor, type EntityTabContributor, type EntityTabDescriptor } from '@processpuzzle/base-entity';
import { BASE_STATE_TRANSLOCO_SCOPE } from '../../base-state.i18n';
import { GovernedEntityRegistry } from '../../domain/definition/governed-entity.registry';
import { ENTITY_STATE_MACHINE_TAB } from './entity-state-machine-tab';

/**
 * Offers the State Machine tab to every entity a state machine governs, and to no other.
 *
 * This is base-state reaching *into* another feature's screens, which is why it is a contributor rather
 * than an `extraTabs` entry: `Order` is a `BaseEntityDefinition` row seeded by base-entity, and neither
 * base-entity nor the application mounting its screens can be expected to know that base-state has a view
 * to add. The binding is the entity name on both sides, exactly as base-rule binds a rule to a context —
 * nothing on either side names the other.
 *
 * Asked once per entity, when {@link EntityScreenResolver} resolves its screens; see
 * {@link GovernedEntityRegistry} for what that one question costs and when the answer is refreshed.
 */
@Injectable({ providedIn: 'root' })
export class EntityStateMachineTabContributor implements EntityTabContributor {
  private readonly governed = inject(GovernedEntityRegistry);

  async tabsFor(descriptor: BaseEntityDescriptor): Promise<EntityTabDescriptor[]> {
    return (await this.governed.governs(descriptor.entityName)) ? [ENTITY_STATE_MACHINE_TAB] : [];
  }
}

/**
 * Registers the State Machine tab for the whole application. Spread into the root `providers`, beside
 * `BASE_STATE_FACADE_PROVIDERS`.
 *
 * Two providers, and both are needed. The contributor is what offers the tab; the transloco scope is what
 * lets its **label** resolve. The label is rendered by `BaseEntityTabsComponent`, which lives on the
 * governed entity's route — under `base-rule` for the samples, under an `AppDefinition`'s shell at run time
 * — and none of those branches register `base_state`. Registering it at the root is the only place that
 * covers all of them, and it is cheap: transloco loads a scope lazily, on the first key that needs it.
 *
 * The alias is spelled out, as everywhere in this workspace: transloco camel-cases the default alias, so
 * `base_state` would silently become `baseState` and miss every key below it.
 */
export function provideEntityStateMachineTab(): Provider[] {
  return [
    { provide: ENTITY_TAB_CONTRIBUTORS, useExisting: EntityStateMachineTabContributor, multi: true },
    provideTranslocoScope({ scope: BASE_STATE_TRANSLOCO_SCOPE, alias: BASE_STATE_TRANSLOCO_SCOPE }),
  ];
}
