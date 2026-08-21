import { Injectable, signal } from '@angular/core';
import { State, Transition } from '../../../domain/state-machine-definition';

/**
 * What the user has selected on the canvas, as the domain objects rather than as diagram nodes: the
 * properties panels edit a `State` and a `Transition`, and nothing about a selection's geometry concerns
 * them.
 *
 * Signals rather than plain fields, because the selection is set from ng-diagram's `selectionChanged`
 * output — outside the panels' own change detection — and a plain field would leave a zoneless component
 * showing a stale subject.
 *
 * A selection is one or the other, never both: the two panels are alternatives, so selecting an edge has
 * to clear the node or both would render at once.
 */
@Injectable({ providedIn: 'root' })
export class DiagramSelectionService {
  private readonly selectedStateSignal = signal<State | undefined>(undefined);
  private readonly selectedTransitionSignal = signal<Transition | undefined>(undefined);

  readonly selectedState = this.selectedStateSignal.asReadonly();
  readonly selectedTransition = this.selectedTransitionSignal.asReadonly();

  selectState(state: State): void {
    this.selectedStateSignal.set(state);
    this.selectedTransitionSignal.set(undefined);
  }

  selectTransition(transition: Transition): void {
    this.selectedTransitionSignal.set(transition);
    this.selectedStateSignal.set(undefined);
  }

  clear(): void {
    this.selectedStateSignal.set(undefined);
    this.selectedTransitionSignal.set(undefined);
  }
}
