import { inject, Injectable, Provider } from '@angular/core';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ENTITY_STATE_CONTROL, type BaseEntityDescriptor, type EntityStateControl } from '@processpuzzle/base-entity';
import { BASE_STATE_TRANSLOCO_SCOPE } from '../../base-state.i18n';
import { GovernedEntityRegistry } from '../../domain/definition/governed-entity.registry';
import { EntityStateControlComponent } from './entity-state-control.component';

/**
 * base-state's answer to base-entity's `ENTITY_STATE_CONTROL`: the attribute a machine governs is its
 * `stateAttributeKey`, and it is rendered by {@link EntityStateControlComponent}.
 */
@Injectable({ providedIn: 'root' })
export class BaseStateEntityStateControl implements EntityStateControl {
  readonly component = EntityStateControlComponent;
  private readonly governed = inject(GovernedEntityRegistry);

  async stateAttributeOf(descriptor: BaseEntityDescriptor): Promise<string | undefined> {
    return (await this.governed.machineFor(descriptor.entityName))?.stateAttributeKey || undefined;
  }
}

/**
 * Registers the STATE form control for the whole application, beside `provideEntityStateMachineTab()`. The
 * transloco scope is registered here too, for the reason given there: the control renders on the governed
 * entity's route, which does not register `base_state`.
 */
export function provideEntityStateControl(): Provider[] {
  return [
    { provide: ENTITY_STATE_CONTROL, useExisting: BaseStateEntityStateControl },
    provideTranslocoScope({ scope: BASE_STATE_TRANSLOCO_SCOPE, alias: BASE_STATE_TRANSLOCO_SCOPE }),
  ];
}
