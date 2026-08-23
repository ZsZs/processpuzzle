import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { createActionRefDescriptor, createGuardRefDescriptor } from '../../domain/definition/bean-ref.descriptors';
import { BeanRef, State, Transition } from '../../domain/definition/state-machine-definition';
import { createStateDescriptor } from '../../domain/definition/state.descriptors';
import { createTransitionDescriptor } from '../../domain/definition/transition.descriptors';

/**
 * The four embedded levels of a state machine, in one module because they are one decision: each has a
 * facade like any other entity — that is what gives it a store, and so a working list and form — and only
 * its repository differs, reading and writing the `State Machine Definition` document rather than an
 * endpoint of its own.
 *
 * A guard and an action share `BeanRef` as their entity type and differ only in their descriptor. That is
 * deliberate and it is also the reason they cannot share a *facade*: the store a facade builds is keyed by
 * the descriptor's entity name, and `guards` and `actions` are two lists whose rows have to stay apart.
 */

@Injectable()
export class StateMachineStateFacade extends EmbeddedEntityFacade<State> {
  readonly entityType = State;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createStateDescriptor();
  }
}

@Injectable()
export class StateMachineTransitionFacade extends EmbeddedEntityFacade<Transition> {
  readonly entityType = Transition;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createTransitionDescriptor();
  }
}

@Injectable()
export class StateTransitionGuardFacade extends EmbeddedEntityFacade<BeanRef> {
  readonly entityType = BeanRef;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createGuardRefDescriptor();
  }
}

@Injectable()
export class StateTransitionActionFacade extends EmbeddedEntityFacade<BeanRef> {
  readonly entityType = BeanRef;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createActionRefDescriptor();
  }
}
