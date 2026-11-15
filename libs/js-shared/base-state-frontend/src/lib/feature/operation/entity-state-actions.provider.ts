import { inject, Injectable } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import type { EntityStateActions, EntityStateFireResult, EntityStateSnapshot } from '@processpuzzle/base-entity';
import { httpErrorMessage } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { ENTITY_STATE_CONTROL_I18N_SCOPE } from '../../base-state.i18n';
import { GovernedEntityRegistry } from '../../domain/definition/governed-entity.registry';
import { StateMachineDefinition } from '../../domain/definition/state-machine-definition';
import { EntityObjectStateService } from '../../domain/operation/entity-object-state.service';

/**
 * What a working context asks of the state machines: where the active entity is and where it can go, and the firing
 * of one transition. The same endpoints and the same wording as the state control of an entity form, put behind the
 * token base-entity reads — see `ENTITY_STATE_ACTIONS`.
 */
@Injectable({ providedIn: 'root' })
export class BaseStateEntityStateActions implements EntityStateActions {
  private readonly governed = inject(GovernedEntityRegistry);
  private readonly objectStates = inject(EntityObjectStateService);
  private readonly transloco = inject(TranslocoService);

  async stateOf(entityName: string, id: string): Promise<EntityStateSnapshot | undefined> {
    const machine = await this.governed.machineFor(entityName);
    if (!machine) return undefined;
    const state = await firstValueFrom(this.objectStates.findState(machine.entityName, id));
    if (!state) return undefined;

    return {
      stateAttribute: machine.stateAttributeKey || undefined,
      currentKey: state.currentStateKey,
      currentLabel: this.stateLabel(machine, state.currentStateKey),
      transitions: state.availableTransitions.map((transition) => ({
        key: transition.triggerKey,
        label: machine.transitions.find((candidate) => candidate.key === transition.transitionKey)?.name || transition.triggerKey,
        enabled: transition.guardsSatisfied,
        blockedReason: transition.blockedReason,
      })),
    };
  }

  async fire(entityName: string, id: string, triggerKey: string, version: number): Promise<EntityStateFireResult> {
    const machine = await this.governed.machineFor(entityName);
    if (!machine) return this.refused('');
    try {
      const result = await firstValueFrom(this.objectStates.fireTransition(machine.entityName, id, triggerKey, version));
      if (!result.success) return this.refused(result.rejectionReason ?? '');
      return { success: true, message: this.transloco.translate(`${ENTITY_STATE_CONTROL_I18N_SCOPE}.fired`, { state: this.stateLabel(machine, result.newStateKey ?? '') }) };
    } catch (error) {
      // A stale version is a 409, and the caller reloads the entity either way.
      return this.refused(httpErrorMessage(error));
    }
  }

  private refused(reason: string): EntityStateFireResult {
    return { success: false, message: this.transloco.translate(`${ENTITY_STATE_CONTROL_I18N_SCOPE}.rejected`, { reason }) };
  }

  private stateLabel(machine: StateMachineDefinition, stateKey: string): string {
    return machine.states.find((state) => state.key === stateKey)?.name || stateKey;
  }
}
